package com.ecommerce.service.impl;

import com.ecommerce.entity.Product;
import com.ecommerce.model.UserProfile;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendEngineService;
import com.ecommerce.service.VectorStoreService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐引擎核心服务实现
 * 多路召回 + 规则精排 + LLM重排 + 多样性控制
 */
@Slf4j
@Service
public class RecommendEngineServiceImpl implements RecommendEngineService {

    @Resource
    private VectorStoreService vectorStoreService;
    @Resource
    private ProductService productService;
    @Resource
    private ChatClient chatClient;

    // 召回通道权重
    private static final double VECTOR_RECALL_WEIGHT = 0.4;
    private static final double HOT_RECALL_WEIGHT = 0.2;
    private static final double NEW_RECALL_WEIGHT = 0.2;
    private static final double CATEGORY_RECALL_WEIGHT = 0.2;

    // 各通道召回数量系数
    private static final int RECALL_FACTOR = 3;

    @Override
    public Map<String, List<Product>> multiChannelRecall(String userId, UserProfile profile, int numItems, Map<String, Object> context) {
        log.info("RecommendEngineServiceImpl.multiChannelRecall 用户={} 开始多路召回", userId);
        Map<String, List<Product>> result = new HashMap<>();

        int recallNum = numItems * RECALL_FACTOR;

        // 通道1: 向量召回（基于用户画像或行为）
        try {
            List<Product> vectorProducts = vectorRecall(userId, profile, (int) (recallNum * VECTOR_RECALL_WEIGHT));
            result.put("vector", vectorProducts);
            log.info("RecommendEngineServiceImpl.multiChannelRecall 向量召回 {} 条", vectorProducts.size());
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 向量召回失败: {}", e.getMessage());
            result.put("vector", List.of());
        }

        // 通道2: 热门召回
        try {
            List<Product> hotProducts = hotRecall((int) (recallNum * HOT_RECALL_WEIGHT));
            result.put("hot", hotProducts);
            log.info("RecommendEngineServiceImpl.multiChannelRecall 热门召回 {} 条", hotProducts.size());
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 热门召回失败: {}", e.getMessage());
            result.put("hot", List.of());
        }

        // 通道3: 新品召回
        try {
            List<Product> newProducts = newArrivalRecall((int) (recallNum * NEW_RECALL_WEIGHT));
            result.put("new", newProducts);
            log.info("RecommendEngineServiceImpl.multiChannelRecall 新品召回 {} 条", newProducts.size());
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 新品召回失败: {}", e.getMessage());
            result.put("new", List.of());
        }

        // 通道4: 类目偏好召回
        try {
            List<Product> categoryProducts = categoryRecall(profile, (int) (recallNum * CATEGORY_RECALL_WEIGHT));
            result.put("category", categoryProducts);
            log.info("RecommendEngineServiceImpl.multiChannelRecall 类目召回 {} 条", categoryProducts.size());
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 类目召回失败: {}", e.getMessage());
            result.put("category", List.of());
        }

        // 去重合并
        Set<String> seenIds = new HashSet<>();
        List<Product> allProducts = new ArrayList<>();
        for (List<Product> list : result.values()) {
            for (Product p : list) {
                if (p != null && p.getProductId() != null && seenIds.add(p.getProductId())) {
                    allProducts.add(p);
                }
            }
        }

        log.info("RecommendEngineServiceImpl.multiChannelRecall 去重后总候选 {} 条", allProducts.size());
        result.put("merged", allProducts);
        return result;
    }

