package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.RecResult;
import com.ecommerce.tool.ProductSearchTool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐意图Agent
 *
 * 基于 ReAct（Observe → Think → Act）模式：
 * 将对话上下文、用户画像、实体信息组装为提示词，注册 ProductSearchTool 工具，
 * 由大模型理解用户意图后自主决定调用哪个工具获取商品，通过 .entity() 返回结构化结果。
 *
 * 同时为 CompareIntentAgent 提供 resolveProductsForComparison 方法，
 * 该方法不使用用户画像（withoutUserProfile），仅根据会话上下文 + 意图实体让 LLM 定位商品。
 */
@Component
public class RecommendIntentAgent extends ReActAgent {

    @Resource
    private ProductSearchTool productSearchTool;

    // ==================== 系统提示词 ====================

    private static final String RECOMMEND_SYSTEM_PROMPT = """
            你是一个资深的电商商品推荐专家。你必须先调用工具获取商品数据，不能直接回答。
            工具返回的是数据库中完整的商品信息（Product），请严格基于工具返回的结果做推荐决策，
            不要自己编造商品数据。

            可用工具：
            - retrieveSimilarProducts：向量语义搜索，根据商品名/品牌/类目返回相似商品
            - recommendProducts：多路召回+精排+多样性控制，最常用的推荐工具
            - getHotProducts：按销量排序的热门商品，适用于用户想看热门/畅销排行
            - getProductsByIds：根据商品ID列表批量获取商品详情
            - getProductInfo：查询单个商品的详细信息

            请根据用户需求选择最合适的工具获取商品，然后将推荐结果按以下JSON格式输出：
            {"products":[{"productId":"P001","productName":"iPhone 16","brand":"Apple","price":7999}]}
            """;

    private static final String COMPARISON_SYSTEM_PROMPT = """
            你是一个电商商品搜索专家，负责根据上下文精准定位用户想对比的商品。
            你必须先调用工具获取商品数据，不能直接回答。工具返回的是数据库中完整的商品信息（Product），
            请严格基于工具返回的结果做决策，不要自己编造商品数据。

            可用工具：
            - getProductsByIds：根据商品ID列表批量获取商品详情，最高效
            - retrieveSimilarProducts：向量语义搜索，根据名称/品牌/类目召回商品
            - getProductInfo：查询单个商品的详细信息
            - recommendProducts：多路召回推荐引擎，作为兜底方案

            请根据上下文信息选择最合适的工具定位商品，然后将结果按以下JSON格式输出：
            {"products":[{"productId":"P001","productName":"iPhone 16","brand":"Apple","price":7999}]}
            """;

    // ==================== 构造与初始化 ====================

    public RecommendIntentAgent() {
        super("recommend_intent", 10.0, 2);
    }

