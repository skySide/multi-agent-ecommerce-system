package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.ProductNamesResult;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.ProductService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 商品对比意图Agent
 * 提取待对比商品、搜索详情、LLM对比分析
 */
@Component
public class CompareIntentAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private ProductService productService;

    @Resource
    private MemoryService memoryService;

    public CompareIntentAgent() {
        super("compare_intent", 10.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");

        log.info("CompareIntentAgent.execute - 商品对比, message: {}", message);

        // 步骤2: 提取待对比的商品名称列表
        List<String> productNames = extractProductNames(entities, message);
        if (productNames.size() < 2) {
            String reply = "请告诉我您想对比哪几款商品，比如\"iPhone 16 和 华为 Mate 70 哪个好？\"";
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("reply", reply)).confidence(1.0).build();
        }

        // 步骤3: 搜索每个商品的详细信息（最多3个）
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < Math.min(3, productNames.size()); i++) {
            String name = productNames.get(i);
            List<Product> found = productService.searchByKeyword(name, 1);
            if (!found.isEmpty()) {
                products.add(found.get(0));
            }
        }

        // 步骤4: 校验是否找到足够商品
        if (products.size() < 2) {
            String reply = "抱歉，我只找到了部分商品信息，请确认商品名称是否正确。";
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("reply", reply)).confidence(1.0).build();
        }

        // 步骤5: LLM对比分析
        String historyContext = memoryService.buildHistoryContext(history, summary);
        String comparison = generateLLMComparison(message, products, historyContext);

        // 步骤6: 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("reply", comparison);
        data.put("products", products);

        log.info("CompareIntentAgent.execute - 对比完成, 商品数: {}", products.size());
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.9).build();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractProductNames(Map<String, Object> entities, String message) {
        // 步骤1: 优先使用意图识别的实体
        if (entities.get("product_names") instanceof List) {
            return (List<String>) entities.get("product_names");
        }
        // 步骤2: 使用LLM从消息中提取
        try {
            String prompt = String.format(
                    "从用户消息中提取商品名称。\n用户消息：%s",
                    message
            );
            ProductNamesResult result = chatClient.prompt().user(prompt).call().entity(ProductNamesResult.class);
            if (result != null && result.getProducts() != null) {
                return result.getProducts();
            }
        } catch (Exception e) {
            log.warn("CompareIntentAgent.extractProductNames 失败", e);
        }
        return Collections.emptyList();
    }

    private String generateLLMComparison(String userQuery, List<Product> products, String historyContext) {
        // 步骤1: 构建商品信息
        StringBuilder productInfo = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            productInfo.append(String.format(
                    "\n【商品%d】%s\n" +
                            "- 品牌：%s\n" +
                            "- 价格：¥%.0f\n" +
                            "- 评分：%.1f/5\n" +
                            "- 销量：%d\n" +
                            "- 描述：%s\n",
                    i + 1,
                    p.getProductName(),
                    p.getBrand(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                    p.getRating() != null ? p.getRating().doubleValue() : 0,
                    p.getSalesCount() != null ? p.getSalesCount() : 0,
                    p.getProductDescription() != null ? p.getProductDescription() : "暂无"
            ));
        }

        // 步骤2: LLM对比分析
        String prompt = String.format(
                "你是专业的电商导购助手。用户想对比商品，请根据商品信息给出专业的对比分析。\n\n" +
                        "%s商品信息：%s\n\n用户问题：%s\n\n" +
                        "请从以下维度进行对比：\n" +
                        "1. 价格性价比\n" +
                        "2. 品牌/口碑\n" +
                        "3. 适用人群\n" +
                        "4. 综合推荐\n\n" +
                        "要求：\n" +
                        "- 客观分析，不要偏袒任何一方\n" +
                        "- 给出明确的购买建议\n" +
                        "- 回答简洁友好，300字以内",
                historyContext, productInfo.toString(), userQuery
        );

        return chatClient.prompt().user(prompt).call().content().trim();
    }
}
