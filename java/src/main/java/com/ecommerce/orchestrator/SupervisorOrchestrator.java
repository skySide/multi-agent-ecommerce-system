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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Supervisor编排器 — 并行分发 + 聚合模式 (Java CompletableFuture实现)
 *
 *        ┌──────────────┐
 *        │  Supervisor   │
 *        └──────┬───────┘
 *   ┌─────┬─────┼─────┬──────┐
 *   ▼     ▼     ▼     ▼      │
 * Profile Rec  Copy Inventory │
 *   └─────┴─────┴─────┘      │
 *          ▼                  │
 *      Aggregator ◄───────────┘
 */
@Service
public class SupervisorOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SupervisorOrchestrator.class);

    @Resource
    private UserProfileAgent userProfileAgent;
    @Resource
    private ProductRecAgent productRecAgent;
    @Resource
    private MarketingCopyAgent marketingCopyAgent;
    @Resource
    private InventoryAgent inventoryAgent;
    @Resource
    private ABTestService abTestService;

    public RecommendationResponse recommend(RecommendationRequest request) {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        Map<String, AgentResult> agentResults = new HashMap<>();

        log.info("SupervisorOrchestrator.recommend start request={} user={}", requestId, request.getUserId());

        String experimentGroup = abTestService.assign(request.getUserId()).getOrDefault("group", "control").toString();

        // Phase 1: parallel — user profile + product recall
        CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(
                Map.of("userId", request.getUserId()));
        CompletableFuture<AgentResult> recFuture = productRecAgent.runAsync(
                Map.of("userId", request.getUserId(), "numItems", request.getNumItems()));

        AgentResult profileResult = profileFuture.join();
        AgentResult recResult = recFuture.join();
        agentResults.put("user_profile", profileResult);
        agentResults.put("product_recall", recResult);

        UserProfile profile = profileResult.getData() != null
                ? (UserProfile) profileResult.getData().get("profile") : null;
        @SuppressWarnings("unchecked")
        List<Product> rawProducts = recResult.getData() != null
                ? (List<Product>) recResult.getData().get("products") : List.of();

        log.info("SupervisorOrchestrator.recommend Phase1完成 画像={} 召回{}条商品",
                profile != null ? profile.getSegments() : "null", rawProducts.size());

        // Phase 2: rerank + inventory check
        // 若画像缺失或信息不足，复用 Phase1 结果，避免重复检索。
        CompletableFuture<AgentResult> rerankFuture;
        if (hasUsableProfile(profile)) {
            rerankFuture = productRecAgent.runAsync(
                    Map.of("userId", request.getUserId(),
                            "userProfile", profile,
                            "numItems", request.getNumItems()));
        } else {
            rerankFuture = CompletableFuture.completedFuture(
                    AgentResult.builder()
                            .agentName("product_rec")
                            .success(true)
                            .data(Map.of(
                                    "products", rawProducts.stream().limit(request.getNumItems()).collect(Collectors.toList()),
                                    "rerank_skipped", true,
                                    "reason", "profile_missing_or_not_informative"
                            ))
                            .confidence(0.8)
                            .build()
            );
        }

        // 提取商品ID传给库存Agent
        List<String> productIds = rawProducts.stream()
                .map(Product::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        CompletableFuture<AgentResult> inventoryFuture = inventoryAgent.runAsync(
                Map.of("productIds", productIds));

        AgentResult rerankResult = rerankFuture.join();
        AgentResult inventoryResult = inventoryFuture.join();
        agentResults.put("rerank", rerankResult);
        agentResults.put("inventory", inventoryResult);

        @SuppressWarnings("unchecked")
        List<Product> rankedProducts = rerankResult.getData() != null
                ? (List<Product>) rerankResult.getData().get("products") : rawProducts;
        @SuppressWarnings("unchecked")
        List<String> availableIds = inventoryResult.getData() != null
                ? (List<String>) inventoryResult.getData().get("available_products") : List.of();

        log.info("SupervisorOrchestrator.recommend Phase2完成 精排{}条 可售{}条",
                rankedProducts.size(), availableIds.size());

        Set<String> availSet = new HashSet<>(availableIds);
        List<Product> finalProducts = rankedProducts.stream()
                .filter(p -> availSet.contains(p.getProductId()))
                .limit(request.getNumItems())
                .collect(Collectors.toList());
        if (finalProducts.isEmpty()) {
            log.warn("SupervisorOrchestrator.recommend 库存过滤后为空，使用精排结果兜底");
            finalProducts = rankedProducts.stream().limit(request.getNumItems()).collect(Collectors.toList());
        }

        // Phase 3: marketing copy
        List<String> finalProductIds = finalProducts.stream()
                .map(Product::getProductId)
                .collect(Collectors.toList());
        AgentResult copyResult = marketingCopyAgent.runAsync(
                        Map.of("userProfile", profile != null ? profile : new UserProfile(),
                                "productIds", finalProductIds))
                .join();
        agentResults.put("marketing_copy", copyResult);

        @SuppressWarnings("unchecked")
        List<com.ecommerce.vo.MarketingCopyVO> copies = copyResult.getData() != null
                ? (List<com.ecommerce.vo.MarketingCopyVO>) copyResult.getData().get("copies") : List.of();

        double totalLatency = (System.nanoTime() - start) / 1_000_000.0;
        log.info("SupervisorOrchestrator.recommend complete request={} latency={:.1f}ms products={} experiment={}",
                requestId, totalLatency, finalProducts.size(), experimentGroup);

        return RecommendationResponse.builder()
                .requestId(requestId)
                .userId(request.getUserId())
                .products(finalProducts)
                .marketingCopies(copies)
                .experimentGroup(experimentGroup)
                .agentResults(agentResults)
                .totalLatencyMs(totalLatency)
                .build();
    }

    private boolean hasUsableProfile(UserProfile profile) {
        if (profile == null) {
            return false;
        }
        boolean hasSegments = profile.getSegments() != null && !profile.getSegments().isEmpty();
        boolean hasCategoryPreference = profile.getPreferredCategories() != null
                && !profile.getPreferredCategories().isEmpty();
        boolean hasPriceRange = profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null;
        return hasSegments || hasCategoryPreference || hasPriceRange;
    }
}