    @Override
    public List<Product> vectorRecall(String userId, UserProfile profile, int numItems) {
        String query = buildQueryFromProfile(profile);
        List<Document> docs = vectorStoreService.searchSimilarProducts(query, numItems * 2);

        List<String> productIds = docs.stream()
                .map(doc -> {
                    Object id = doc.getMetadata().get("productId");
                    return id != null ? id.toString() : null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(numItems)
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            return List.of();
        }
        return productService.listByProductIds(productIds);
    }

    @Override
    public List<Product> hotRecall(int numItems) {
        return productService.listHotProducts(numItems);
    }

    @Override
    public List<Product> newArrivalRecall(int numItems) {
        return productService.listNewArrivals(numItems);
    }

    @Override
    public List<Product> categoryRecall(UserProfile profile, int numItems) {
        if (profile == null || profile.getPreferredCategories() == null || profile.getPreferredCategories().isEmpty()) {
            log.info("RecommendEngineServiceImpl.categoryRecall 用户无类目偏好，返回热门商品");
            return hotRecall(numItems);
        }

        List<String> categoryNames = profile.getPreferredCategories();
        // 通过类目名称查询商品（简化实现，实际可通过categoryId查询）
        List<Product> products = new ArrayList<>();
        for (String category : categoryNames) {
            List<Product> list = productService.searchByKeyword(category, numItems / categoryNames.size() + 1);
            products.addAll(list);
        }
        return products.stream()
                .distinct()
                .limit(numItems)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> rerank(List<Product> candidates, UserProfile profile, int numItems) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        log.info("RecommendEngineServiceImpl.rerank 候选集 {} 条，开始精排", candidates.size());

        // Step 1: 规则打分
        List<ScoredProduct> scored = candidates.stream()
                .map(p -> new ScoredProduct(p, ruleScore(p, profile)))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());

        // Step 2: 取 top N * 2 给 LLM 重排
        List<Product> topCandidates = scored.stream()
                .limit(Math.min(numItems * 2, scored.size()))
                .map(s -> s.product)
                .collect(Collectors.toList());

        // Step 3: LLM 重排（如果画像可用）
        if (profile != null && chatClient != null) {
            try {
                List<Product> llmRanked = llmRerank(topCandidates, profile, numItems);
                log.info("RecommendEngineServiceImpl.rerank LLM重排完成，返回 {} 条", llmRanked.size());
                return llmRanked;
            } catch (Exception e) {
                log.warn("RecommendEngineServiceImpl.rerank LLM重排失败，使用规则排序: {}", e.getMessage());
            }
        }

        return scored.stream()
                .limit(numItems)
                .map(s -> s.product)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> applyDiversity(List<Product> candidates, int numItems, double lambda) {
        if (candidates == null || candidates.size() <= numItems) {
            return candidates;
        }

        // MMR (Maximal Marginal Relevance) 简化实现
        List<Product> selected = new ArrayList<>();
        Set<String> selectedCategories = new HashSet<>();

        // 第一轮：选相关性最高的
        Product first = candidates.get(0);
        selected.add(first);
        if (first.getCategoryName() != null) {
            selectedCategories.add(first.getCategoryName());
        }

        List<Product> remaining = new ArrayList<>(candidates);
        remaining.remove(first);

        while (selected.size() < numItems && !remaining.isEmpty()) {
            Product best = null;
            double bestScore = -Double.MAX_VALUE;

            for (Product candidate : remaining) {
                double relevance = 1.0 / (selected.size() + 1); // 简化相关性
                double diversity = 1.0;
                if (candidate.getCategoryName() != null && selectedCategories.contains(candidate.getCategoryName())) {
                    diversity = 0.3; // 同类目惩罚
                }
                double mmrScore = lambda * relevance - (1 - lambda) * diversity;
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    best = candidate;
                }
            }

            if (best != null) {
                selected.add(best);
                remaining.remove(best);
                if (best.getCategoryName() != null) {
                    selectedCategories.add(best.getCategoryName());
                }
            } else {
                break;
            }
        }

        log.info("RecommendEngineServiceImpl.applyDiversity MMR调整后返回 {} 条", selected.size());
        return selected;
    }

    @Override
    public List<Product> recommend(String userId, UserProfile profile, int numItems, Map<String, Object> context) {
        log.info("RecommendEngineServiceImpl.recommend 用户={} 请求推荐 {} 条", userId, numItems);

        // 1. 多路召回
        Map<String, List<Product>> recallResult = multiChannelRecall(userId, profile, numItems * 3, context);
        List<Product> candidates = recallResult.getOrDefault("merged", List.of());

        if (candidates.isEmpty()) {
            log.warn("RecommendEngineServiceImpl.recommend 召回为空，返回热门商品兜底");
            candidates = hotRecall(numItems);
        }

        // 2. 精排
        List<Product> ranked = rerank(candidates, profile, numItems * 2);

        // 3. 多样性控制
        List<Product> diverse = applyDiversity(ranked, numItems, 0.5);

        log.info("RecommendEngineServiceImpl.recommend 最终返回 {} 条推荐", diverse.size());
        return diverse;
    }

    // ========== 私有方法 ==========

