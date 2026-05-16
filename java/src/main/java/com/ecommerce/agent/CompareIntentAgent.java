package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.MemoryService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 商品对比意图Agent
 * 仅负责 LLM 对比分析。所有商品定位（不论按ID、按序号解析、按名称/品牌搜索）
 * 全部委托给 {@link RecommendIntentAgent#resolveProductsForComparison} 完成。
 */
@Component
public class CompareIntentAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private MemoryService memoryService;

    @Resource
    private RecommendIntentAgent recommendIntentAgent;

    public CompareIntentAgent() {
        super("compare_intent", 10.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        String userId = (String) params.get("userId");
        String sessionId = (String) params.get("sessionId");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");

        log.info("CompareIntentAgent.execute - 商品对比, message: {}, sessionId: {}", message, sessionId);

        // 步骤2 获取productList
        // 全部商品定位委托 RecommendIntentAgent（withoutUserProfile）
        // entities 直接透传 — product_ids / indices / all / product_names / brand / category
        // RecommendIntentAgent 会合并会话上下文后自主决定获取策略
        List<Product> productList = recommendIntentAgent.resolveProductsForComparison(
                message, userId, sessionId, history, summary, entities);

        if (productList.size() < 2) {
            String reply = "请告诉我您想对比哪几款商品，比如\"iPhone 16 和 华为 Mate 70 哪个好？\"或者\"比较第1个和第2个\"";
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("reply", reply)).confidence(1.0).build();
        }

        // 步骤3: LLM对比分析
        String historyContext = memoryService.buildHistoryContext(history, summary);
        String comparison = generateLLMComparison(message, productList, historyContext);

        // 步骤4: 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("reply", comparison);
        data.put("products", productList);

        log.info("CompareIntentAgent.execute - 对比完成, 商品数: {}", productList.size());
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.9).build();
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

        // 步骤2: LLM对比分析，使用Markdown表格格式
        String prompt = String.format(
                "你是专业的电商导购助手。用户想对比商品，请根据商品信息给出专业的对比分析。\n\n" +
                        "%s商品信息：%s\n\n用户问题：%s\n\n" +
                        "请按以下格式输出：\n\n" +
                        "## 📊 商品对比表格\n\n" +
                        "| 对比项 | 商品1 | 商品2 | ... |\n" +
                        "|--------|-------|-------|-----|\n" +
                        "| 商品名 | xxx | xxx | |\n" +
                        "| 品牌 | xxx | xxx | |\n" +
                        "| 价格 | ¥xxx | ¥xxx | |\n" +
                        "| 评分 | x.x分 | x.x分 | |\n" +
                        "| 销量 | xxx件 | xxx件 | |\n" +
                        "| 特点 | xxx | xxx | |\n\n" +
                        "## 💡 对比分析\n\n" +
                        "请从以下维度进行简要分析（每个维度2-3句话）：\n" +
                        "1. **价格性价比**：...\n" +
                        "2. **品牌/口碑**：...\n" +
                        "3. **适用人群**：...\n\n" +
                        "## 🎯 购买建议\n\n" +
                        "根据不同需求给出建议：\n" +
                        "- 如果您注重性价比，推荐...\n" +
                        "- 如果您追求品质，推荐...\n" +
                        "- 综合推荐...\n\n" +
                        "要求：\n" +
                        "- 表格内容要准确、简洁\n" +
                        "- 分析要客观公正\n" +
                        "- 建议要有针对性\n" +
                        "- 整体回答控制在500字以内",
                historyContext, productInfo.toString(), userQuery
        );

        return chatClient.prompt().user(prompt).call().content().trim();
    }
}
