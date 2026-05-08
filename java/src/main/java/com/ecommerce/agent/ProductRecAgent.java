package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.RecResult;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendEngineService;
import com.ecommerce.tool.ProductSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 商品推荐Agent
 * 多策略召回 + LLM重排 + 多样性控制
 * LLM通过ProductSearchTool的工具获取商品信息后，只需输出商品ID列表，
 * 由Agent回表数据库获取完整商品数据，避免LLM序列化复杂字段导致的JSON解析异常
 */
@Component
public class ProductRecAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private ProductSearchTool productSearchTool;

    @Resource
    private RecommendEngineService recommendEngineService;

    @Resource
    private ProductService productService;

    public ProductRecAgent() {
        super("product_rec", 8.0, 2);
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        UserProfile profile = (UserProfile) params.get("userProfile");
        int numItems = (int) params.getOrDefault("numItems", 10);
        String userId = (String) params.getOrDefault("userId", "anonymous");

        log.info("ProductRecAgent.execute - 请求推荐, 用户: {}, 数量: {}", userId, numItems);

        // 步骤2: 提取用户查询
        String userQuery = "";
        if (params.get("userQuery") instanceof String) {
            userQuery = (String) params.get("userQuery");
        }

        // 步骤3: 构建LLM提示词
        String systemPrompt = "你是一个资深的电商商品推荐专家。请根据用户的查询需求选择合适的商品检索工具。" +
                "工具已提供详细的参数说明，请按说明使用。" +
                "\n工具返回完整商品信息（Product），请参考这些信息进行推荐决策。" +
                "\n请将推荐结果按以下JSON格式输出。" +
                "\n\n输出JSON格式示例：" +
                "\n{\"products\":[{\"productId\":\"P001\",\"productName\":\"iPhone 16\",\"brand\":\"Apple\",\"price\":7999},{\"productId\":\"P002\",\"productName\":\"华为Mate 70\",\"brand\":\"华为\",\"price\":6999}]}";

        String userMessage = String.format(
                "用户ID: %s\n用户画像: %s\n查询需求: %s\n需要推荐数量: %d\n请选择合适的工具获取推荐结果。",
                userId,
                profile != null ? profileToString(profile) : "无",
                userQuery.isEmpty() ? "根据画像推荐" : userQuery,
                numItems
        );

        // 步骤4: 调用LLM决策并执行工具调用，工具返回的List<Product>直接映射到RecResult
        RecResult result = chatClient.prompt()
                .system(systemPrompt)
                .tools(productSearchTool)
                .user(userMessage)
                .call()
                .entity(RecResult.class);

        // 步骤5: 使用LLM返回的推荐结果
        List<Product> finalProducts;
        if (Objects.nonNull(result) && !CollectionUtils.isEmpty(result.getProducts())) {
            finalProducts = result.getProducts();
            log.info("ProductRecAgent.execute - LLM推荐完成, 商品数: {}", finalProducts.size());
        } else {
            log.warn("ProductRecAgent.execute - LLM未返回推荐结果，使用引擎兜底");
            finalProducts = fallbackRecommend(userId, profile, numItems, params);
        }

        // 步骤6: 返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("products", finalProducts);
        data.put("candidate_count", finalProducts.size());

        log.info("ProductRecAgent.execute - 推荐完成, 用户: {}, 返回: {} 条", userId, finalProducts.size());

        return AgentResult.builder()
                .agentName(name).success(true).data(data).confidence(0.85)
                .build();
    }

    /**
     * 兜底推荐逻辑
     */
    private List<Product> fallbackRecommend(String userId, UserProfile profile, int numItems, Map<String, Object> params) {
        log.warn("ProductRecAgent.fallbackRecommend - 使用推荐引擎兜底");
        List<Product> products = recommendEngineService.recommend(userId, profile, numItems, params);

        if (CollectionUtils.isEmpty(products)) {
            log.warn("ProductRecAgent.fallbackRecommend - 推荐引擎返回空，使用热门商品");
            products = productService.listHotProducts(numItems);
        }
        return products;
    }

    /**
     * 用户画像转字符串
     */
    private String profileToString(UserProfile profile) {
        if (Objects.isNull(profile)) {
            return "无";
        }
        return String.format("分群=%s, 类目=%s, 品牌=%s, 价格=%.0f-%.0f",
                profile.getSegments(), profile.getPreferredCategories(),
                profile.getPreferredBrands(),
                profile.getPriceRangeMin() != null ? profile.getPriceRangeMin().doubleValue() : 0.0,
                profile.getPriceRangeMax() != null ? profile.getPriceRangeMax().doubleValue() : 99999.0);
    }
}
