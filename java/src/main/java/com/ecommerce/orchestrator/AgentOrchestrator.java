package com.ecommerce.orchestrator;

import com.ecommerce.agent.ReActAgent;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.ExecutionGroup;
import com.ecommerce.model.ExecutionPlan;
import com.ecommerce.model.response.IntentItem;
import com.ecommerce.util.IntentAgentResolver;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 编排器
 * 采用 Plan & Execute 模式调度多个 ReActAgent：
 *
 * Plan 阶段：根据意图依赖关系（dependsOn）构建执行计划 DAG，拓扑排序生成串行/并行执行组
 * Execute 阶段：按拓扑序调度——同组并行（agentParallelExecutor），组间串行，上游输出传递下游
 *
 * 后续可扩展支持 PlanExecutorAgent 等新型 Agent。
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    @Resource
    private IntentAgentResolver intentAgentResolver;

    @Resource(name = "agentParallelExecutor")
    private ThreadPoolExecutor agentParallelExecutor;

    /**
     * Plan & Execute 入口
     *
     * @param intents 多意图列表（含依赖关系）
     * @param params  公共参数（userId, sessionId, message, history, summary 等）
     * @return 各子Agent 的 AgentResult 列表
     */
    public List<AgentResult> execute(List<IntentItem> intents, Map<String, Object> params) {
        // 步骤1: 参数校验
        if (CollectionUtils.isEmpty(intents)) {
            log.warn("AgentOrchestrator.execute - 意图列表为空");
            return Collections.emptyList();
        }

        log.info("AgentOrchestrator.execute - 开始 Plan & Execute, intents: {}", intents);

        // 步骤2: Plan —— 构建执行计划 DAG
        ExecutionPlan plan = buildPlan(intents);
        log.info("AgentOrchestrator.execute - Plan 完成, groups: {}", plan.getGroups());

        // 步骤3: Execute —— 按拓扑序调度
        Map<String, Map<String, Object>> accumulatedResults = new LinkedHashMap<>();
        List<AgentResult> allResults = new ArrayList<>();

        for (int groupIdx = 0; groupIdx < plan.getGroups().size(); groupIdx++) {
            ExecutionGroup group = plan.getGroups().get(groupIdx);
            log.info("AgentOrchestrator.execute - 执行组{}/{}, group: {}, 上游数据: {}",
                    groupIdx + 1, plan.getGroups().size(), group, accumulatedResults.keySet());

            // 步骤3a: 将上游累积结果注入当前组的各 Agent 参数
            group.setUpstreamData(new LinkedHashMap<>(accumulatedResults));

            // 步骤3b: 执行当前组（并行或串行）
            List<AgentResult> groupResults = executeGroup(group, params);

            // 步骤3c: 收集结果并累积到上游数据中
            for (int i = 0; i < group.getIntentItems().size(); i++) {
                IntentItem item = group.getIntentItems().get(i);
                AgentResult result = groupResults.get(i);
                if (result != null) {
                    allResults.add(result);
                    if (result.getData() != null) {
                        accumulatedResults.put(item.getIntent(), result.getData());
                    }
                }
            }
        }

        log.info("AgentOrchestrator.execute - Execute 完成, totalResults: {}", allResults.size());
        return allResults;
    }

    /**
     * Plan 阶段：根据 dependsOn 构建执行计划 DAG
     *
     * 算法：
     *   ① 构建 intent → IntentItem 索引
     *   ② 计算每个意图的入度（被依赖数）
     *   ③ 拓扑排序：入度为 0 的节点进入当前并行组
     *   ④ 移除当前组节点后，入度更新的节点进入下一组
     *   ⑤ 重复直到所有节点被处理
     */
    private ExecutionPlan buildPlan(List<IntentItem> intents) {
        // 步骤1: 构建索引
        Map<String, IntentItem> intentMap = new LinkedHashMap<>();
        for (IntentItem item : intents) {
            intentMap.put(item.getIntent(), item);
        }

        // 步骤2: 计算入度（被多少个其他意图依赖）
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> dependentsMap = new LinkedHashMap<>(); // intent → 依赖它的意图列表

        for (IntentItem item : intents) {
            inDegree.putIfAbsent(item.getIntent(), 0);
            dependentsMap.putIfAbsent(item.getIntent(), new ArrayList<>());
        }

        for (IntentItem item : intents) {
            if (!CollectionUtils.isEmpty(item.getDependsOn())) {
                for (String dep : item.getDependsOn()) {
                    if (intentMap.containsKey(dep)) {
                        // item 依赖 dep → dep 完成后才能执行 item
                        inDegree.merge(item.getIntent(), 1, Integer::sum);
                        dependentsMap.computeIfAbsent(dep, k -> new ArrayList<>()).add(item.getIntent());
                    } else {
                        log.info("AgentOrchestrator.buildPlan - 依赖的意图不在列表中，忽略, intent: {}, dependsOn: {}",
                                item.getIntent(), dep);
                    }
                }
            }
        }

        // 步骤3: 拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<ExecutionGroup> groups = new ArrayList<>();
        while (!queue.isEmpty()) {
            // 步骤3a: 取出当前层所有入度为 0 的节点 → 一个并行组
            List<IntentItem> currentGroupItems = new ArrayList<>();
            int currentSize = queue.size();
            for (int i = 0; i < currentSize; i++) {
                String intent = queue.poll();
                IntentItem item = intentMap.get(intent);
                if (item != null) {
                    currentGroupItems.add(item);
                }

                // 步骤3b: 更新依赖该节点的其他节点的入度
                List<String> dependents = dependentsMap.getOrDefault(intent, Collections.emptyList());
                for (String dependent : dependents) {
                    int newDegree = inDegree.merge(dependent, -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.add(dependent);
                    }
                }
            }

            if (!currentGroupItems.isEmpty()) {
                boolean parallel = currentGroupItems.size() > 1;
                groups.add(ExecutionGroup.builder()
                        .intentItems(currentGroupItems)
                        .parallel(parallel)
                        .build());
            }
        }

        // 步骤4: 处理未入队的节点（可能存在循环依赖，强制串行执行）
        if (groups.stream().mapToInt(g -> g.getIntentItems().size()).sum() < intents.size()) {
            log.warn("AgentOrchestrator.buildPlan - 检测到循环依赖或孤立意图，剩余节点强制串行");
            for (IntentItem item : intents) {
                boolean alreadyInPlan = groups.stream()
                        .flatMap(g -> g.getIntentItems().stream())
                        .anyMatch(gi -> gi.getIntent().equals(item.getIntent()));
                if (!alreadyInPlan) {
                    groups.add(ExecutionGroup.builder()
                            .intentItems(List.of(item))
                            .parallel(false)
                            .build());
                }
            }
        }

        return ExecutionPlan.builder().groups(groups).build();
    }

    /**
     * Execute 阶段：执行一个执行组
     *
     * 并行组：使用 agentParallelExecutor 同时提交所有 Agent
     * 串行组（单元素）：直接在当前线程执行
     */
    private List<AgentResult> executeGroup(ExecutionGroup group, Map<String, Object> params) {
        List<IntentItem> items = group.getIntentItems();

        if (group.isParallel() && items.size() > 1) {
            // 步骤1: 并行执行
            return executeParallel(items, group.getUpstreamData(), params);
        } else {
            // 步骤2: 串行执行（单元素或显式串行组）
            List<AgentResult> results = new ArrayList<>();
            for (IntentItem item : items) {
                results.add(executeSingle(item, group.getUpstreamData(), params));
            }
            return results;
        }
    }

    /**
     * 并行执行多个 Agent
     */
    private List<AgentResult> executeParallel(List<IntentItem> items,
                                               Map<String, Map<String, Object>> upstreamData,
                                               Map<String, Object> params) {
        // 步骤1: 为每个意图创建 CompletableFuture
        List<CompletableFuture<AgentResult>> futures = new ArrayList<>();
        for (IntentItem item : items) {
            CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(
                    () -> executeSingle(item, upstreamData, params),
                    agentParallelExecutor
            );
            futures.add(future);
        }

        // 步骤2: 等待全部完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.join();
        } catch (Exception e) {
            log.error("AgentOrchestrator.executeParallel - 并行执行异常", e);
        }

        // 步骤3: 收集结果（异常返回 null）
        List<AgentResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                AgentResult result = futures.get(i).getNow(null);
                results.add(result);
                if (result == null) {
                    log.warn("AgentOrchestrator.executeParallel - 第{}个Agent结果为空, intent: {}",
                            i + 1, items.get(i).getIntent());
                }
            } catch (Exception e) {
                log.error("AgentOrchestrator.executeParallel - 获取第{}个Agent结果异常, intent: {}",
                        i + 1, items.get(i).getIntent(), e);
                results.add(null);
            }
        }

        return results;
    }

    /**
     * 执行单个 Agent
     */
    private AgentResult executeSingle(IntentItem item,
                                       Map<String, Map<String, Object>> upstreamData,
                                       Map<String, Object> params) {
        // 步骤1: 查找对应的 ReActAgent
        ReActAgent agent = intentAgentResolver.resolve(item.getIntent());
        if (agent == null) {
            log.error("AgentOrchestrator.executeSingle - 未找到对应Agent, intent: {}", item.getIntent());
            return AgentResult.builder()
                    .agentName("unknown")
                    .success(false)
                    .error("未找到处理意图 [" + item.getIntent() + "] 的Agent")
                    .confidence(0.0)
                    .build();
        }

        // 步骤2: 构建子Agent 参数（合并公共参数 + 上游结果 + 当前意图实体）
        Map<String, Object> subParams = new HashMap<>(params);
        subParams.put("entities", item.getEntities());

        // 步骤2a: 注入依赖的上游结果
        if (!CollectionUtils.isEmpty(item.getDependsOn())
                && !CollectionUtils.isEmpty(upstreamData)) {
            for (String dep : item.getDependsOn()) {
                Map<String, Object> depData = upstreamData.get(dep);
                if (depData != null) {
                    subParams.put("upstream_" + dep, depData);
                    // 关键字段直接合并到顶层（如 products）
                    if (depData.containsKey("products")) {
                        subParams.putIfAbsent("products", depData.get("products"));
                    }
                    if (depData.containsKey("reply")) {
                        subParams.putIfAbsent("upstream_reply_" + dep, depData.get("reply"));
                    }
                }
            }
        }

        log.info("AgentOrchestrator.executeSingle - 开始执行, intent: {}, agentName: {}, dependsOn: {}",
                item.getIntent(), agent.getName(), item.getDependsOn());

        // 步骤3: 调用子Agent 执行
        try {
            AgentResult result = agent.runAsync(subParams).join();
            log.info("AgentOrchestrator.executeSingle - 执行完成, intent: {}, agentName: {}, success: {}",
                    item.getIntent(), agent.getName(), result.isSuccess());
            return result;
        } catch (Exception e) {
            log.error("AgentOrchestrator.executeSingle - 执行异常, intent: {}, agentName: {}",
                    item.getIntent(), agent.getName(), e);
            return AgentResult.builder()
                    .agentName(agent.getName())
                    .success(false)
                    .error(e.getMessage())
                    .confidence(0.0)
                    .build();
        }
    }
}
