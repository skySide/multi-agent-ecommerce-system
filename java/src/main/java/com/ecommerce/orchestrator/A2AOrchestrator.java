package com.ecommerce.orchestrator;

import com.ecommerce.agent.AgentRegistry;
import com.ecommerce.agent.BaseAgent;
import com.ecommerce.model.*;
import com.ecommerce.model.response.IntentItem;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.MemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * A2A 任务编排器（替代 AgentOrchestrator）
 *
 * 基于 Google A2A Protocol 理念：Agent 通过 AgentCard 声明能力（capability），
 * LLM 生成 A2ATask 列表（指定 capabilityId + input），AgentRegistry 动态匹配 Agent。
 *
 * 核心原则：
 * - 意图依赖数据（requiredData），不依赖其他意图
 * - 编排器只做调度和数据传递，不直接查询数据库、不做硬编码过滤
 * - 数据获取和筛选由 Agent 通过 Tool 完成
 * - Agent 匹配通过 capabilityId 声明而非硬编码 intent 映射
 */
@Component
public class A2AOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(A2AOrchestrator.class);

    @Lazy
    @Resource
    private AgentRegistry agentRegistry;

    @Resource(name = "agentParallelExecutor")
    private ThreadPoolExecutor agentParallelExecutor;

    @Resource
    private ChatClient chatClient;

    @Resource
    private ConversationSessionService conversationSessionService;

    @Resource
    private MemoryService memoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConversationContext conversationContext;

    // ==================== Plan & Execute 入口 ====================

    public List<AgentResult> execute(List<IntentItem> intents, Map<String, Object> params) {
        if (CollectionUtils.isEmpty(intents)) {
            log.warn("A2AOrchestrator.execute - 意图列表为空");
            return Collections.emptyList();
        }
        log.info("A2AOrchestrator.execute - 开始 Plan & Execute, intentCount: {}", intents.size());

        this.conversationContext = buildConversationContext(params);

        List<A2ATask> tasks = generatePlan(intents, conversationContext, params);
        if (CollectionUtils.isEmpty(tasks)) {
            log.warn("A2AOrchestrator.execute - LLM 计划生成失败，降级为串行执行");
            return fallbackSerialExecute(intents, params);
        }
        return executeTasks(tasks, params);
    }

    // ==================== 对话上下文构建 ====================

    @SuppressWarnings("unchecked")
    private ConversationContext buildConversationContext(Map<String, Object> params) {
        String sessionId = (String) params.get("sessionId");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        Map<String, Object> availableData = new HashMap<>();
        List<Map<String, Object>> previousRounds = new ArrayList<>();

        try {
            var session = conversationSessionService.getBySessionId(sessionId);
            if (session != null) {
                if (session.getExtractedInfo() != null && !session.getExtractedInfo().isEmpty()
                        && !session.getExtractedInfo().equals("{}")) {
                    availableData = objectMapper.readValue(session.getExtractedInfo(), Map.class);
                }
                if (session.getRoundIntents() != null && !session.getRoundIntents().isEmpty()
                        && !session.getRoundIntents().equals("[]")) {
                    previousRounds = objectMapper.readValue(session.getRoundIntents(),
                            new TypeReference<List<Map<String, Object>>>() {});
                }
            }
        } catch (Exception e) {
            log.warn("A2AOrchestrator.buildConversationContext - 解析 session 数据失败", e);
        }

        List<String> recentDialogue = history != null
                ? history.subList(Math.max(0, history.size() - 6), history.size())
                : Collections.emptyList();

        return ConversationContext.builder()
                .availableData(availableData)
                .previousRounds(previousRounds)
                .dialogueSummary(summary)
                .recentDialogue(recentDialogue)
                .build();
    }

    // ==================== LLM 计划生成 ====================

    private List<A2ATask> generatePlan(List<IntentItem> intents, ConversationContext context,
                                        Map<String, Object> params) {
        String message = (String) params.get("message");
        String planPrompt = buildPlanPrompt(intents, context, message);

        log.info("A2AOrchestrator.generatePlan - 开始调用 LLM 生成 A2A 任务计划");

        try {
            BeanOutputConverter<ExecutionPlan> converter = new BeanOutputConverter<>(ExecutionPlan.class);
            String response = chatClient.prompt().user(planPrompt).call().content();
            log.info("A2AOrchestrator.generatePlan - LLM原始响应: {}", response);

            ExecutionPlan plan = converter.convert(response);
            if (plan == null || CollectionUtils.isEmpty(plan.getTasks())) {
                log.warn("A2AOrchestrator.generatePlan - LLM 返回空任务列表");
                return null;
            }

            List<A2ATask> tasks = plan.getTasks();
            for (A2ATask task : tasks) {
                if (task.getStatus() == null) {
                    task.setStatus("pending");
                }
            }

            log.info("A2AOrchestrator.generatePlan - 生成 A2A 任务计划:\n{}",
                    toPrettyString(plan.getContextAnalysis(), tasks));
            return tasks;
        } catch (Exception e) {
            log.error("A2AOrchestrator.generatePlan - LLM 计划生成失败", e);
            return null;
        }
    }

    private String buildPlanPrompt(List<IntentItem> intents, ConversationContext context, String message) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是执行计划生成器。根据用户意图和可用能力（capability），生成 A2A Task 列表。\n\n");
        sb.append("核心原则：\n");
        sb.append("1. 意图需要的是数据，不是其他意图。你的工作是决定每个意图所需的数据如何获取。\n");
        sb.append("2. 优先从历史数据中筛选匹配的数据。只有当历史数据不满足需求时，才生成新的数据获取任务。\n");
        sb.append("3. 每个任务指定 capabilityId（能力标识）和 input（输入参数），不要指定具体 Agent。\n");
        sb.append("4. 严格按能力描述选择合适的 capabilityId，不要使用不匹配的能力。\n");
        sb.append("5. 如果确实无法获取到数据，如实告知用户，绝不编造。\n");
        sb.append("6. 【关键】端到端能力（描述中标注了\"端到端\"、\"内部自动\"等）自身已处理所有数据需求，" +
                "不要为它们生成额外的数据获取任务（如 search_by_keyword、get_user_profile）。直接生成该能力对应的一个 task 即可。\n");
        sb.append("7. 【引用解析】如果用户用位置/序号引用历史商品（如\"第1个\"、\"最后一个\"、\"刚才推荐的\"），" +
                "必须从【可用数据】中的 recommended_product_ids 列表解析出具体 ID，使用 get_by_ids 并传入 product_ids。\n" +
                "例：recommended_product_ids=[P01,P02,P03] + 用户说\"第1个和最后一个\" → input={\"product_ids\":[\"P01\",\"P03\"]}\n" +
                "例：recommended_product_ids=[P01...P06] + 用户说\"比较刚才推荐的\" → input={\"product_ids\":[\"P01\",...,\"P06\"]}\n\n");

        // 可用能力列表（含描述）
        List<AgentCard> cards = agentRegistry.getAllCards();
        sb.append("可用能力（每个能力后附描述，请严格按描述选择）：\n");
        if (!cards.isEmpty()) {
            for (AgentCard card : cards) {
                if (card.getCapabilities() != null) {
                    for (AgentCapability cap : card.getCapabilities()) {
                        sb.append(String.format("- %s: %s\n", cap.getId(), cap.getDescription()));
                    }
                }
            }
        } else {
            sb.append("（无可用能力——请降级为直接回复）\n");
        }
        sb.append("\n");

        // 可用数据
        sb.append("【可用数据（来自历史对话 extracted_info）】\n");
        if (context.getAvailableData() != null && !context.getAvailableData().isEmpty()) {
            context.getAvailableData().forEach((k, v) ->
                    sb.append(String.format("- %s: %s\n", k, v)));
        } else {
            sb.append("（无历史累积数据）\n");
        }

        sb.append("\n【历史轮次（round_intents）】\n");
        if (context.getPreviousRounds() != null && !context.getPreviousRounds().isEmpty()) {
            for (int i = 0; i < context.getPreviousRounds().size(); i++) {
                Map<String, Object> round = context.getPreviousRounds().get(i);
                sb.append(String.format("- Round %s: %s → entities: %s\n",
                        round.getOrDefault("round", "?"),
                        round.getOrDefault("intent", "?"),
                        round.getOrDefault("entities", "{}")));
            }
        } else {
            sb.append("（无历史轮次）\n");
        }

        sb.append("\n【对话摘要】\n").append(context.getDialogueSummary() != null
                ? context.getDialogueSummary() : "（无摘要）").append("\n");

        sb.append("\n【最近对话】\n");
        if (context.getRecentDialogue() != null && !context.getRecentDialogue().isEmpty()) {
            sb.append(String.join("\n", context.getRecentDialogue())).append("\n");
        } else {
            sb.append("（无最近对话）\n");
        }

        // 意图需求
        sb.append("\n【当前用户消息】\n").append(message).append("\n");
        sb.append("\n【需要执行的意图及数据需求】\n");
        for (int i = 0; i < intents.size(); i++) {
            IntentItem item = intents.get(i);
            sb.append(String.format("%d. %s", i + 1, item.getIntent()));
            if (!CollectionUtils.isEmpty(item.getRequiredData())) {
                sb.append(":\n");
                for (DataRequirement req : item.getRequiredData()) {
                    sb.append(String.format("   - 需要 %s → filterDescription: \"%s\"\n",
                            req.getType(), req.getFilterDescription()));
                }
            } else {
                sb.append("（无特殊数据需求）\n");
            }
        }

        // 输出格式
        sb.append("\n请生成 A2A 任务列表（JSON 格式）：\n");
        sb.append("{\n");
        sb.append("  \"contextAnalysis\": \"分析当前可用数据与用户需求的匹配情况\",\n");
        sb.append("  \"tasks\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"task_1\",\n");
        sb.append("      \"capabilityId\": \"search_by_keyword\",\n");
        sb.append("      \"input\": {\"query\": \"从历史商品中筛选小米和华为品牌\"},\n");
        sb.append("      \"dependsOn\": [],\n");
        sb.append("      \"reasoning\": \"历史有商品但需按品牌筛选\"\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"task_2\",\n");
        sb.append("      \"capabilityId\": \"compare_products\",\n");
        sb.append("      \"input\": {},\n");
        sb.append("      \"dependsOn\": [\"task_1\"],\n");
        sb.append("      \"reasoning\": \"依赖task_1获取的商品列表进行对比分析\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("注意：\n");
        sb.append("- capabilityId 必须从上述「可用能力」列表中选择\n");
        sb.append("- input 只填写从用户消息中提取的参数；如果参数需要从上游任务获取，input 留空 {}\n");
        sb.append("- dependsOn 引用的 task id 必须存在于前面的任务中\n");
        sb.append("- 每个任务必须有 reasoning 说明");

        return sb.toString();
    }

    private String toPrettyString(String contextAnalysis, List<A2ATask> tasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== A2A 任务计划 ==========\n");
        sb.append("上下文分析: ").append(contextAnalysis != null ? contextAnalysis : "无").append("\n\n");

        if (CollectionUtils.isEmpty(tasks)) {
            sb.append("（无任务）\n");
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                A2ATask task = tasks.get(i);
                sb.append(String.format("Task %s [capabilityId: %s]", task.getId(), task.getCapabilityId()));
                if (task.getAssignedAgent() != null) {
                    sb.append(String.format(" → agent: %s", task.getAssignedAgent()));
                }
                sb.append("\n");
                if (task.getInput() != null && !task.getInput().isEmpty()) {
                    sb.append("  输入: ").append(task.getInput()).append("\n");
                }
                sb.append("  依赖: ").append(CollectionUtils.isEmpty(task.getDependsOn())
                        ? "无" : String.join(", ", task.getDependsOn())).append("\n");
                sb.append("  推理: ").append(task.getReasoning()).append("\n");
                if (i < tasks.size() - 1) sb.append("\n");
            }
        }
        sb.append("==================================");
        return sb.toString();
    }

    // ==================== 任务执行 ====================

    private List<AgentResult> executeTasks(List<A2ATask> tasks, Map<String, Object> params) {
        Map<String, A2ATask> taskMap = tasks.stream()
                .collect(Collectors.toMap(A2ATask::getId, t -> t, (a, b) -> a, LinkedHashMap::new));
        Map<String, Map<String, Object>> taskOutputs = new LinkedHashMap<>();
        Map<String, AgentResult> taskResults = new LinkedHashMap<>();
        Set<String> completedIds = new HashSet<>();
        Set<String> pending = new LinkedHashSet<>(taskMap.keySet());

        while (!pending.isEmpty()) {
            List<A2ATask> readyTasks = tasks.stream()
                    .filter(t -> !completedIds.contains(t.getId()) && isReady(t, completedIds))
                    .toList();

            if (readyTasks.isEmpty() && !pending.isEmpty()) {
                log.error("A2AOrchestrator.executeTasks - 死锁任务: {}", pending);
                break;
            }

            log.info("A2AOrchestrator.executeTasks - 执行 {} 个 ready 任务: {}",
                    readyTasks.size(), readyTasks.stream().map(A2ATask::getId).toList());

            if (readyTasks.size() == 1) {
                A2ATask task = readyTasks.get(0);
                AgentResult result = executeTask(task, taskOutputs, params);
                if (result != null && result.isSuccess()) {
                    taskOutputs.put(task.getId(), result.getData());
                }
                taskResults.put(task.getId(), result);
                completedIds.add(task.getId());
            } else {
                Map<String, AgentResult> parallelResults = executeTasksParallel(readyTasks, taskOutputs, params);
                for (Map.Entry<String, AgentResult> entry : parallelResults.entrySet()) {
                    AgentResult result = entry.getValue();
                    if (result != null && result.isSuccess()) {
                        A2ATask task = taskMap.get(entry.getKey());
                        if (task != null) taskOutputs.put(task.getId(), result.getData());
                    }
                    taskResults.put(entry.getKey(), result);
                    completedIds.add(entry.getKey());
                }
            }
            pending.removeAll(completedIds);
        }

        List<AgentResult> allResults = new ArrayList<>(taskResults.values());
        log.info("A2AOrchestrator.executeTasks - 执行完成, totalResults: {}", allResults.size());
        return allResults;
    }

    private boolean isReady(A2ATask task, Set<String> completed) {
        if (CollectionUtils.isEmpty(task.getDependsOn())) return true;
        return completed.containsAll(task.getDependsOn());
    }

    private Map<String, AgentResult> executeTasksParallel(List<A2ATask> tasks,
                                                           Map<String, Map<String, Object>> taskOutputs,
                                                           Map<String, Object> params) {
        Map<String, AgentResult> results = new LinkedHashMap<>();
        List<CompletableFuture<Map.Entry<String, AgentResult>>> futures = new ArrayList<>();

        for (A2ATask task : tasks) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> new AbstractMap.SimpleEntry<>(task.getId(), executeTask(task, taskOutputs, params)),
                    agentParallelExecutor));
        }

        try { CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join(); }
        catch (Exception e) { log.error("A2AOrchestrator.executeTasksParallel - 并行执行异常", e); }

        for (CompletableFuture<Map.Entry<String, AgentResult>> future : futures) {
            try {
                Map.Entry<String, AgentResult> entry = future.getNow(null);
                if (entry != null) results.put(entry.getKey(), entry.getValue());
            } catch (Exception e) { log.error("A2AOrchestrator.executeTasksParallel - 收集结果异常", e); }
        }
        return results;
    }

    // ==================== 单任务执行 ====================

    @SuppressWarnings("unchecked")
    private AgentResult executeTask(A2ATask task,
                                     Map<String, Map<String, Object>> taskOutputs,
                                     Map<String, Object> params) {
        log.info("A2AOrchestrator.executeTask - 执行任务, id: {}, capabilityId: {}", task.getId(), task.getCapabilityId());

        try {
            List<AgentCard> candidates = agentRegistry.findByCapability(task.getCapabilityId());
            if (candidates.isEmpty()) {
                log.error("A2AOrchestrator.executeTask - 未找到匹配的 Agent, capabilityId: {}", task.getCapabilityId());
                task.setStatus("failed");
                return AgentResult.builder().agentName("orchestrator").success(false)
                        .error("未找到能力 [" + task.getCapabilityId() + "] 对应的 Agent").confidence(0.0).build();
            }

            AgentCard selectedCard = candidates.get(0);
            BaseAgent agent = agentRegistry.getAgent(selectedCard.getName());
            if (agent == null) {
                task.setStatus("failed");
                return AgentResult.builder().agentName("orchestrator").success(false)
                        .error("Agent [" + selectedCard.getName() + "] 未注册").confidence(0.0).build();
            }

            task.setAssignedAgent(selectedCard.getName());
            task.setStatus("running");
            log.info("A2AOrchestrator.executeTask - 匹配到 Agent: {}, capabilityId: {}",
                    selectedCard.getName(), task.getCapabilityId());

            Map<String, Object> subParams = buildTaskInput(task, taskOutputs, params);
            AgentResult result = agent.runAsync(subParams).join();

            if (result.isSuccess()) {
                task.setOutput(result.getData());
                task.setStatus("completed");
            } else {
                task.setStatus("failed");
            }

            log.info("A2AOrchestrator.executeTask - 任务完成, id: {}, agent: {}, success: {}",
                    task.getId(), selectedCard.getName(), result.isSuccess());
            return result;
        } catch (Exception e) {
            log.error("A2AOrchestrator.executeTask - 任务执行异常, id: {}, capabilityId: {}",
                    task.getId(), task.getCapabilityId(), e);
            task.setStatus("failed");
            return AgentResult.builder().agentName("orchestrator").success(false)
                    .error(e.getMessage()).confidence(0.0).build();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildTaskInput(A2ATask task,
                                                Map<String, Map<String, Object>> taskOutputs,
                                                Map<String, Object> params) {
        Map<String, Object> subParams = new HashMap<>(params);

        if (task.getInput() != null && !task.getInput().isEmpty()) {
            subParams.putAll(task.getInput());
        }

        if (conversationContext != null && conversationContext.getAvailableData() != null
                && !conversationContext.getAvailableData().isEmpty()) {
            subParams.put("contextData", new HashMap<>(conversationContext.getAvailableData()));
            Map<String, Object> entities = subParams.get("entities") instanceof Map
                    ? new HashMap<>((Map<String, Object>) subParams.get("entities"))
                    : new HashMap<>();
            for (Map.Entry<String, Object> entry : conversationContext.getAvailableData().entrySet()) {
                entities.putIfAbsent("history_" + entry.getKey(), entry.getValue());
            }
            subParams.put("entities", entities);
        }

        if (!CollectionUtils.isEmpty(task.getDependsOn())) {
            for (String depId : task.getDependsOn()) {
                Map<String, Object> depOutput = taskOutputs.get(depId);
                if (depOutput != null) {
                    subParams.put("upstream_" + depId, depOutput);
                    if (depOutput.containsKey("products")) {
                        subParams.putIfAbsent("products", depOutput.get("products"));
                    }
                    if (depOutput.containsKey("reply")) {
                        subParams.putIfAbsent("upstream_reply_" + depId, depOutput.get("reply"));
                    }
                }
            }
        }

        return subParams;
    }

    // ==================== 降级 ====================

    private List<AgentResult> fallbackSerialExecute(List<IntentItem> intents, Map<String, Object> params) {
        log.warn("A2AOrchestrator.fallbackSerialExecute - 降级为串行执行, intentCount: {}", intents.size());
        List<AgentResult> results = new ArrayList<>();
        Map<String, Map<String, Object>> accumulated = new LinkedHashMap<>();

        for (IntentItem item : intents) {
            List<AgentCard> candidates = agentRegistry.findByCapability(item.getIntent());
            if (candidates.isEmpty()) {
                log.warn("A2AOrchestrator.fallbackSerialExecute - 未找到 Agent, intent: {}", item.getIntent());
                continue;
            }

            BaseAgent agent = agentRegistry.getAgent(candidates.get(0).getName());
            if (agent == null) continue;

            Map<String, Object> subParams = new HashMap<>(params);
            subParams.put("entities", item.getEntities());
            for (Map.Entry<String, Map<String, Object>> entry : accumulated.entrySet()) {
                subParams.put("upstream_" + entry.getKey(), entry.getValue());
                if (entry.getValue().containsKey("products")) {
                    subParams.putIfAbsent("products", entry.getValue().get("products"));
                }
            }

            try {
                AgentResult result = agent.runAsync(subParams).join();
                results.add(result);
                if (result.isSuccess() && result.getData() != null) {
                    accumulated.put(item.getIntent(), result.getData());
                }
            } catch (Exception e) {
                log.error("A2AOrchestrator.fallbackSerialExecute - Agent执行异常, intent: {}",
                        item.getIntent(), e);
            }
        }
        return results;
    }
}
