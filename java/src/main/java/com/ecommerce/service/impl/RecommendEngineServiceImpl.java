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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐引擎核心服务实现。
 * <p>流程：多路召回 -> RRF融合 -> 重排 -> 多样性控制。</p>
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
    // RRF 融合常量（越大越平滑）
    private static final int RRF_K = 60;

    // 固定通道权重，避免每次调用重复创建对象
    private static final Map<String, Double> CHANNEL_WEIGHTS = Map.of(
            "vector", VECTOR_RECALL_WEIGHT,
            "hot", HOT_RECALL_WEIGHT,
            "new", NEW_RECALL_WEIGHT,
            "category", CATEGORY_RECALL_WEIGHT
    );

    // 固定比较器，避免每次调用重复创建对象
    private static final Comparator<Product> BY_ID =
            Comparator.comparing(p -> p.getProductId() == null ? "" : p.getProductId());
    private static final Comparator<Product> BY_SALES_DESC =
            Comparator.comparingInt((Product p) -> p.getSalesCount() == null ? 0 : p.getSalesCount()).reversed();
    private static final Comparator<Product> BY_RATING_DESC =
            Comparator.comparingDouble((Product p) -> p.getRating() == null ? 0.0 : p.getRating().doubleValue()).reversed();

    /**
     * 多路召回主入口。
     * <p>步骤：四路召回 -> 通道内稳定排序 -> RRF融合。</p>
     */
    @Override
    public Map<String, List<Product>> multiChannelRecall(String userId, UserProfile profile, int numItems, Map<String, Object> context) {
        log.info("RecommendEngineServiceImpl.multiChannelRecall 用户={} 开始多路召回", userId);
        Map<String, List<Product>> result = new HashMap<>();
        int recallNum = numItems * RECALL_FACTOR;

        try {
            List<Product> vectorProducts = vectorRecall(userId, profile, (int) (recallNum * VECTOR_RECALL_WEIGHT));
            result.put("vector", sortForChannel("vector", vectorProducts, profile));
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 向量召回失败: {}", e.getMessage());
            result.put("vector", List.of());
        }

        try {
            List<Product> hotProducts = hotRecall((int) (recallNum * HOT_RECALL_WEIGHT));
            result.put("hot", sortForChannel("hot", hotProducts, profile));
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 热门召回失败: {}", e.getMessage());
            result.put("hot", List.of());
        }

        try {
            List<Product> newProducts = newArrivalRecall((int) (recallNum * NEW_RECALL_WEIGHT));
            result.put("new", sortForChannel("new", newProducts, profile));
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 新品召回失败: {}", e.getMessage());
            result.put("new", List.of());
        }

        try {
            List<Product> categoryProducts = categoryRecall(profile, (int) (recallNum * CATEGORY_RECALL_WEIGHT));
            result.put("category", sortForChannel("category", categoryProducts, profile));
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.multiChannelRecall 类目召回失败: {}", e.getMessage());
            result.put("category", List.of());
        }

        List<Product> merged = mergeByRrf(result, recallNum);
        result.put("merged", merged);
        log.info("RecommendEngineServiceImpl.multiChannelRecall 合并后候选 {} 条", merged.size());
        return result;
    }

    /**
     * 向量召回通道：query构建、metadata过滤、回表并按向量顺序重排。
     * 需要重新排序的目的，是因为查询数据库的数据， 和向量召回的顺序有可能不一样，而向量召回的数据
     * 已经按照相似度进行排序了， 因此需要查询数据库之后，还需要再进行排序
     *
     * 之所以要查询数据库，是因为可能我们数据库变更之后，没有那么快同步到向量数据库中，因此
     * 需要再次查询数据库，从而得到准确的数据，降低幻觉
     */
    @Override
    public List<Product> vectorRecall(String userId, UserProfile profile, int numItems) {
        String query = buildQueryFromProfile(profile);
        Map<String, Object> filters = buildVectorFilters(profile);
        List<Document> docs = filters.isEmpty()
                ? vectorStoreService.searchSimilarProducts(query, numItems * 2)
                : vectorStoreService.searchSimilarProducts(query, numItems * 2, filters);

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
        List<Product> products = productService.listByProductIds(productIds);
        return reorderByIds(products, productIds);
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
            return hotRecall(numItems);
        }
        List<String> categoryNames = profile.getPreferredCategories();
        List<Product> products = new ArrayList<>();
        for (String category : categoryNames) {
            List<Product> list = productService.searchByKeyword(category, numItems / categoryNames.size() + 1);
            products.addAll(list);
        }
        return products.stream().distinct().limit(numItems).collect(Collectors.toList());
    }

    /**
     * 精排流程（简化版）。
     * <p>步骤：先规则排序，再尝试LLM做顺序调整，失败时回退规则排序。</p>
     */
    @Override
    public List<Product> rerank(List<Product> candidates, UserProfile profile, int numItems) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ScoredProduct> scored = candidates.stream()
                .map(p -> new ScoredProduct(p, ruleScore(p, profile)))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
        List<Product> ruleTop = scored.stream()
                .limit(Math.min(numItems * 2, scored.size()))
                .map(ScoredProduct::getProduct)
                .collect(Collectors.toList());

        if (profile != null && chatClient != null) {
            try {
                List<Product> llmRanked = llmRerank(ruleTop, profile, numItems);
                if (!llmRanked.isEmpty()) {
                    return llmRanked;
                }
            } catch (Exception e) {
                log.warn("RecommendEngineServiceImpl.rerank LLM重排失败，回退规则排序: {}", e.getMessage());
            }
        }
        return ruleTop.stream().limit(numItems).collect(Collectors.toList());
    }

    @Override
    public List<Product> applyDiversity(List<Product> candidates, int numItems, double lambda) {
        if (candidates == null || candidates.size() <= numItems) {
            return candidates;
        }
        List<Product> selected = new ArrayList<>();
        Set<String> selectedCategories = new HashSet<>();

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
                double relevance = 1.0 / (selected.size() + 1);
                double diversity = 1.0;
                if (candidate.getCategoryName() != null && selectedCategories.contains(candidate.getCategoryName())) {
                    diversity = 0.3;
                }
                double mmrScore = lambda * relevance - (1 - lambda) * diversity;
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
            remaining.remove(best);
            if (best.getCategoryName() != null) {
                selectedCategories.add(best.getCategoryName());
            }
        }
        return selected;
    }

    /**
     * 推荐主流程（保留轻量 trace）。
     * <p>按你的要求：重点通过日志输出“重排前多路召回结果”和“重排后结果”。</p>
     */
    @Override
    public List<Product> recommend(String userId, UserProfile profile, int numItems, Map<String, Object> context) {
        Map<String, List<Product>> recallResult = multiChannelRecall(userId, profile, numItems * 3, context);
        List<Product> candidates = recallResult.getOrDefault("merged", List.of());
        if (candidates.isEmpty()) {
            candidates = hotRecall(numItems);
        }

        logRecallSnapshot(userId, recallResult);
        List<Product> ranked = rerank(candidates, profile, numItems * 2);
        log.info("RecommendEngineServiceImpl.recommend user={} 重排后ID={}", userId, toIdList(ranked));

        List<Product> diverse = applyDiversity(ranked, numItems, 0.5);
        log.info("RecommendEngineServiceImpl.recommend user={} 最终推荐ID={}", userId, toIdList(diverse));

        return diverse;
    }

    /**
     * 将用户画像字段拼接为向量检索query。
     */
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

    /**
     * 生成向量检索 metadata 过滤条件。
     */
    private Map<String, Object> buildVectorFilters(UserProfile profile) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (profile == null) {
            return filters;
        }
        if (profile.getPriceRange() != null && profile.getPriceRange().length >= 2) {
            filters.put("price_gte", profile.getPriceRange()[0]);
            filters.put("price_lte", profile.getPriceRange()[1]);
        }
        if (profile.getPreferredCategories() != null && !profile.getPreferredCategories().isEmpty()) {
            filters.put("categoryName", profile.getPreferredCategories().get(0));
        }
        return filters;
    }

    /**
     * RRF 融合。
     * <p>要求每个通道输入有序，否则 rank 语义会失真。</p>
     */
    private List<Product> mergeByRrf(Map<String, List<Product>> channelResult, int limit) {
        if (channelResult == null || channelResult.isEmpty()) {
            return List.of();
        }
        Map<String, Product> productMap = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();

        for (Map.Entry<String, List<Product>> entry : channelResult.entrySet()) {
            String channel = entry.getKey();
            if ("merged".equals(channel)) {
                continue;
            }
            List<Product> products = entry.getValue();
            if (products == null || products.isEmpty()) {
                continue;
            }
            double weight = CHANNEL_WEIGHTS.getOrDefault(channel, 0.1);
            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                if (p == null || p.getProductId() == null) {
                    continue;
                }
                productMap.putIfAbsent(p.getProductId(), p);
                double inc = weight / (RRF_K + i + 1.0);
                scores.put(p.getProductId(), scores.getOrDefault(p.getProductId(), 0.0) + inc);
            }
        }
        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> productMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 通道内排序，保证 RRF 的 rank 输入稳定。
     */
    private List<Product> sortForChannel(String channel, List<Product> products, UserProfile profile) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        Comparator<Product> byRuleDesc = Comparator.comparingDouble((Product p) -> ruleScore(p, profile)).reversed();
        List<Product> sorted = new ArrayList<>(products);
        switch (channel) {
            case "hot":
                sorted.sort(BY_SALES_DESC.thenComparing(BY_RATING_DESC).thenComparing(BY_ID));
                break;
            case "new":
                sorted.sort(byRuleDesc.thenComparing(BY_ID));
                break;
            case "category":
                sorted.sort(byRuleDesc.thenComparing(BY_SALES_DESC).thenComparing(BY_ID));
                break;
            case "vector":
            default:
                // vector 通道在 vectorRecall 已按相似度顺序返回，这里不再二次排序
                break;
        }
        return sorted;
    }

    /**
     * 按指定ID顺序重排，修复数据库回表顺序不稳定问题。
     */
    private List<Product> reorderByIds(List<Product> products, List<String> orderedIds) {
        if (products == null || products.isEmpty() || orderedIds == null || orderedIds.isEmpty()) {
            return products == null ? List.of() : products;
        }
        Map<String, Product> map = products.stream()
                .filter(p -> p != null && p.getProductId() != null)
                .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));
        List<Product> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            Product p = map.get(id);
            if (p != null) {
                ordered.add(p);
            }
        }
        return ordered;
    }

    /**
     * 规则分：销量、评分、偏好匹配、价格匹配、库存惩罚。
     */
    private double ruleScore(Product product, UserProfile profile) {
        double score = 0.0;
        if (product.getSalesCount() != null) {
            score += Math.log(product.getSalesCount() + 1) * 2;
        }
        if (product.getRating() != null) {
            score += product.getRating().doubleValue() * 5;
        }
        if (profile != null) {
            if (profile.getPreferredCategories() != null && product.getCategoryName() != null
                    && profile.getPreferredCategories().contains(product.getCategoryName())) {
                score += 20;
            }
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
            if (profile.getRecentPurchases() != null && product.getBrand() != null) {
                for (String item : profile.getRecentPurchases()) {
                    if (item.contains(product.getBrand()) || product.getBrand().contains(item)) {
                        score += 10;
                    }
                }
            }
        }
        if (product.getStock() != null && product.getStock() < 10) {
            score -= 10;
        }
        return score;
    }

    /**
     * LLM兜底排序，仅在主重排阶段调用。
     */
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
        for (Product p : candidates) {
            if (result.size() >= numItems) {
                break;
            }
            if (!rankedIds.contains(p.getProductId())) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 解析LLM返回的商品ID列表（支持JSON数组和逐行文本）。
     */
    private List<String> parseIdList(String raw) {
        List<String> result = new ArrayList<>();
        try {
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

    /**
     * 记录多路召回结果，便于观察重排前输入。
     */
    private void logRecallSnapshot(String userId, Map<String, List<Product>> recallResult) {
        log.info("RecommendEngineServiceImpl.recommend user={} 多路召回结果 vector={}, hot={}, new={}, category={}, merged={}",
                userId,
                toIdList(recallResult.getOrDefault("vector", List.of())),
                toIdList(recallResult.getOrDefault("hot", List.of())),
                toIdList(recallResult.getOrDefault("new", List.of())),
                toIdList(recallResult.getOrDefault("category", List.of())),
                toIdList(recallResult.getOrDefault("merged", List.of())));
    }

    private List<String> toIdList(List<Product> products) {
        return products.stream().map(Product::getProductId).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 内部模型：商品 + 分数。
     */
    private static class ScoredProduct {
        private final Product product;
        private final double score;

        ScoredProduct(Product product, double score) {
            this.product = product;
            this.score = score;
        }

        Product getProduct() {
            return product;
        }

        double getScore() {
            return score;
        }
    }
}
