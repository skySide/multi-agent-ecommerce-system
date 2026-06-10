package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.RecResult;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.QueryRewriteService;
import com.ecommerce.service.RecommendEngineService;
import com.ecommerce.tool.ProductSearchTool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 商品查询意图Agent
 *
 * 基于 ReAct（Tool Calling）模式：
 * 将对话上下文、历史数据、实体信息组装为提示词，注册 ProductSearchTool 工具，
 * 由大模型理解用户意图后自主决定调用哪个工具获取商品，通过 .entity() 返回结构化结果。
 *
 * 核心改进：
 * 1. 注册 ProductSearchTool，让 LLM 可以调用 getProductsByIds、retrieveSimilarProducts 等
 * 2. 感知 contextData（历史累积数据），优先使用历史商品 ID 获取
 * 3. 保留原有硬编码逻辑作为降级方案
 */
@Component
public class ProductQueryIntentAgent extends ReActAgent {

    @Resource
    private ProductSearchTool productSearchTool;

    @Resource
    private ProductService productService;

    @Resource
    private RecommendEngineService recommendEngineService;

    @Resource
    private QueryRewriteService queryRewriteService;

    // ==================== 系统提示词 ====================

    private static final String SYSTEM_PROMPT = """
            你是一个电商商品查询专家。你必须先调用工具获取商品数据，不能直接回答。
            工具返回的是数据库中完整的商品信息（Product），请严格基于工具返回的结果做决策，
            不要自己编造商品数据。

            【重要】你需要根据上下文决定使用哪个工具：
            1. 如果 message 或 contextData 中包含历史推荐的商品ID列表，优先使用 getProductsByIds 获取这些商品
            2. 【必须筛选】获取商品后，必须严格按用户指定的品牌/条件筛选。例如 message 为"筛选小米和华为品牌"时，只返回品牌为小米或华为的商品，丢弃不匹配的。不要返回全部商品
            3. 如果历史数据不满足需求，使用 retrieveSimilarProducts 或 recommendProducts 搜索

            可用工具：
            - getProductsByIds：根据商品ID列表批量获取商品详情，最高效
            - retrieveSimilarProducts：向量语义搜索，根据名称/品牌/类目召回商品
            - recommendProducts：多路召回推荐引擎，作为兜底方案
            - getProductInfo：查询单个商品的详细信息

            请根据上下文信息选择最合适的工具获取商品，然后将结果按以下JSON格式输出：
            {"products":[{"productId":"P001","productName":"iPhone 16","brand":"Apple","price":7999}]}
            """;

    // ==================== 构造与�ization ====================

    public ProductQueryIntentAgent() {
        super("product_query_intent", 8.0, 2);
    }