    private String buildQueryFromProfile(UserProfile profile) {
        if (profile == null) {
            return "热门商品推荐";
        }
        StringBuilder sb = new StringBuilder();
        if (profile.getPreferredCategories() != null && !profile.getPreferredCategories().isEmpty()) {
            sb.append(String.join(" ", profile.getPreferredCategories()));
        }
        if (profile.getRecentViews() != null && !profile.getRecentViews().isEmpty()) {
            sb.append(" ").append(String.join(" ", profile.getRecentViews()));
        }
        if (profile.getRecentPurchases() != null && !profile.getRecentPurchases().isEmpty()) {
            sb.append(" ").append(String.join(" ", profile.getRecentPurchases()));
        }
        return sb.length() > 0 ? sb.toString() : "热门商品推荐";
    }

    private double ruleScore(Product product, UserProfile profile) {
        double score = 0.0;

        // 基础分：销量和评分
        if (product.getSalesCount() != null) {
            score += Math.log(product.getSalesCount() + 1) * 2;
        }
        if (product.getRating() != null) {
            score += product.getRating().doubleValue() * 5;
        }

        if (profile != null) {
            // 类目匹配加分
            if (profile.getPreferredCategories() != null && product.getCategoryName() != null) {
                if (profile.getPreferredCategories().contains(product.getCategoryName())) {
                    score += 20;
                }
            }

            // 价格匹配加分
            if (profile.getPriceRange() != null && product.getPrice() != null) {
                double min = profile.getPriceRange()[0];
                double max = profile.getPriceRange()[1];
                double price = product.getPrice().doubleValue();
                if (price >= min && price <= max) {
                    score += 15;
                } else if (price <= max * 1.2) {
                    score += 5;
                }
            }

            // 品牌匹配加分
            if (profile.getRecentPurchases() != null && product.getBrand() != null) {
                for (String item : profile.getRecentPurchases()) {
                    if (item.contains(product.getBrand()) || product.getBrand().contains(item)) {
                        score += 10;
                    }
                }
            }
        }

        // 库存惩罚
        if (product.getStock() != null && product.getStock() < 10) {
            score -= 10;
        }

        return score;
    }

    private List<Product> llmRerank(List<Product> candidates, UserProfile profile, int numItems) {
        String productList = candidates.stream()
                .map(p -> String.format("%s:%s(%s,¥%.0f,销量%d,评分%.1f)",
                        p.getProductId(), p.getProductName(), p.getCategoryName(),
                        p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                        p.getSalesCount() != null ? p.getSalesCount() : 0,
                        p.getRating() != null ? p.getRating().doubleValue() : 0))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "你是电商推荐专家。根据用户画像为用户选出最匹配的%d个商品。\n" +
                        "用户画像: 偏好类目=%s, 价格范围=%.0f-%.0f, 分群=%s\n" +
                        "候选商品(按规则预排序):\n%s\n" +
                        "请输出商品ID数组（JSON格式），只输出ID数组，不要解释。",
                numItems,
                profile.getPreferredCategories(),
                profile.getPriceRange()[0], profile.getPriceRange()[1],
                profile.getSegments(),
                productList
        );

        String response = chatClient.prompt().user(prompt).call().content();
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
            cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
        }

        // 解析 ID 列表
        List<String> rankedIds = parseIdList(cleaned);
        Map<String, Product> idMap = candidates.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        List<Product> result = new ArrayList<>();
        for (String id : rankedIds) {
            Product p = idMap.get(id);
            if (p != null) {
                result.add(p);
            }
        }

        // 补全
        for (Product p : candidates) {
            if (result.size() >= numItems) break;
            if (!rankedIds.contains(p.getProductId())) {
                result.add(p);
            }
        }

        return result;
    }

    private List<String> parseIdList(String raw) {
        List<String> result = new ArrayList<>();
        try {
            // 尝试解析 JSON 数组
            raw = raw.trim();
            if (raw.startsWith("[") && raw.endsWith("]")) {
                String content = raw.substring(1, raw.length() - 1);
                for (String part : content.split(",")) {
                    String id = part.trim().replace("\"", "").replace("'", "");
                    if (!id.isEmpty()) {
                        result.add(id);
                    }
                }
            } else {
                // 逐行解析
                for (String line : raw.split("\n")) {
                    String id = line.trim().replace("\"", "").replace("'", "").replace(",", "");
                    if (!id.isEmpty()) {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.parseIdList 解析ID列表失败: {}", e.getMessage());
        }
        return result;
    }

    // 内部类：带分数的商品
    private static class ScoredProduct {
        final Product product;
        final double score;

        ScoredProduct(Product product, double score) {
            this.product = product;
            this.score = score;
        }
    }
}
