package com.ecommerce.orchestrator;

import com.ecommerce.agent.*;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.RecommendationResponse;
import com.ecommerce.service.ABTestService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Supervisor编排器 — 串行线性流程
 *
 *  ① UserProfileAgent → 获取/构建用户画像
 *  ② ProductRecAgent  → 基于画像推荐商品
 *  ③ InventoryAgent   → 检查推荐商品的库存
 *  返回最终结果
 */
@Service
public class SupervisorOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SupervisorOrchestrator.class);

    @Resource
    private UserProfileAgent userProfileAgent;
    @Resource
    private ProductRecAgent productRecAgent;
    @Resource
    private InventoryAgent inventoryAgent;
    @Resource
    private ABTestService abTestService;

    public RecommendationResponse recommend(RecommendationRequest request) {
        long start = System.nanoTime();
        Map<String, AgentResult> agentResults = new HashMap<>();

        // 步骤1: 获取用户画像
        UserProfile profile = getUserProfile(request.getUserId(), agentResults);

        // 步骤2: 基于画像推荐商品
        List<Product> products = recommendProducts(request.getUserId(), profile, request.getNumItems(), agentResults);

        // 步骤3: 检查库存，过滤无货商品
        List<Product> finalProducts = checkInventory(products, request.getNumItems(), agentResults);

        double totalLatencyMs = (System.nanoTime() - start) / 1_000_000.0;
        return buildResponse(request, finalProducts, agentResults, totalLatencyMs);
    }

    /**
     * 获取用户画像，失败时返回默认画像
     */
    private UserProfile getUserProfile(String userId, Map<String, AgentResult> agentResults) {
        log.info("SupervisorOrchestrator.getUserProfile begin, userId: {}", userId);
        AgentResult result = userProfileAgent.runAsync(Map.of("userId", userId)).join();
        agentResults.put("user_profile", result);

        UserProfile profile = null;
        if (Objects.nonNull(result) && Objects.nonNull(result.getData())) {
            profile = (UserProfile) result.getData().get("profile");
        }

        if (!result.isSuccess() || Objects.isNull(profile)) {
            log.warn("SupervisorOrchestrator.getUserProfile warn, profile获取失败，降级使用默认画像");
            profile = UserProfile.builder().userId(userId).segments("active").build();
        }

        log.info("SupervisorOrchestrator.getUserProfile end, profile = {}", profile);
        return profile;
    }

    /**
     * 基于用户画像推荐商品
     */
    private List<Product> recommendProducts(String userId, UserProfile profile, int numItems,
                                             Map<String, AgentResult> agentResults) {
        log.info("SupervisorOrchestrator.recommendProducts - 推荐商品, userId: {}, numItems: {}", userId, numItems);
        AgentResult result = productRecAgent.runAsync(Map.of(
                "userId", userId,
                "userProfile", profile,
                "numItems", numItems
        )).join();
        agentResults.put("product_rec", result);

        @SuppressWarnings("unchecked")
        List<Product> products = result.getData() != null
                ? (List<Product>) result.getData().get("products") : Collections.emptyList();

        if (CollectionUtils.isEmpty(products)) {
            log.warn("SupervisorOrchestrator.recommendProducts - 推荐商品为空");
            return Collections.emptyList();
        }

        log.info("SupervisorOrchestrator.recommendProducts - 推荐完成, 共{}条商品", products.size());
        return products;
    }

    /**
     * 检查商品库存，过滤无货商品
     */
    private List<Product> checkInventory(List<Product> products, int numItems,
                                           Map<String, AgentResult> agentResults) {
        if (CollectionUtils.isEmpty(products)) {
            return Collections.emptyList();
        }

        log.info("SupervisorOrchestrator.checkInventory - 库存检查, 共{}条商品", products.size());
        List<String> productIds = products.stream()
                .map(Product::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        AgentResult result = inventoryAgent.runAsync(Map.of("productIds", productIds)).join();
        agentResults.put("inventory", result);

        @SuppressWarnings("unchecked")
        List<String> availableIds = result.getData() != null
                ? (List<String>) result.getData().get("available_products") : Collections.emptyList();

        Set<String> availSet = new HashSet<>(availableIds);
        List<Product> finalProducts = products.stream()
                .filter(p -> availSet.contains(p.getProductId()))
                .limit(numItems)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(finalProducts)) {
            log.warn("SupervisorOrchestrator.checkInventory - 库存过滤后无可用商品，使用原始推荐结果");
            return products.stream().limit(numItems).collect(Collectors.toList());
        }

        log.info("SupervisorOrchestrator.checkInventory - 库存检查完成, 可售: {}条", finalProducts.size());
        return finalProducts;
    }

    /**
     * 构建推荐响应
     */
    private RecommendationResponse buildResponse(RecommendationRequest request, List<Product> products,
                                                  Map<String, AgentResult> agentResults, double totalLatencyMs) {
        String requestId = UUID.randomUUID().toString();
        String experimentGroup = abTestService.assign(request.getUserId()).getOrDefault("group", "control").toString();

        log.info("SupervisorOrchestrator.buildResponse - requestId: {}, products: {}", requestId, products.size());

        return RecommendationResponse.builder()
                .requestId(requestId)
                .userId(request.getUserId())
                .products(products)
                .experimentGroup(experimentGroup)
                .agentResults(agentResults)
                .totalLatencyMs(totalLatencyMs)
                .build();
    }
}
