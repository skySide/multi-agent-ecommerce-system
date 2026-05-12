package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.RecommendEngineService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 推荐意图Agent
 * 基于用户消息中的实体信息 + 长期记忆（用户画像），调用推荐引擎获取推荐结果
 */
@Component
public class RecommendIntentAgent extends BaseAgent {

    @Resource
    private RecommendEngineService recommendEngineService;

    @Resource
    private MemoryService memoryService;

    public RecommendIntentAgent() {
        super("recommend_intent", 10.0, 2);
    }

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

        log.info("RecommendIntentAgent.execute - 对话式推荐, userId: {}, entities: {}", userId, entities);

        // 步骤2: 合并跨轮实体 + 构建用户画像
        Map<String, Object> mergedEntities = memoryService.mergeWithSessionMemory(sessionId, entities);
        UserProfile profile = memoryService.buildProfileFromEntities(userId, mergedEntities);
        String longTermContext = memoryService.buildLongTermContext(userId);

        // 步骤3: 构建推荐上下文
        Map<String, Object> context = new HashMap<>(mergedEntities);
        context.put("userQuery", message);
        context.put("history", history);
        context.put("summary", summary);

        // 步骤4: 调用推荐引擎
        int numItems = mergedEntities.get("num_items") instanceof Number
                ? ((Number) mergedEntities.get("num_items")).intValue() : 6;
        List<Product> products = recommendEngineService.recommend(userId, profile, numItems, context);

        // 步骤5: 生成推荐回复
        String reply = generateRecommendReply(products);

        // 步骤6: 将推荐商品ID保存到entities中，供后续对比使用
        Map<String, Object> data = new HashMap<>();
        data.put("reply", reply);
        data.put("products", products);
        
        // 步骤7: 保存推荐商品ID到entities（关键：供后续对比使用）
        Map<String, Object> enrichedEntities = new HashMap<>(entities != null ? entities : new HashMap<>());
        enrichedEntities.put("recommended_product_ids", products.stream()
                .map(Product::getProductId)
                .collect(java.util.stream.Collectors.toList()));
        data.put("entities", enrichedEntities);

        log.info("RecommendIntentAgent.execute - 推荐完成, userId: {}, 商品数: {}, 已保存ID到entities", userId, products.size());
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.9).build();
    }

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
}
