package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.IntentRecognitionResult;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.UserBehaviorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 对话编排Agent
 * 负责意图识别、子Agent路由分发、记忆管理
 * 
 * 流程：
 *   ① 意图识别（LLM）
 *   ② 日志打印意图识别结果
 *   ③ 根据意图路由到对应的子Agent
 *   ④ 日志打印路由目标
 *   ⑤ 调用子Agent执行
 *   ⑥ 更新记忆（保存对话历史）
 *   ⑦ 返回结果
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
    private List<BaseAgent> intentAgents;

    private final Map<String, BaseAgent> intentRouter = new LinkedHashMap<>();

    /** 意图 → 子Agent名称 映射 */
    private static final Map<String, String> INTENT_AGENT_MAPPING = Map.of(
            "recommend", "recommend_intent",
            "product_query", "product_query_intent",
            "knowledge_query", "knowledge_intent",
            "compare", "compare_intent",
            "chitchat", "chitchat_intent"
    );

    public ConversationAgent() {
        super("conversation", 15.0, 2);
    }

    @PostConstruct
    public void init() {
        for (BaseAgent agent : intentAgents) {
            if (agent == this) {
                continue;
            }
            for (Map.Entry<String, String> entry : INTENT_AGENT_MAPPING.entrySet()) {
                if (entry.getValue().equals(agent.name)) {
                    intentRouter.put(entry.getKey(), agent);
                    break;
                }
            }
        }
        log.info("ConversationAgent.init - 意图路由注册完成, 共{}个意图, actual={}", INTENT_AGENT_MAPPING.size(), intentRouter.size());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数（sessionId、history、summary 已由 ConversationServiceImpl 处理）
        String userId = (String) params.get("userId");
        String sessionId = (String) params.get("sessionId");
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        log.info("ConversationAgent.execute - 开始处理, userId: {}, sessionId: {}, message: {}", userId, sessionId, message);

        // 步骤2: 意图识别（LLM）
        IntentRecognitionResult intentResult = recognizeIntent(message, history, summary);
        String intent = intentResult.getIntent();
        Map<String, Object> entities = intentResult.getEntities();

        log.info("ConversationAgent.execute - 意图识别结果 ===> intent: {}, entities: {}", intent, entities);

        // 步骤3: 根据意图路由到对应的子Agent
        BaseAgent subAgent = intentRouter.get(intent);
        if (subAgent == null) {
            log.warn("ConversationAgent.execute - 未识别的意图: {}, 降级为chitchat", intent);
            subAgent = intentRouter.get("chitchat");
        }

        log.info("ConversationAgent.execute - 路由决策 ===> intent: {} -> subAgent: {}, entityKeys: {}",
                intent, subAgent.name,
                entities != null ? entities.keySet() : "none");

        // 步骤4: 构建子Agent参数并调用
        Map<String, Object> subParams = new HashMap<>();
        subParams.put("userId", userId);
        subParams.put("message", message);
        subParams.put("sessionId", sessionId);
        subParams.put("history", history);
        subParams.put("summary", summary);
        subParams.put("entities", entities);

        AgentResult subResult = subAgent.runAsync(subParams).join();

        // 步骤5: 提取回复内容
        String reply = extractReply(subResult);

        // 步骤6: 合并entities（子Agent可能返回增强的entities，如推荐商品列表）
        Map<String, Object> finalEntities = entities;
        if (subResult.getData() != null && subResult.getData().get("entities") instanceof Map) {
            Map<String, Object> subEntities = (Map<String, Object>) subResult.getData().get("entities");
            finalEntities = new HashMap<>(entities != null ? entities : new HashMap<>());
            finalEntities.putAll(subEntities);
        }

        // 步骤7: 更新短期记忆（保存对话历史和entities）
        history.add("用户: " + message);
        history.add("助手: " + reply);
        if (history.size() > 20) {
            history = history.subList(history.size() - 20, history.size());
        }
        memoryService.saveHistory(
                conversationSessionService.getBySessionId(sessionId),
                history, finalEntities);

        // 步骤7: 记录用户行为
        recordBehavior(userId, message);

        // 步骤8: 组装返回结果
        Map<String, Object> resultData = new HashMap<>(subResult.getData() != null ? subResult.getData() : new HashMap<>());
        resultData.putIfAbsent("reply", reply);
        resultData.put("sessionId", sessionId);
        resultData.put("intent", intent);
        resultData.put("dialogueHistory", history);
        resultData.put("summary", summary);
        resultData.put("entities", finalEntities);

        log.info("ConversationAgent.execute - 处理完成, userId: {}, intent: {}, subAgent: {}", userId, intent, subAgent.name);
        return AgentResult.builder()
                .agentName(name)
                .success(subResult.isSuccess())
                .data(resultData)
                .confidence(subResult.getConfidence())
                .build();
    }

    private IntentRecognitionResult recognizeIntent(String message, List<String> history, String summary) {
        // 步骤1: 构建历史上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);

        // 步骤2: 调用LLM识别意图
        String prompt = String.format(
                "分析用户意图。\n" +
                        "意图说明：\n" +
                        "- recommend: 用户想要推荐商品（如\"推荐手机\"\"适合学生的笔记本\"）\n" +
                        "- product_query: 用户询问某款商品（如\"iPhone 16 多少钱\"\"华为 Mate 70 怎么样\"）\n" +
                        "- knowledge_query: 用户问售后/物流/优惠活动等知识性问题\n" +
                        "- compare: 用户对比商品（如\"iPhone 和 华为哪个好\"\"对比这两款笔记本\"\"比较一下刚才推荐的\"\"比较第1个和第2个\"）\n" +
                        "- chitchat: 闲聊\n" +
                        "\n" +
                        "实体说明：\n" +
                        "- category: 类目（如\"手机\"）\n" +
                        "- brand: 品牌（如\"华为\"）\n" +
                        "- price_min, price_max: 价格区间\n" +
                        "- product_name: 单个商品名\n" +
                        "- product_names: 商品名数组（如[\"iPhone 16\", \"华为 Mate 70\"]）\n" +
                        "- product_ids: 商品ID数组（如果用户明确指定了商品ID）\n" +
                        "- indices: 序号数组（当用户说\"比较第1个和第2个\"时，提取序号，如[1, 2]）\n" +
                        "- all: 布尔值（当用户说\"比较这几个\"\"对比刚才推荐的\"等指代性语句时，设为true）\n" +
                        "- num_items: 推荐数量\n" +
                        "\n" +
                        "注意：\n" +
                        "1. 当用户说\"比较第1个和第2个\"\"对比第2个和第3个\"时，提取indices，如[1, 2]或[2, 3]\n" +
                        "2. 当用户说\"比较这几个\"\"对比刚才推荐的\"等指代性语句时，设置all=true\n" +
                        "3. 当用户直接说商品名时，提取到product_names中\n" +
                        "\n" +
                        "%s用户消息：%s",
                historyContext, message
        );

        try {
            IntentRecognitionResult result = chatClient.prompt().user(prompt).call().entity(IntentRecognitionResult.class);
            if (result != null && result.getIntent() != null) {
                return result;
            }
            return new IntentRecognitionResult("chitchat", new HashMap<>());
        } catch (Exception e) {
            log.error("ConversationAgent.recognizeIntent 意图识别失败", e);
            return new IntentRecognitionResult("chitchat", new HashMap<>());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractReply(AgentResult result) {
        if (result.getData() != null) {
            Object reply = result.getData().get("reply");
            if (reply instanceof String) {
                return (String) reply;
            }
        }
        return "抱歉，我暂时无法处理您的请求，请稍后再试。";
    }

    private void recordBehavior(String userId, String message) {
        try {
            userBehaviorService.recordBehavior(userId, null, "chat", message, "conversation");
        } catch (Exception e) {
            log.warn("ConversationAgent.recordBehavior 记录行为失败", e);
        }
    }
}
