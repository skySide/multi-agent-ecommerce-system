package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 商品对比意图Agent
 *
 * 基于 ReAct 模式（单轮 Think→Act→Observe）：
 * Think：分析对比维度 → Act：调用 LLM 生成对比分析 → Observe：返回结果
 *
 * 商品列表来源于编排层传入（Route 时由 ConversationAgent 传入 params，
 * Plan & Execute 时由 AgentOrchestrator 传递上游 RecommendIntentAgent 的结果）。
 * 不再直接注入 RecommendIntentAgent。
 */
@Component
public class CompareIntentAgent extends ReActAgent {

    @Resource
    private ChatClient chatClient;

    public CompareIntentAgent() {
        super("compare_intent", 10.0, 2);
    }

    @Override
    public com.ecommerce.model.AgentCard getAgentCard() {
        return com.ecommerce.model.AgentCard.builder()
                .name("compare_intent")
                .description("对比多个商品，生成对比表格和分析建议")
                .capabilities(List.of(
                        com.ecommerce.model.AgentCapability.builder()
                                .id("compare_products")
                                .name("商品对比")
                                .description("从多个维度对比商品。需要上游提供商品列表（通过 search_by_keyword 或 get_by_ids 获取）")
                                .tags(List.of("compare", "product"))
                                .build()
                ))
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "products", Map.of("type", "array", "description", "待对比的商品列表"),
                                "message", Map.of("type", "string", "description", "用户原始消息")
                        ),
                        "required", List.of()
                ))
                .outputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "reply", Map.of("type", "string", "description", "对比分析结果")
                        )
                ))
                .build();
    }

    // ==================== ReActAgent 抽象方法实现 ====================

    @Override
    protected String buildSystemPrompt(Map<String, Object> params) {
        return "你是专业的电商导购助手，擅长从多个维度对比不同商品，给出客观公正的分析和建议。";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildUserMessage(Map<String, Object> params) {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        // 步骤2: 获取商品列表（编排层传入）
        List<Product> productList = extractProducts(params);

        // 步骤3: 构建提示词
        String historyContext = memoryService.buildHistoryContext(history, summary);
        return buildComparisonPrompt(message, productList, historyContext);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        log.info("CompareIntentAgent.execute - 商品对比, message: {}, sessionId: {}",
                message, params.get("sessionId"));

        // 步骤2: Think → 获取商品列表（编排层传入或从 entities/products 中取）
        List<Product> productList = extractProducts(params);

        if (CollectionUtils.isEmpty(productList) || productList.size() < 2) {
            String reply = "请告诉我您想对比哪几款商品，比如\"iPhone 16 和 华为 Mate 70 哪个好？\"或者\"比较第1个和第2个\"";
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("reply", reply)).confidence(1.0).build();
        }

        // 步骤3: Act → LLM对比分析（单轮 ReAct）
        String historyContext = memoryService.buildHistoryContext(history, summary);
        String comparison = chatClient.prompt()
                .system(buildSystemPrompt(params))
                .user(buildComparisonPrompt(message, productList, historyContext))
                .call()
                .content();

        // 步骤4: Observe → 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("reply", comparison.trim());
        data.put("products", productList);

        log.info("CompareIntentAgent.execute - 对比完成, 商品数: {}", productList.size());
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
     * 从 params 中提取商品列表
     * 来源优先级：products > upstream_recommend 中的 products > 无需商品列表
     */
    @SuppressWarnings("unchecked")
    private List<Product> extractProducts(Map<String, Object> params) {
        // 步骤1: 直接从 params 获取（Route 场景）
        if (params.get("products") instanceof List<?> products) {
            return products.stream()
                    .filter(p -> p instanceof Product)
                    .map(p -> (Product) p)
                    .toList();
        }

        // 步骤2: 从上游 recommend Agent 的结果中获取（Plan & Execute 场景）
        if (params.get("upstream_recommend") instanceof Map<?, ?> upstreamData) {
            Map<String, Object> upstream = (Map<String, Object>) upstreamData;
            if (upstream.get("products") instanceof List<?> products) {
                return products.stream()
                        .filter(p -> p instanceof Product)
                        .map(p -> (Product) p)
                        .toList();
            }
        }

        return Collections.emptyList();
    }

    /**
     * 构建 LLM 对比分析提示词
     */
    private String buildComparisonPrompt(String userQuery, List<Product> products, String historyContext) {
        // 步骤1: 构建商品信息
        StringBuilder productInfo = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            productInfo.append(String.format(
                    "\n【商品%d】%s\n- 品牌：%s\n- 价格：¥%.0f\n- 评分：%.1f/5\n- 销量：%d\n- 描述：%s\n",
                    i + 1, p.getProductName(), p.getBrand(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                    p.getRating() != null ? p.getRating().doubleValue() : 0,
                    p.getSalesCount() != null ? p.getSalesCount() : 0,
                    p.getProductDescription() != null ? p.getProductDescription() : "暂无"
            ));
        }

        // 步骤2: 构建完整提示词
        return String.format(
                "%s商品信息：%s\n\n用户问题：%s\n\n" +
                        "请按以下格式输出：\n\n## 📊 商品对比表格\n\n" +
                        "| 对比项 | 商品1 | 商品2 |\n|--------|-------|-------|\n" +
                        "| 商品名 | xxx | xxx |\n| 品牌 | xxx | xxx |\n" +
                        "| 价格 | ¥xxx | ¥xxx |\n| 评分 | x.x分 | x.x分 |\n" +
                        "| 销量 | xxx件 | xxx件 |\n| 特点 | xxx | xxx |\n\n" +
                        "## 💡 对比分析\n" +
                        "1. **价格性价比**\n2. **品牌/口碑**\n3. **适用人群**\n\n" +
                        "## 🎯 购买建议\n- 注重性价比推荐...\n- 追求品质推荐...\n- 综合推荐...\n\n" +
                        "要求：表格准确简洁、分析客观、建议有针对性、整体500字以内",
                historyContext, productInfo.toString(), userQuery
        );
    }
}
