package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.RecResult;
import com.ecommerce.service.MemoryService;
import com.ecommerce.tool.ProductSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐意图Agent
 *
 * <p>基于 LLM + Tools + entity() 的真正 Agent 模式：
 * 将对话上下文、用户画像、实体信息组装为提示词，注册 {@link ProductSearchTool} 工具，
 * 由大模型理解用户意图后自主决定调用哪个工具获取商品，最后通过 .entity() 返回结构化结果。</p>
 *
 * <p>同时为 {@link CompareIntentAgent} 提供 {@link #resolveProductsForComparison} 方法，
 * 该方法不使用用户画像（withoutUserProfile），仅根据会话上下文 + 意图实体让 LLM 定位商品。</p>
 */
@Component
public class RecommendIntentAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private MemoryService memoryService;

    @Resource
    private ProductSearchTool productSearchTool;

    public RecommendIntentAgent() {
        super("recommend_intent", 10.0, 2);
    }

    // ==================== 系统提示词 ====================

    /**
     * 推荐场景系统提示词
     */
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

    /**
     * 对比场景系统提示词（withoutUserProfile）
     */
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

    // ==================== Agent 主入口（withUserProfile） ====================

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

        // 步骤2 构建带用户画像的提示词
        String userMessage = buildRecommendUserMessage(userId, message, sessionId, history, summary, entities);

        // 步骤3 利用大模型调用工具，获取对应的数据
        RecResult result = chatClient.prompt()
                .system(RECOMMEND_SYSTEM_PROMPT)
                .tools(productSearchTool)
                .user(userMessage)
                .call()
                .entity(RecResult.class);

        List<Product> products = result != null && !CollectionUtils.isEmpty(result.getProducts())
                ? result.getProducts() : Collections.emptyList();

        // 步骤4: 生成推荐回复
        String reply = generateRecommendReply(products);

        // 步骤5: 将推荐商品ID保存到entities中，供后续对比使用
        Map<String, Object> data = new HashMap<>();
        data.put("reply", reply);
        data.put("products", products);

        // 步骤6: 保存推荐商品ID供后续对比使用
        Map<String, Object> enrichedEntities = new HashMap<>(entities != null ? entities : new HashMap<>());
        enrichedEntities.put("recommended_product_ids", products.stream()
                .map(Product::getProductId)
                .collect(Collectors.toList()));
        data.put("entities", enrichedEntities);

        log.info("RecommendIntentAgent.execute - 完成, 商品数: {}", products.size());
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.9).build();
    }

    // ==================== 对比场景商品定位（withoutUserProfile） ====================

    /**
     * 为对比场景定位商品（<b>withoutUserProfile</b>）
     *
     * <p>与 {@link #execute} 的核心差异：
     * <ul>
     *   <li>不使用用户画像（UserProfile）—— 对比不关心用户偏好，只需精准定位商品</li>
     *   <li>系统提示词引导 LLM 优先使用 getProductsByIds / retrieveSimilarProducts，
     *       根据实体内容（product_ids / indices / product_names / brand / category）选择策略</li>
     *   <li>通过 .entity(RecResult.class) 获取 LLM 结构化输出，
     *       而非直接调用 Service 方法</li>
     * </ul>
     *
     * @param message   用户原始消息（如"比较华为和联想电脑"）
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param history   对话历史
     * @param summary   对话摘要
     * @param entities  意图识别实体（透传，LLM 自行判断如何使用）
     * @return 定位到的商品列表
     */
    @SuppressWarnings("unchecked")
    public List<Product> resolveProductsForComparison(String message, String userId, String sessionId,
                                                       List<String> history, String summary,
                                                       Map<String, Object> entities) {
        log.info("RecommendIntentAgent.resolveProductsForComparison (withoutUserProfile) - message: {}, entities: {}",
                message, entities != null ? entities.keySet() : "null");

        // 合并会话上下文得到完整实体
        Map<String, Object> mergedEntities = memoryService.mergeWithSessionMemory(sessionId,
                entities != null ? entities : new HashMap<>());

        // 构建对比场景用户提示词
        String userMessage = buildComparisonUserMessage(message, history, summary, mergedEntities);

        // LLM + Tools + entity() — LLM 根据上下文自主选择工具定位商品
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

    /**
     * 构建推荐场景用户提示词（含用户画像）
     */
    private String buildRecommendUserMessage(String userId, String message, String sessionId,
                                              List<String> history, String summary,
                                              Map<String, Object> entities) {
        StringBuilder sb = new StringBuilder();

        // 用户标识
        sb.append(String.format("用户ID: %s\n", userId));

        // 长期记忆：查询 user_profile 表的持久化画像数据
        String longTermContext = memoryService.buildLongTermContext(userId);
        if (longTermContext != null && !longTermContext.isEmpty()) {
            sb.append("\n【长期偏好】\n").append(longTermContext).append("\n");
        }

        // 短期记忆：本次会话中合并跨轮实体得到的临时偏好
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

        // 已提取的实体
        if (entities != null && !entities.isEmpty()) {
            sb.append("\n【已提取实体】\n");
            entities.forEach((k, v) -> sb.append(String.format("  %s: %s\n", k, v)));
        }

        sb.append("\n请根据以上信息，选择合适的工具为用户推荐商品。");
        return sb.toString();
    }

    /**
     * 构建对比场景用户提示词（withoutUserProfile）
     */
    private String buildComparisonUserMessage(String message, List<String> history,
                                               String summary, Map<String, Object> mergedEntities) {
        StringBuilder sb = new StringBuilder();

        // 对话上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);
        if (!historyContext.isEmpty()) {
            sb.append("【对话上下文】\n").append(historyContext).append("\n\n");
        }

        // 用户原始问题
        sb.append("【用户问题】\n").append(message).append("\n\n");

        // 合并后的完整实体
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
        // 步骤1: 处理空列表
        if (products.isEmpty()) {
            return "抱歉，暂时没有合适的商品推荐给您。您可以告诉我更具体的需求，比如预算、品牌偏好等。";
        }

        // 步骤2: 拼接推荐列表文本（包含商品ID以便后续对比时识别）
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