    @Override
    public com.ecommerce.model.AgentCard getAgentCard() {
        return com.ecommerce.model.AgentCard.builder()
                .name("product_query_intent")
                .description("查询商品信息，支持关键词搜索、ID 批量查询、向量语义召回")
                .capabilities(List.of(
                        com.ecommerce.model.AgentCapability.builder()
                                .id("search_by_keyword")
                                .name("关键词搜索")
                                .description("【构建块】根据关键词搜索商品，支持品牌、类目等筛选。通常作为 compare 的前置步骤，为对比提供商品列表")
                                .tags(List.of("product", "search"))
                                .build(),
                        com.ecommerce.model.AgentCapability.builder()
                                .id("get_by_ids")
                                .name("ID 批量查询")
                                .description("【构建块】根据商品 ID 列表批量获取商品详情。适用场景：用户按位置引用（如\"第1个和最后一个\"）时，编排层解析出具体 ID 后传入")
                                .tags(List.of("product", "query"))
                                .build()
                ))
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "搜索关键词或商品名称"),
                                "product_ids", Map.of("type", "array", "description", "商品 ID 列表")
                        ),
                        "required", List.of()
                ))
                .outputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "products", Map.of("type", "array", "description", "商品列表"),
                                "reply", Map.of("type", "string", "description", "回复文本")
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
        return SYSTEM_PROMPT;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildUserMessage(Map<String, Object> params) {
        return buildUserMessageWithContext(params);
    }

    /**
     * 重写 execute() 以支持 Tool Calling + 降级逻辑
     */
    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");
        Map<String, Object> contextData = (Map<String, Object>) params.get("contextData");

        log.info("ProductQueryIntentAgent.execute - 商品查询, message: {}, contextData: {}",
                message, contextData != null ? contextData.keySet() : "null");

        // 步骤2: 构建包含历史数据的用户消息
        String userMessage = buildUserMessageWithContext(params);

        // 步骤3: 尝试使用 LLM + Tools
        List<Product> products = null;
        try {
            products = tryToolCalling(userMessage);
        } catch (Exception e) {
            log.warn("ProductQueryIntentAgent.execute - Tool Calling 失败，降级为硬编码逻辑", e);
        }

        // 步骤4: 如果 Tool Calling 未返回结果，使用降级逻辑
        if (CollectionUtils.isEmpty(products)) {
            log.info("ProductQueryIntentAgent.execute - 使用降级逻辑");
            products = fallbackSearch(message, history, summary, entities);
        }

        // 步骤5: 生成回复
        String reply = generateProductQueryReply(products, message);

        // 步骤6: 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("reply", reply);
        data.put("products", products);

        log.info("ProductQueryIntentAgent.execute - 查询完成, 找到{}条商品", products.size());
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.9).build();
    }

    @Override
    protected AgentResult buildResult(String llmResponse, Map<String, Object> params) {
        // 默认实现（由 execute() 覆盖时不会调用至此）
        return AgentResult.builder().agentName(name).success(true)
                .data(Map.of("reply", llmResponse)).confidence(1.0).build();
    }

    // ==================== 私有方法 ====================

    /**
     * 构建包含历史数据上下文的用户消息
     */
    @SuppressWarnings("unchecked")
    private String buildUserMessageWithContext(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();

        String message = (String) params.get("message");
        String query = (String) params.get("query");
        Object productIdsObj = params.get("product_ids");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");
        Map<String, Object> contextData = (Map<String, Object>) params.get("contextData");

        // 步骤1: 任务指令（A2A LLM 生成的精确 query，优先级最高）
        if (query != null && !query.isBlank()) {
            sb.append("【任务指令】").append(query).append("\n\n");
        }

        // 步骤1b: 指定的商品 ID 列表（A2A 编排层解析出的精确 ID）
        if (productIdsObj instanceof List && !((List<?>) productIdsObj).isEmpty()) {
            sb.append("【指定商品ID】").append(productIdsObj).append("\n");
            sb.append("请直接使用 getProductsByIds 工具获取这些商品，不要搜索。\n\n");
        }

        // 步骤2: 用户原始消息（完整上下文）
        sb.append("用户原始问题：").append(message).append("\n\n");

        // 步骤3: 历史数据上下文
        if (contextData != null && !contextData.isEmpty()) {
            sb.append("【历史可用数据】\n");
            for (Map.Entry<String, Object> entry : contextData.entrySet()) {
                sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
            }
            sb.append("\n");
        }

        // 步骤4: 实体信息
        if (entities != null && !entities.isEmpty()) {
            sb.append("【实体信息】\n");
            for (Map.Entry<String, Object> entry : entities.entrySet()) {
                sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
            }
        }

        sb.append("\n请根据以上信息，选择合适的工具获取商品。");
        sb.append("如果【任务指令】要求筛选特定品牌，获取商品后必须严格按品牌筛选，丢弃不匹配的。");
        sb.append("如果有历史商品ID，优先使用 getProductsByIds。");
        return sb.toString();
    }

    /**
     * 尝试使用 LLM + Tools 获取商品
     */
    private List<Product> tryToolCalling(String userMessage) {
        log.info("ProductQueryIntentAgent.tryToolCalling - 开始 Tool Calling");

        RecResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .tools(getTools().toArray())
                .user(userMessage)
                .call()
                .entity(RecResult.class);

        if (result != null && !CollectionUtils.isEmpty(result.getProducts())) {
            log.info("ProductQueryIntentAgent.tryToolCalling - Tool Calling 成功, 商品数: {}",
                    result.getProducts().size());
            return result.getProducts();
        }

        log.warn("ProductQueryIntentAgent.tryToolCalling - Tool Calling 返回空结果");
        return Collections.emptyList();
    }

    /**
     * 降级逻辑：使用原有的向量召回 + 关键词搜索
     */
    @SuppressWarnings("unchecked")
    private List<Product> fallbackSearch(String message, List<String> history,
                                          String summary, Map<String, Object> entities) {
        log.info("ProductQueryIntentAgent.fallbackSearch - 开始降级搜索, message: {}", message);

        // 步骤1: 提取查询关键词
        String productName = entities.get("product_name") instanceof String
                ? (String) entities.get("product_name") : message;

        // 步骤2: Query 改写
        String rewrittenQuery = rewriteQuery(productName, history, summary);

        // 步骤3: 向量召回
        List<Product> vectorProducts = recommendEngineService.vectorRecall(rewrittenQuery, null, 3);

        // 步骤4: 数据库关键词搜索
        List<Product> dbProducts = productService.searchByKeyword(productName, 3);

        // 步骤5: 合并去重
        Set<String> seenIds = new HashSet<>();
        List<Product> mergedProducts = new ArrayList<>();
        for (Product p : vectorProducts) {
            if (p != null && StringUtils.isNotBlank(p.getProductId()) && !seenIds.contains(p.getProductId())) {
                seenIds.add(p.getProductId());
                mergedProducts.add(p);
            }
        }
        for (Product p : dbProducts) {
            if (p != null && StringUtils.isNotBlank(p.getProductId()) && !seenIds.contains(p.getProductId())) {
                seenIds.add(p.getProductId());
                mergedProducts.add(p);
            }
        }

        log.info("ProductQueryIntentAgent.fallbackSearch - 降级搜索完成, 商品数: {}", mergedProducts.size());
        return mergedProducts;
    }

    /**
     * Query 改写
     */
    private String rewriteQuery(String query, List<String> history, String summary) {
        try {
            String rewritten = queryRewriteService.rewriteWithContext(query, history, summary);
            if (StringUtils.isNotBlank(rewritten)) {
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("ProductQueryIntentAgent.rewriteQuery - 改写失败", e);
        }
        return query;
    }

    /**
     * 生成商品查询回复
     */
    private String generateProductQueryReply(List<Product> products, String query) {
        // 步骤1: 处理空列表
        if (CollectionUtils.isEmpty(products)) {
            return "抱歉，没有找到与\"" + query + "\"相关的商品。";
        }

        // 步骤2: 展示商品列表
        if (products.size() == 1) {
            Product p = products.get(0);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("为您找到 %s：\n", p.getProductName()));
            sb.append(String.format("- 价格：¥%.0f\n", p.getPrice() != null ? p.getPrice().doubleValue() : 0));
            sb.append(String.format("- 品牌：%s\n", p.getBrand()));
            sb.append(String.format("- 类目：%s\n", p.getCategoryName()));
            sb.append(String.format("- 评分：%.1f/5\n", p.getRating() != null ? p.getRating().doubleValue() : 0));
            if (StringUtils.isNotBlank(p.getProductDescription())) {
                sb.append(String.format("- 描述：%s\n", p.getProductDescription()));
            }
            return sb.toString();
        }

        // 步骤3: 多个商品时，列出简要信息
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("为您找到 %d 个相关商品：\n\n", products.size()));
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append(String.format("%d. %s（%s）¥%.0f\n",
                    i + 1, p.getProductName(), p.getBrand(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0));
        }
        return sb.toString();
    }
}
