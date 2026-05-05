package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendEngineService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 商品推荐Agent — 多策略召回 + LLM重排 + 多样性控制
 * 接入真实数据库和向量搜索
 */
@Component
public class ProductRecAgent extends BaseAgent {

    @Resource
    private RecommendEngineService recommendEngineService;
    @Resource
    private ProductService productService;

    public ProductRecAgent() {
        super("product_rec", 8.0, 2);
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        UserProfile profile = (UserProfile) params.get("userProfile");
        int numItems = (int) params.getOrDefault("numItems", 10);
        String userId = (String) params.getOrDefault("userId", "anonymous");

        log.info("ProductRecAgent.execute 用户={} 请求推荐 {} 条", userId, numItems);

        // 使用推荐引擎进行召回+精排+多样性控制
        List<Product> finalProducts = recommendEngineService.recommend(userId, profile, numItems, params);

        // 兜底：如果推荐引擎返回空，使用热门商品
        if (finalProducts.isEmpty()) {
            log.warn("ProductRecAgent.execute 推荐引擎返回空，使用热门商品兜底");
            finalProducts = productService.listHotProducts(numItems);
        }

        // 直接返回 entity.Product 列表
        Map<String, Object> data = new HashMap<>();
        data.put("products", finalProducts);
        data.put("recall_strategy", "vector+hot+new+category");
        data.put("candidate_count", finalProducts.size());
        data.put("engine_used", "RecommendEngineService");

        log.info("ProductRecAgent.execute 推荐完成，返回 {} 条商品", finalProducts.size());

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.85)
                .build();
    }
}
