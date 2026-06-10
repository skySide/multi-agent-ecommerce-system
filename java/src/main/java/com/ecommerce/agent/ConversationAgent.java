package com.ecommerce.agent;

import com.ecommerce.entity.ConversationSession;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.IntentItem;
import com.ecommerce.model.response.MultiIntentRecognitionResult;
import com.ecommerce.orchestrator.A2AOrchestrator;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.RepeatedQuestionDetector;
import com.ecommerce.service.SessionQualityMetricsService;
import com.ecommerce.service.UserBehaviorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话编排Agent
 * 负责意图识别、委托编排、记忆管理
 *
 * 流程：
 *   ① 意图识别（LLM → 输出意图列表 + requiredData）
 *   ② 全部意图（单/多）统一委托 A2AOrchestrator（Plan & Execute 调度，A2A Protocol）
 *   ③ 更新记忆
 *   ④ 聚合结果返回
 *
 * 说明：session创建/校验、短期记忆获取由 ConversationServiceImpl 完成
 */
@Component
public class ConversationAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private MemoryService memoryService;

    @Resource
    private ConversationSessionService conversationSessionService;

    @Resource
    private UserBehaviorService userBehaviorService;

    @Resource
    private RepeatedQuestionDetector repeatedQuestionDetector;

    @Resource
    private SessionQualityMetricsService sessionQualityMetricsService;

    @Resource
    private A2AOrchestrator a2aOrchestrator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 正在执行的 CompletableFuture，key 为 sessionId，用于停止生成 */
    private final Map<String, CompletableFuture<AgentResult>> runningFutures = new ConcurrentHashMap<>();

    public ConversationAgent() {
        super("conversation", 15.0, 2);
    }

    /**
     * 运行并追踪 CompletableFuture，支持取消
     */
    public CompletableFuture<AgentResult> runAndTrack(String sessionId, Map<String, Object> params) {
        CompletableFuture<AgentResult> future = runAsync(params);
        runningFutures.put(sessionId, future);
        future.whenComplete((result, ex) -> runningFutures.remove(sessionId));
        return future;
    }

    /**
     * 取消指定会话的生成
     */
    public boolean cancelGeneration(String sessionId) {
        CompletableFuture<AgentResult> future = runningFutures.remove(sessionId);
        if (Objects.nonNull(future) && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            log.info("ConversationAgent.cancelGeneration - 取消生成, sessionId: {}, cancelled: {}", sessionId, cancelled);
            return cancelled;
        }
        log.info("ConversationAgent.cancelGeneration - 无正在执行的任务, sessionId: {}", sessionId);
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String userId = (String) params.get("userId");
        String sessionId = (String) params.get("sessionId");
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        log.info("ConversationAgent.execute - 开始处理, userId: {}, sessionId: {}, message: {}", userId, sessionId, message);

        // 步骤2: 计算当前轮次
        int currentRound = getCurrentRound(sessionId);

        // 步骤3: 多意图识别
        MultiIntentRecognitionResult multiIntentResult = recognizeIntents(message, history, summary);
        List<IntentItem> intentItems = multiIntentResult != null ? multiIntentResult.getIntents() : Collections.emptyList();

        log.info("ConversationAgent.execute - 意图识别结果, intentCount: {}, intents: {}",
                intentItems.size(), intentItems);

        // 步骤4: 按意图数分流
        List<AgentResult> subResults;
        String primaryIntent;

        if (CollectionUtils.isEmpty(intentItems)) {
            // 意图识别失败
            return buildErrorResult(sessionId, history, summary, currentRound, userId, message);
        } else {
            // 所有意图（单意图 or 多意图）统一委托 A2AOrchestrator（Plan & Execute）
            // 废除单意图快速路径：任何需要数据的意图都必须经过编排层协调，确保 contextData 注入
            primaryIntent = intentItems.get(0).getIntent();
            Map<String, Object> subParams = buildSubAgentParams(userId, sessionId, message, history, summary,
                    intentItems.get(0).getEntities());
            subResults = a2aOrchestrator.execute(intentItems, subParams);
        }

        // 步骤5: 提取回复和合并 entities
        String reply = aggregateReplies(subResults);
        Map<String, Object> finalEntities = mergeAllEntities(subResults);

        // 步骤6: 更新短期记忆
        history = updateSessionMemory(sessionId, history, message, reply, finalEntities, currentRound,
                primaryIntent, intentItems);

        // 步骤7: 异步检测重复提问
        triggerAsyncDetection(sessionId, message, primaryIntent, finalEntities, currentRound);

        // 步骤8: 转人工检测
        checkTransferToHuman(primaryIntent, sessionId, userId, currentRound);

        // 步骤9: 记录用户行为
        recordBehavior(userId, message);

        // 步骤10: 组装返回结果
        return buildAgentResult(sessionId, reply, primaryIntent, history, summary, finalEntities, subResults);
    }

    // ==================== 意图识别 ====================

    /**
     * 多意图识别
     * LLM 从单条用户消息中识别多个意图、实体及数据需求
     *
     * 核心改变：意图声明 requiredData（需要什么数据）而非 dependsOn（依赖哪个意图）。
     * 数据来源由 AgentOrchestrator 的 LLM 执行计划决定。
     */
    private MultiIntentRecognitionResult recognizeIntents(String message, List<String> history, String summary) {
        // 步骤1: 构建历史上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);

        // 步骤2: 调用 LLM 识别意图（输出 requiredData 替代 dependsOn）
        String prompt = String.format(
                "分析用户意图。用户可能同时包含多个意图，请识别所有意图，并分析每个意图执行需要什么数据。\n\n" +
                        "意图类型：\n" +
                        "- recommend: 用户想要推荐商品（端到端能力——内部自动搜索商品+获取画像，requiredData 应为空数组 []）\n" +
                        "- product_query: 用户询问某款商品\n" +
                        "- knowledge_query: 用户问售后/物流/优惠活动等知识性问题\n" +
                        "- compare: 用户对比商品\n" +
                        "- chitchat: 闲聊\n" +
                        "- transfer_to_human: 用户明确要求转人工客服\n\n" +
                        "数据需求类型（requiredData.type）——只在意图自身无法获取时才声明：\n" +
                        "- PRODUCT_LIST: 需要商品列表（仅 compare/product_query 使用——recommend 内部自己搜索，不需要）\n" +
                        "- POLICY_INFO: 需要政策信息（退换货、物流、售后、保修、优惠活动）\n" +
                        "- ORDER_INFO: 需要订单信息（订单状态、物流追踪）\n" +
                        "- USER_PROFILE: 需要用户画像（仅当用户画像需要被多个意图共享时才声明；recommend 内部自己获取，不需要）\n" +
                        "- GENERAL_KNOWLEDGE: 需要通用知识（品牌介绍、使用教程等）\n\n" +
                        "输出格式（JSON）：\n" +
                        "{\"intents\":[\n" +
                        "  {\"intent\":\"recommend\",\"entities\":{\"category\":\"手机\"},\"requiredData\":[]," +
                        "\"outputKey\":\"recommended_products\"},\n" +
                        "  {\"intent\":\"compare\",\"entities\":{\"brands\":[\"vivo\",\"小米\"]}," +
                        "\"requiredData\":[{\"type\":\"PRODUCT_LIST\",\"filterDescription\":\"vivo和小米品牌手机\"}]," +
                        "\"outputKey\":\"comparison_result\"},\n" +
                        "  {\"intent\":\"knowledge_query\",\"entities\":{\"query\":\"退换货政策\"}," +
                        "\"requiredData\":[{\"type\":\"POLICY_INFO\",\"filterDescription\":\"退换货政策\"}]," +
                        "\"outputKey\":\"policy_info\"}\n" +
                        "]}\n\n" +
                        "说明：\n" +
                        "- requiredData 声明此意图执行需要什么数据（类型 + 从用户消息提取的筛选条件）\n" +
                        "- 不需要数据的意图（如闲聊）requiredData 为空数组\n" +
                        "- requiredData 不包含数量——LLM 无法预知匹配数量，应从历史数据中筛选\n" +
                        "- outputKey 为此意图产出的数据标识 key\n" +
                        "- 如果只有一个意图，输出包含 1 个元素的数组\n" +
                        "- 重要：USER_PROFILE 是数据类型，不是意图！不要为'获取用户画像'生成单独的 intent。" +
                        "若推荐需要画像，在 recommend 的 requiredData 中声明 USER_PROFILE 即可\n\n" +
                        "%s用户消息：%s",
                historyContext, message
        );

        try {
            // 步骤3: 使用 BeanOutputConverter 进行 JSON → POJO 转换
            BeanOutputConverter<MultiIntentRecognitionResult> converter =
                    new BeanOutputConverter<>(MultiIntentRecognitionResult.class);
            String response = chatClient.prompt().user(prompt).call().content();
            log.info("ConversationAgent.recognizeIntents - LLM原始响应: {}", response);
            MultiIntentRecognitionResult result = converter.convert(response);
            if (result != null && !CollectionUtils.isEmpty(result.getIntents())) {
                return result;
            }
            return null;
        } catch (Exception e) {
            log.error("ConversationAgent.recognizeIntents - 多意图识别失败", e);
            return null;
        }
    }

    // ==================== 分流与聚合 ====================

    /**
     * 聚合所有子Agent 的回复
     */
    private String aggregateReplies(List<AgentResult> subResults) {
        if (CollectionUtils.isEmpty(subResults)) {
            return "抱歉，无法理解您的需求，请换个方式描述。";
        }

        if (subResults.size() == 1) {
            return extractReply(subResults.get(0));
        }

        // 多结果：拼接各子Agent 回复
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < subResults.size(); i++) {
            AgentResult result = subResults.get(i);
            if (result != null && result.isSuccess()) {
                String reply = extractReply(result);
                if (reply != null && !reply.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n\n---\n\n");
                    }
                    sb.append(reply);
                }
            }
        }

        String aggregated = sb.toString();
        if (aggregated.isBlank()) {
            return "抱歉，无法理解您的需求，请换个方式描述。";
        }
        return aggregated;
    }

    /**
     * 合并所有子Agent 返回的 entities
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeAllEntities(List<AgentResult> subResults) {
        Map<String, Object> merged = new HashMap<>();
        if (CollectionUtils.isEmpty(subResults)) {
            return merged;
        }

        for (AgentResult result : subResults) {
            if (result != null && result.getData() != null
                    && result.getData().get("entities") instanceof Map) {
                Map<String, Object> subEntities = (Map<String, Object>) result.getData().get("entities");
                merged.putAll(subEntities);
            }
        }
        return merged;
    }

    /**
     * 构建意图识别失败时的错误结果
     */
    private AgentResult buildErrorResult(String sessionId, List<String> history, String summary,
                                          int currentRound, String userId, String message) {
        String reply = "抱歉，无法理解您的需求，请换个方式描述。";
        Map<String, Object> finalEntities = new HashMap<>();

        // 更新记忆（即使失败也记录对话）
        history = updateSessionMemory(sessionId, history, message, reply, finalEntities, currentRound,
                "unknown", Collections.emptyList());

        triggerAsyncDetection(sessionId, message, "unknown", finalEntities, currentRound);
        recordBehavior(userId, message);

        return buildAgentResult(sessionId, reply, "unknown", history, summary, finalEntities, Collections.emptyList());
    }

    /**
     * 构建子Agent 统一参数
     */
    private Map<String, Object> buildSubAgentParams(String userId, String sessionId, String message,
                                                     List<String> history, String summary,
                                                     Map<String, Object> entities) {
        Map<String, Object> subParams = new HashMap<>();
        subParams.put("userId", userId);
        subParams.put("message", message);
        subParams.put("sessionId", sessionId);
        subParams.put("history", history);
        subParams.put("summary", summary);
        subParams.put("entities", entities);
        return subParams;
    }

    // ==================== 记忆管理 ====================

    private int getCurrentRound(String sessionId) {
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null || session.getRoundIntents() == null) {
            return 0;
        }
        try {
            List<Map<String, Object>> roundIntents = objectMapper.readValue(
                    session.getRoundIntents(), new TypeReference<>() {});
            return roundIntents.size();
        } catch (Exception e) {
            log.warn("ConversationAgent.getCurrentRound - 解析 round_intents 失败", e);
            return 0;
        }
    }

    private List<String> updateSessionMemory(String sessionId, List<String> history,
                                              String message, String reply,
                                              Map<String, Object> finalEntities,
                                              int currentRound, String primaryIntent,
                                              List<IntentItem> intentItems) {
        // 步骤1: 追加本轮对话
        history.add("用户: " + message);
        history.add("助手: " + reply);
        if (history.size() > 20) {
            history = history.subList(history.size() - 20, history.size());
        }

        // 步骤2: 保存对话历史和 merged entities
        memoryService.saveHistory(
                conversationSessionService.getBySessionId(sessionId),
                history, finalEntities);

        // 步骤3: 构建 round_intents 数据
        Map<String, Object> roundEntities = new HashMap<>();
        for (IntentItem item : intentItems) {
            if (item.getEntities() != null) {
                roundEntities.putAll(item.getEntities());
            }
        }

        // 步骤4: 更新 round_intents
        memoryService.updateRoundIntents(
                conversationSessionService.getBySessionId(sessionId),
                currentRound, primaryIntent, roundEntities);

        return history;
    }

    // ==================== 辅助方法 ====================

    private void triggerAsyncDetection(String sessionId, String message, String intent,
                                        Map<String, Object> entities, int currentRound) {
        final String finalSessionId = sessionId;
        CompletableFuture.runAsync(() -> {
            try {
                repeatedQuestionDetector.detect(
                        conversationSessionService.getBySessionId(finalSessionId),
                        message, intent, entities, currentRound);
            } catch (Exception e) {
                log.warn("ConversationAgent.triggerAsyncDetection - 重复提问检测异常, sessionId: {}", finalSessionId, e);
            }
        });
    }

    private void checkTransferToHuman(String intent, String sessionId, String userId, int currentRound) {
        if ("transfer_to_human".equals(intent)) {
            String metricValue = "{\"trigger\":\"user_request\",\"round\":" + currentRound + "}";
            sessionQualityMetricsService.recordTransferToHuman(sessionId, userId, metricValue, currentRound);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractReply(AgentResult result) {
        if (result != null && result.getData() != null) {
            Object reply = result.getData().get("reply");
            if (reply instanceof String) {
                return (String) reply;
            }
        }
        return "抱歉，我暂时无法处理您的请求，请稍后再试。";
    }

    private AgentResult buildAgentResult(String sessionId, String reply, String intent,
                                          List<String> history, String summary,
                                          Map<String, Object> finalEntities,
                                          List<AgentResult> subResults) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("reply", reply);
        resultData.put("sessionId", sessionId);
        resultData.put("intent", intent);
        resultData.put("dialogueHistory", history);
        resultData.put("summary", summary);
        resultData.put("entities", finalEntities);

        // 附件子任务结果（多意图场景）
        if (!CollectionUtils.isEmpty(subResults) && subResults.size() > 1) {
            List<Map<String, Object>> subTasks = new ArrayList<>();
            for (AgentResult sr : subResults) {
                Map<String, Object> task = new HashMap<>();
                task.put("agentName", sr.getAgentName());
                task.put("success", sr.isSuccess());
                task.put("latencyMs", sr.getLatencyMs());
                if (sr.getData() != null) {
                    task.put("reply", sr.getData().get("reply"));
                }
                subTasks.add(task);
            }
            resultData.put("subTasks", subTasks);
        } else if (subResults != null && subResults.size() == 1) {
            // 单结果：合并其 data 中的关键字段
            AgentResult singleResult = subResults.get(0);
            if (singleResult.getData() != null) {
                resultData.putAll(singleResult.getData());
            }
        }

        log.info("ConversationAgent.buildAgentResult - 处理完成, sessionId: {}, intent: {}", sessionId, intent);
        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(resultData)
                .confidence(subResults.isEmpty() ? 0.5 : subResults.get(0).getConfidence())
                .build();
    }

    private void recordBehavior(String userId, String message) {
        try {
            userBehaviorService.recordBehavior(userId, null, "chat", message, "conversation");
        } catch (Exception e) {
            log.warn("ConversationAgent.recordBehavior - 记录行为失败", e);
        }
    }
}