    @Override
    public com.ecommerce.model.AgentCard getAgentCard() {
        return com.ecommerce.model.AgentCard.builder()
                .name("recommend_intent")
                .description("根据用户偏好和上下文推荐商品，支持多路召回和个性化排序")
                .capabilities(List.of(
                        com.ecommerce.model.AgentCapability.builder()
                                .id("recommend_products")
                                .name("商品推荐")
                                .description("【端到端】基于用户画像和对话上下文推荐商品。内部自动获取用户画像+搜索商品，无需前置任务")
                                .tags(List.of("recommend", "product"))
                                .build()
                ))
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "message", Map.of("type", "string", "description", "用户推荐请求"),
                                "category", Map.of("type", "string", "description", "商品类目")
                        ),
                        "required", List.of()
                ))
                .outputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "products", Map.of("type", "array", "description", "推荐商品列表"),
                                "reply", Map.of("type", "string", "description", "推荐理由")
                        )
                ))
                .build();
    }

    @PostConstruct
    public void init() {
        registerTool(productSearchTool);
    }

    // ==================== ReActAgent 抽象方法实现 ====================

    @Override
    protected String buildSystemPrompt(Map<String, Object> params) {
        return RECOMMEND_SYSTEM_PROMPT;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildUserMessage(Map<String, Object> params) {
        // 步骤1: 提取参数
        String userId = (String) params.get("userId");
        String message = (String) params.get("message");
        String sessionId = (String) params.get("sessionId");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");

        return buildRecommendUserMessage(userId, message, sessionId, history, summary, entities);
    }

    /**
     * 重写 execute() 以支持 .entity(RecResult.class) 结构化输出
     * 在 ReAct 框架的基础上保留对商品实体的精确获取
     */
    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String userId = (String) params.get("userId");
        String message = (String) params.get("message");
        String sessionId = (String) params.get("sessionId");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");

        log.info("RecommendIntentAgent.execute - userId: {}, entities: {}", userId,
                entities != null ? entities.keySet() : "null");

        // 步骤2: Think → 构建带用户画像的提示词
        String systemPrompt = buildSystemPrompt(params);
        String userMessage = buildRecommendUserMessage(userId, message, sessionId, history, summary, entities);

        // 步骤3: Act → 调用 LLM + Tools（Spring AI 内部处理 Tool Calling 循环）
        // ReAct 循环上限 3 轮，每轮检查中断标志
        int iteration = 0;
        RecResult recResult = null;

        while (iteration < MAX_ITERATIONS && !Thread.currentThread().isInterrupted()) {
            iteration++;
            log.info("RecommendIntentAgent.execute - 第{}轮 Think+Act, userId: {}", iteration, userId);

            recResult = chatClient.prompt()
                    .system(systemPrompt)
                    .tools(getTools().toArray())
                    .user(userMessage)
                    .call()
                    .entity(RecResult.class);

            // Observe → 检查结果
            if (recResult != null && !CollectionUtils.isEmpty(recResult.getProducts())) {
                log.info("RecommendIntentAgent.execute - 第{}轮 Observe 成功, 商品数: {}",
                        iteration, recResult.getProducts().size());
                break;
            }

            log.warn("RecommendIntentAgent.execute - 第{}轮 Observe 为空", iteration);
        }

        // 处理中断
        if (Thread.currentThread().isInterrupted()) {
            log.warn("RecommendIntentAgent.execute - 检测到线程中断");
            return fallback(0, new InterruptedException("Agent interrupted"));
        }

        // 步骤4: 提取商品列表并生成回复
        List<Product> products = recResult != null && !CollectionUtils.isEmpty(recResult.getProducts())
                ? recResult.getProducts() : Collections.emptyList();
        String reply = generateRecommendReply(products);

        // 步骤5: 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("reply", reply);
        data.put("products", products);

        Map<String, Object> enrichedEntities = new HashMap<>(entities != null ? entities : new HashMap<>());
        enrichedEntities.put("recommended_product_ids", products.stream()
                .map(Product::getProductId)
                .collect(Collectors.toList()));
        data.put("entities", enrichedEntities);

        log.info("RecommendIntentAgent.execute - 完成, 商品数: {}", products.size());
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.9).build();
    }

    @Override
    protected AgentResult buildResult(String llmResponse, Map<String, Object> params) {
        // 默认实现（由 execute() 覆盖时不会调用至此）
        return AgentResult.builder().agentName(name).success(true)
                .data(Map.of("reply", llmResponse)).confidence(1.0).build();
    }

    // ==================== 对比场景商品定位（withoutUserProfile） ====================

    /**
     * 为对比场景定位商品（withoutUserProfile）
     *
     * 与 execute 的核心差异：不使用用户画像，LLM 优先使用 getProductsByIds / retrieveSimilarProducts
     */
    @SuppressWarnings("unchecked")
    public List<Product> resolveProductsForComparison(String message, String userId, String sessionId,
                                                       List<String> history, String summary,
                                                       Map<String, Object> entities) {
        log.info("RecommendIntentAgent.resolveProductsForComparison - message: {}, entities: {}",
                message, entities != null ? entities.keySet() : "null");

        // 步骤1: 合并会话上下文得到完整实体
        Map<String, Object> mergedEntities = memoryService.mergeWithSessionMemory(sessionId,
                entities != null ? entities : new HashMap<>());

        // 步骤2: 构建对比场景用户提示词
        String userMessage = buildComparisonUserMessage(message, history, summary, mergedEntities);

        // 步骤3: LLM + Tools + entity()
        RecResult result = chatClient.prompt()
                .system(COMPARISON_SYSTEM_PROMPT)
                .tools(productSearchTool)
                .user(userMessage)
                .call()
                .entity(RecResult.class);

        List<Product> products = result != null && !CollectionUtils.isEmpty(result.getProducts())
                ? result.getProducts() : Collections.emptyList();

        log.info("RecommendIntentAgent.resolveProductsForComparison - 完成, 商品数: {}", products.size());
        return products;
    }

    // ==================== 提示词构建 ====================

    private String buildRecommendUserMessage(String userId, String message, String sessionId,
                                              List<String> history, String summary,
                                              Map<String, Object> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("用户ID: %s\n", userId));

        // 长期记忆
        String longTermContext = memoryService.buildLongTermContext(userId);
        if (longTermContext != null && !longTermContext.isEmpty()) {
            sb.append("\n【长期偏好】\n").append(longTermContext).append("\n");
        }

        // 短期记忆
        Map<String, Object> mergedEntities = memoryService.mergeWithSessionMemory(sessionId, entities);
        UserProfile profile = memoryService.buildProfileFromEntities(userId, mergedEntities);
        sb.append(String.format("\n【短期偏好】%s\n", profileToString(profile)));

        // 推荐数量
        int numItems = mergedEntities.get("num_items") instanceof Number
                ? ((Number) mergedEntities.get("num_items")).intValue() : 6;
        sb.append(String.format("需要推荐数量: %d\n", numItems));

        // 对话上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);
        if (!historyContext.isEmpty()) {
            sb.append("\n【对话上下文】\n").append(historyContext).append("\n");
        }

        // 当前消息
        sb.append("\n【用户当前需求】\n").append(message).append("\n");

        // 已提取实体
        if (entities != null && !entities.isEmpty()) {
            sb.append("\n【已提取实体】\n");
            entities.forEach((k, v) -> sb.append(String.format("  %s: %s\n", k, v)));
        }

        sb.append("\n请根据以上信息，选择合适的工具为用户推荐商品。");
        return sb.toString();
    }

    private String buildComparisonUserMessage(String message, List<String> history,
                                               String summary, Map<String, Object> mergedEntities) {
        StringBuilder sb = new StringBuilder();
        String historyContext = memoryService.buildHistoryContext(history, summary);
        if (!historyContext.isEmpty()) {
            sb.append("【对话上下文】\n").append(historyContext).append("\n\n");
        }
        sb.append("【用户问题】\n").append(message).append("\n\n");
        if (mergedEntities != null && !mergedEntities.isEmpty()) {
            sb.append("【实体信息（含会话历史）】\n");
            mergedEntities.forEach((k, v) -> sb.append(String.format("  %s: %s\n", k, v)));
            sb.append("\n");
        }
        sb.append("请根据以上上下文和实体信息，选择合适的工具定位用户想对比的商品。");
        return sb.toString();
    }

    // ==================== 回复生成 ====================

    private String generateRecommendReply(List<Product> products) {
        if (products.isEmpty()) {
            return "抱歉，暂时没有合适的商品推荐给您。您可以告诉我更具体的需求，比如预算、品牌偏好等。";
        }
        StringBuilder sb = new StringBuilder("根据您的需求，我为您精选了以下商品：\n\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append(String.format("%d. %s（%s）¥%.0f - %s\n",
                    i + 1, p.getProductName(), p.getBrand(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                    p.getCategoryName()));
        }
        sb.append("\n如果您想了解某款商品的详细信息，直接问我就可以啦！\n");
        sb.append("您也可以说\"比较第1个和第2个\"或\"对比这几款手机\"来对比商品。");
        return sb.toString();
    }

    private String profileToString(UserProfile profile) {
        if (profile == null) {
            return "未获取到用户画像";
        }
        return String.format("偏好类目=%s, 品牌=%s, 价格区间=%.0f-%.0f, 分群=%s",
                profile.getPreferredCategories() != null ? profile.getPreferredCategories() : "无",
                profile.getPreferredBrands() != null ? profile.getPreferredBrands() : "无",
                profile.getPriceRangeMin() != null ? profile.getPriceRangeMin().doubleValue() : 0,
                profile.getPriceRangeMax() != null ? profile.getPriceRangeMax().doubleValue() : 99999,
                profile.getSegments() != null ? profile.getSegments() : "无");
    }
}
