package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.ProductNamesResult;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 商品对比意图Agent
 * 提取待对比商品、搜索详情、LLM对比分析
 * 
 * 商品获取优先级：
 * 1. 从意图识别的entities中获取product_ids（用户明确指定或LLM从序号转换）
 * 2. 从会话的extractedInfo中获取recommended_product_ids，结合indices获取
 * 3. 从意图识别的entities中获取product_names
 */
@Component
public class CompareIntentAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private ProductService productService;

    @Resource
    private MemoryService memoryService;

    @Resource
    private ConversationSessionService conversationSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompareIntentAgent() {
        super("compare_intent", 10.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        String sessionId = (String) params.get("sessionId");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");

        log.info("CompareIntentAgent.execute - 商品对比, message: {}, sessionId: {}", message, sessionId);

        // 步骤2: 提取商品ID列表
        List<String> productIds = extractProductIds(sessionId, entities);

        // 步骤3: 校验是否找到足够商品
        if (productIds.size() < 2) {
            String reply = "请告诉我您想对比哪几款商品，比如\"iPhone 16 和 华为 Mate 70 哪个好？\"或者\"比较第1个和第2个\"";
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("reply", reply)).confidence(1.0).build();
        }

        // 步骤4: 根据productId查询商品详情
        List<Product> products = productService.listByProductIds(productIds);

        if (products.size() < 2) {
            String reply = "抱歉，部分商品信息无法找到，请确认商品信息。";
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

    /**
     * 提取商品ID列表
     * 优先级：product_ids > recommended_product_ids + indices > product_names
     */
    @SuppressWarnings("unchecked")
    private List<String> extractProductIds(String sessionId, Map<String, Object> entities) {
        List<String> productIds = new ArrayList<>();
        
        // 步骤1: 优先从entities中获取product_ids（意图识别可能已经识别出）
        if (entities != null && entities.get("product_ids") instanceof List) {
            productIds = (List<String>) entities.get("product_ids");
            if (!CollectionUtils.isEmpty(productIds)) {
                log.info("CompareIntentAgent.extractProductIds - 从entities.product_ids获取: {}", productIds);
                return productIds;
            }
        }
        
        // 步骤2: 从会话的extractedInfo中获取recommended_product_ids，结合indices获取
        List<String> recommendedProductIds = getRecommendedProductIdsFromSession(sessionId);
        if (!recommendedProductIds.isEmpty()) {
            // 步骤3: 从entities中获取indices（用户说"第1个和第2个"，意图识别提取出序号）
            if (entities != null && entities.get("indices") instanceof List) {
                List<Integer> indices = (List<Integer>) entities.get("indices");
                for (Integer index : indices) {
                    if (index > 0 && index <= recommendedProductIds.size()) {
                        productIds.add(recommendedProductIds.get(index - 1));
                    }
                }
                if (!productIds.isEmpty()) {
                    log.info("CompareIntentAgent.extractProductIds - 从indices获取: indices={}, productIds={}", indices, productIds);
                    return productIds;
                }
            }
            
            // 步骤4: 如果没有indices，检查是否是"比较这几个"等指代性语句（意图识别可能返回all=true）
            if (entities != null && Boolean.TRUE.equals(entities.get("all"))) {
                // 返回所有推荐的商品
                productIds = recommendedProductIds;
                log.info("CompareIntentAgent.extractProductIds - 从all标记获取: {}", productIds);
                return productIds;
            }
        }
        
        // 步骤5: 从entities中获取product_names
        if (entities != null && entities.get("product_names") instanceof List) {
            List<String> productNames = (List<String>) entities.get("product_names");
            if (productNames.size() >= 2) {
                for (String name : productNames) {
                    List<Product> found = productService.searchByKeyword(name, 1);
                    if (!found.isEmpty()) {
                        productIds.add(found.get(0).getProductId());
                    }
                }
                log.info("CompareIntentAgent.extractProductIds - 从product_names获取: {}", productIds);
                return productIds;
            }
        }
        
        return productIds;
    }
    
    /**
     * 从会话的extractedInfo中获取之前推荐的商品ID列表
     */
    @SuppressWarnings("unchecked")
    private List<String> getRecommendedProductIdsFromSession(String sessionId) {
        try {
            var session = conversationSessionService.getBySessionId(sessionId);
            if (session == null || session.getExtractedInfo() == null) {
                return Collections.emptyList();
            }
            
            Map<String, Object> extractedInfo = objectMapper.readValue(session.getExtractedInfo(), Map.class);
            if (extractedInfo.get("recommended_product_ids") instanceof List) {
                return (List<String>) extractedInfo.get("recommended_product_ids");
            }
        } catch (Exception e) {
            log.warn("CompareIntentAgent.getRecommendedProductIdsFromSession - 获取失败", e);
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
