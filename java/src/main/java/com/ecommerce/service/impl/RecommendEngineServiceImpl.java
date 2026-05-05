package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.ecommerce.common.enums.ErrorCode;
import com.ecommerce.dto.RewriteResultDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.QueryRewriteService;
import com.ecommerce.service.RecommendEngineService;
import com.ecommerce.service.VectorStoreService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐引擎核心服务实现。
 * <p>流程：Query改写 -> 多路召回 -> RRF融合 -> 重排 -> 多样性控制。</p>
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
    @Resource
    private QueryRewriteService queryRewriteService;

    /** 召回通道权重 */
    private static final double VECTOR_RECALL_WEIGHT = 0.4;
    private static final double HOT_RECALL_WEIGHT = 0.2;
    private static final double NEW_RECALL_WEIGHT = 0.2;
    private static final double CATEGORY_RECALL_WEIGHT = 0.2;

    /** 各通道召回数量系数 */
    private static final int RECALL_FACTOR = 3;
    /** RRF 融合常量（越大越平滑） */
    private static final int RRF_K = 60;

    /** 固定通道权重，避免每次调用重复创建对象 */
    private static final Map<String, Double> CHANNEL_WEIGHTS = Map.of(
            "vector", VECTOR_RECALL_WEIGHT,
            "hot", HOT_RECALL_WEIGHT,
            "new", NEW_RECALL_WEIGHT,
            "category", CATEGORY_RECALL_WEIGHT
    );

    /** 固定比较器，避免每次调用重复创建对象 */
    private static final Comparator<Product> BY_ID =
            Comparator.comparing(p -> p.getProductId() == null ? "" : p.getProductId());
    private static final Comparator<Product> BY_SALES_DESC =
            Comparator.comparingInt((Product p) -> p.getSalesCount() == null ? 0 : p.getSalesCount()).reversed();
    private static final Comparator<Product> BY_RATING_DESC =
            Comparator.comparingDouble((Product p) -> p.getRating() == null ? 0.0 : p.getRating().doubleValue()).reversed();

    /**
     * 多路召回主入口。
     * <p>步骤：四路召回 -> 通道内稳定排序 -> RRF融合。</p>
     * <p>所有召回通道都会结合用户查询条件（如有）进行过滤。</p>
     */
    @Override
    public Map<String, List<Product>> multiChannelRecall(UserProfile profile, int numItems, Map<String, Object> context) {
        log.info("RecommendEngineServiceImpl.multiChannelRecall begin, profile = {}, numItems = {}, context = {}",
                profile, numItems, context);
        Map<String, List<Product>> result = new HashMap<>();
        int recallNum = numItems * RECALL_FACTOR;

        // 从 context 提取改写后的查询信息
        String query = extractQueryFromContext(context);
        String queryCategory = extractCategoryFromContext(context);
        String queryBrand = extractBrandFromContext(context);
        BigDecimal queryPriceMin = extractPriceFromContext(context, "price_min");
        BigDecimal queryPriceMax = extractPriceFromContext(context, "price_max");

        log.info("RecommendEngineServiceImpl.multiChannelRecall 查询条件: category={}, brand={}, price={}~{}",
                queryCategory, queryBrand, queryPriceMin, queryPriceMax);

        // 1. 向量召回：使用改写后的 query
        try {
            List<Product> vectorProducts = vectorRecall(query, profile, (int) (recallNum * VECTOR_RECALL_WEIGHT));
            // 向量召回已经使用改写后的 query，无需再过滤
            result.put("vector", sortForChannel("vector", vectorProducts, profile));
        } catch (Exception e) {
            log.error("RecommendEngineService.multiChannelRecall 向量召回失败: profile = {}, context = {}",
                    profile, context, e);
            result.put("vector", List.of());
        }

        // 2. 热门召回：结合用户查询条件过滤
        try {
            List<Product> hotProducts = hotRecallWithFilters(
                    (int) (recallNum * HOT_RECALL_WEIGHT),
                    queryCategory, queryBrand, queryPriceMin, queryPriceMax);
            result.put("hot", sortForChannel("hot", hotProducts, profile));
        } catch (Exception e) {
            log.error("RecommendEngineService.multiChannelRecall 热门召回失败: profile = {}, context = {}",
                    profile, context, e);
            result.put("hot", List.of());
        }

        // 3. 新品召回：结合用户查询条件过滤
        try {
            List<Product> newProducts = newArrivalRecallWithFilters(
                    (int) (recallNum * NEW_RECALL_WEIGHT),
                    queryCategory, queryBrand, queryPriceMin, queryPriceMax);
            result.put("new", sortForChannel("new", newProducts, profile));
        } catch (Exception e) {
            log.warn("RecommendEngineService.multiChannelRecall 新品召回失败: profile = {}, context = {}",
                    profile, context, e);
            result.put("new", List.of());
        }

        // 4. 类目召回：优先使用查询中的类目，否则使用画像偏好
        try {
            List<Product> categoryProducts = categoryRecallWithQuery(
                    profile, (int) (recallNum * CATEGORY_RECALL_WEIGHT),
                    queryCategory, queryBrand, queryPriceMin, queryPriceMax);
            result.put("category", sortForChannel("category", categoryProducts, profile));
        } catch (Exception e) {
            log.warn("RecommendEngineService.multiChannelRecall 类目召回失败: profile = {}, context = {}",
                    profile, context, e);
            result.put("category", List.of());
        }

        List<Product> merged = mergeByRrf(result, numItems);
        result.put("merged", merged);
        log.info("RecommendEngineService.multiChannelRecall end, profile = {}, context = {}, merged = {}",
                profile, context, merged);
        return result;
    }

    /**
     * 从 context 中提取类目
     */
    private String extractCategoryFromContext(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object category = context.get("category");
        if (category == null) {
            category = context.get("categories");
        }
        if (category instanceof String) {
            return (String) category;
        }
        return null;
    }

    /**
     * 从 context 中提取品牌
     */
    private String extractBrandFromContext(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object brand = context.get("brand");
        if (brand == null) {
            brand = context.get("brands");
        }
        if (brand instanceof String) {
            return (String) brand;
        }
        return null;
    }

    /**
     * 从 context 中提取改写后的query
     */
    private String extractQueryFromContext(Map<String, Object> context) {
        if (Objects.isNull(context)) {
            return null;
        }
        Object rewrittenQuery = context.get("rewritten_query");
        if (Objects.nonNull(rewrittenQuery)) {
            return rewrittenQuery.toString();
        }
        Object userQuery = context.get("user_query");
        if (Objects.nonNull(userQuery)) {
            return userQuery.toString();
        }
        return null;
    }

    /**
     * 从 context 中提取价格
     */
    private BigDecimal extractPriceFromContext(Map<String, Object> context, String key) {
        if (context == null) {
            return null;
        }
        Object price = context.get(key);
        if (price instanceof Number) {
            return BigDecimal.valueOf(((Number) price).doubleValue());
        }
        return null;
    }

    /**
     * 热门召回（带过滤条件）
     */
    private List<Product> hotRecallWithFilters(int numItems, String category, String brand,
                                                 BigDecimal priceMin, BigDecimal priceMax) {
        // 先获取热门商品（多取一些用于过滤）
        List<Product> hotProducts = productService.listHotProducts(numItems * 3);

        // 根据用户查询条件过滤
        List<Product> filtered = hotProducts.stream()
                .filter(p -> matchesCategory(p, category))
                .filter(p -> matchesBrand(p, brand))
                .filter(p -> matchesPrice(p, priceMin, priceMax))
                .limit(numItems)
                .collect(Collectors.toList());

        log.info("RecommendEngineServiceImpl.hotRecallWithFilters 热门召回: 原始{}条 → 过滤后{}条",
                hotProducts.size(), filtered.size());

        // 如果过滤后为空，降级返回热门商品（不过滤）
        if (filtered.isEmpty() && (category != null || brand != null || priceMin != null)) {
            log.warn("RecommendEngineServiceImpl.hotRecallWithFilters 过滤后为空，降级返回热门商品");
            return hotProducts.stream().limit(numItems).collect(Collectors.toList());
        }

        return filtered;
    }

    /**
     * 新品召回（带过滤条件）
     */
    private List<Product> newArrivalRecallWithFilters(int numItems, String category, String brand,
                                                        BigDecimal priceMin, BigDecimal priceMax) {
        List<Product> newProducts = productService.listNewArrivals(numItems * 3);

        List<Product> filtered = newProducts.stream()
                .filter(p -> matchesCategory(p, category))
                .filter(p -> matchesBrand(p, brand))
                .filter(p -> matchesPrice(p, priceMin, priceMax))
                .limit(numItems)
                .collect(Collectors.toList());

        log.info("RecommendEngineServiceImpl.newArrivalRecallWithFilters 新品召回: 原始{}条 → 过滤后{}条",
                newProducts.size(), filtered.size());

        if (filtered.isEmpty() && (category != null || brand != null || priceMin != null)) {
            log.warn("RecommendEngineServiceImpl.newArrivalRecallWithFilters 过滤后为空，降级返回新品");
            return newProducts.stream().limit(numItems).collect(Collectors.toList());
        }

        return filtered;
    }

    /**
     * 类目召回（结合用户查询）
     */
    private List<Product> categoryRecallWithQuery(UserProfile profile, int numItems,
                                                    String queryCategory, String queryBrand,
                                                    BigDecimal priceMin, BigDecimal priceMax) {
        // 优先使用用户查询中的类目，否则使用画像偏好
        String targetCategory = queryCategory;
        if (targetCategory == null && profile != null && profile.getPreferredCategories() != null) {
            targetCategory = profile.getPreferredCategories().split(",")[0];
        }

        if (targetCategory == null || targetCategory.isEmpty()) {
            // 无类目偏好，返回热门
            return hotRecallWithFilters(numItems, null, queryBrand, priceMin, priceMax);
        }

        // 使用类目搜索
        List<Product> products = productService.searchByKeyword(targetCategory, numItems * 2);

        // 额外过滤品牌和价格
        List<Product> filtered = products.stream()
                .filter(p -> matchesBrand(p, queryBrand))
                .filter(p -> matchesPrice(p, priceMin, priceMax))
                .limit(numItems)
                .collect(Collectors.toList());

        log.info("RecommendEngineServiceImpl.categoryRecallWithQuery 类目召回: 类目={}, 原始{}条 → 过滤后{}条",
                targetCategory, products.size(), filtered.size());

        return filtered;
    }

    /**
     * 判断商品是否匹配类目
     */
    private boolean matchesCategory(Product product, String category) {
        if (category == null || category.isEmpty()) {
            return true;
        }
        if (product.getCategoryName() == null) {
            return false;
        }
        // 支持部分匹配：用户说"电脑"可以匹配"笔记本"
        return product.getCategoryName().contains(category) || category.contains(product.getCategoryName());
    }

    /**
     * 判断商品是否匹配品牌
     */
    private boolean matchesBrand(Product product, String brand) {
        if (brand == null || brand.isEmpty()) {
            return true;
        }
        if (product.getBrand() == null) {
            return false;
        }
        return product.getBrand().equalsIgnoreCase(brand);
    }

    /**
     * 判断商品是否匹配价格区间
     */
    private boolean matchesPrice(Product product, BigDecimal priceMin, BigDecimal priceMax) {
        if (priceMin == null && priceMax == null) {
            return true;
        }
        if (product.getPrice() == null) {
            return false;
        }
        double price = product.getPrice().doubleValue();
        if (priceMin != null && price < priceMin.doubleValue()) {
            return false;
        }
        if (priceMax != null && price > priceMax.doubleValue()) {
            return false;
        }
        return true;
    }

    /**
     * 向量召回通道：query构建、metadata过滤、回表并按向量顺序重排。
     * 需要重新排序的目的，是因为查询数据库的数据，和向量召回的顺序有可能不一样，
     * 而向量召回的数据已经按照相似度进行排序了，因此需要查询数据库之后，还需要再进行排序。
     * 之所以要查询数据库，是因为可能我们数据库变更之后，没有那么快同步到向量数据库中，
     * 因此需要再次查询数据库，从而得到准确的数据，降低幻觉。
     */
    @Override
    public List<Product> vectorRecall(String query, UserProfile profile, int numItems) {
        log.info("RecommendEngineService.vectorRecall begin, query = {}, profile = {}", query, profile);
        //String query = buildQueryFromProfile(profile);
        Map<String, Object> filters = buildVectorFilters(profile);
        List<Document> docs = filters.isEmpty()
                ? vectorStoreService.searchSimilarProducts(query, numItems * 2)
                : vectorStoreService.searchSimilarProducts(query, numItems * 2, filters);

        List<String> productIds = docs.stream()
                .map(doc -> {
                    Object id = doc.getMetadata().get("productId");
                    if (Objects.nonNull(id)) {
                        return id.toString();
                    }
                    return null;
                })
                .filter(item -> !StringUtils.isBlank(item))
                .distinct()
                .limit(numItems)
                .collect(Collectors.toList());
        if (productIds.isEmpty()) {
            log.error("RecommendEngineService.vectorRecall unable to recall products");
            return List.of();
        }
        List<Product> products = productService.listByProductIds(productIds);
        List<Product> reorderProductList = reorderByIds(products, productIds);
        log.info("RecommendEngineService.vectorRecall end, reorderProductList = {}", reorderProductList);
        return reorderProductList;
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
        List<String> categoryNames = Arrays.asList(profile.getPreferredCategories().split(","));
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
     * 推荐主流程。
     * <p>步骤：Query改写 -> 多路召回 -> 精排 -> 多样性控制。</p>
     * <p>支持基于用户原始查询进行智能改写，提升召回精准度。</p>
     */
    @Override
    public List<Product> recommend(String userId, UserProfile profile, int numItems, Map<String, Object> context) {
        log.info("RecommendEngineServiceImpl.recommend 用户={} 开始推荐流程", userId);

        // 1. 从 context 中获取用户原始查询，进行智能改写
        doRewriteQuery(context);
        // 2. 多路召回
        Map<String, List<Product>> recallResult = multiChannelRecall(profile, numItems * 3, context);
        List<Product> candidates = recallResult.getOrDefault("merged", List.of());
        if (candidates.isEmpty()) {
            log.info("RecommendEngineService.recommend error, unable to recall products");
            candidates = hotRecall(numItems);
        }

        // 3. 记录召回快照
        logRecallSnapshot(userId, recallResult);

        // 4. 精排
        List<Product> ranked = rerank(candidates, profile, numItems * 2);
        log.info("RecommendEngineServiceImpl.recommend user={} 重排后ID={}", userId, toIdList(ranked));

        // 5. 多样性控制
        List<Product> diverse = applyDiversity(ranked, numItems, 0.5);
        log.info("RecommendEngineServiceImpl.recommend user={} 最终推荐ID={}", userId, toIdList(diverse));

        return diverse;
    }

    /**
     * 对userQuery进行改写：
     * 具体逻辑通过利用LLM，基于当前已有的userQuery、对话历史、以及 用户画像这些信息
     * 从而进行改写userQuery
     * @param context
     */
    private void doRewriteQuery(Map<String, Object> context) {
        String userQuery = extractUserQuery(context);
        if (!StringUtils.isBlank(userQuery)) {
            try {
                // 使用 LLM 改写 query，获取意图和实体
                RewriteResultDTO rewriteResult = queryRewriteService.rewrite(userQuery, context);
                log.info("RecommendEngineServiceImpl.doRewriteQuery query改写: originalUserQuery = {}, rewriteQuery = {}",
                        userQuery, rewriteResult.getRewrittenQuery());
                // 将改写结果合并到 context，供后续召回使用
                if (Objects.nonNull(context)) {
                    if (Objects.nonNull(rewriteResult.getEntities())) {
                        context.putAll(rewriteResult.getEntities());
                    }
                    // 保存改写后的 query，供向量召回使用
                    if (!StringUtils.isBlank(rewriteResult.getRewrittenQuery())) {
                        context.put("rewritten_query", rewriteResult.getRewrittenQuery());
                    }
                }
            } catch (Exception e) {
                log.error("RecommendEngineServiceImpl.doRewriteQuery query改写失败: =context = {}",context, e);
                throw new BusinessException(ErrorCode.RECOMMEND_ERROR.getCode(), "query改写异常");
            }
        }
    }

    /**
     * 从 context 中提取用户原始查询
     */
    private String extractUserQuery(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object query = context.get("userQuery");
        if (query == null) {
            query = context.get("query");
        }
        if (query == null) {
            query = context.get("message");
        }
        if (query != null) {
            return query.toString();
        }
        return null;
    }

    /**
     * 基于改写结果丰富用户画像
     */
    private UserProfile enrichProfileFromRewrite(UserProfile profile, RewriteResultDTO rewriteResult) {
        if (Objects.isNull(profile)) {
            log.error("RecommendEngineService.enrichProfileFromRewrite error, profile is null");
            return null;
        }
        if (Objects.isNull(rewriteResult) || CollectionUtils.isEmpty(rewriteResult.getEntities())) {
            return profile;
        }

        log.info("RecommendEngineService.enrichProfileFromRewrite begin, profile = {}, rewriteResult = {}", profile, rewriteResult);
        Map<String, Object> entities = rewriteResult.getEntities();
        // 从实体中更新偏好
        if (entities.get("category") instanceof String) {
            profile.setPreferredCategories((String) entities.get("category"));
        }
        if (entities.get("brand") instanceof String) {
            profile.setPreferredBrands((String) entities.get("brand"));
        }
        if (entities.get("price_min") instanceof Number) {
            BigDecimal priceMin = BigDecimal.valueOf(((Number) entities.get("price_min")).doubleValue());
            profile.setPriceRangeMin(priceMin);
        }
        if (entities.get("price_max") instanceof Number) {
            BigDecimal priceMax = BigDecimal.valueOf(((Number) entities.get("price_max")).doubleValue());
            profile.setPriceRangeMax(priceMax);
        }
        log.info("RecommendEngineService.enrichProfileFromRewrite end, enrichProfile = {}", profile);
        return profile;
    }

    /**
     * 将用户画像字段拼接为向量检索 query。
     * <p>优先使用 LLM 进行智能改写，失败时降级为简单拼接。</p>
     */
    /*private String buildQueryFromProfile(UserProfile profile) {
        if (profile == null) {
            return "热门商品推荐";
        }

        // 从画像中提取偏好信息
        String categories = profile.getPreferredCategories();
        String brands = profile.getPreferredBrands();
        String priceRange = null;
        if (profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null) {
            priceRange = String.format("%.0f-%.0f元",
                    profile.getPriceRangeMin().doubleValue(),
                    profile.getPriceRangeMax().doubleValue());
        }

        // 如果没有偏好信息，返回默认
        if ((categories == null || categories.isEmpty()) && (brands == null || brands.isEmpty())) {
            return "热门商品推荐";
        }

        // 构建原始 query
        StringBuilder rawQuery = new StringBuilder();
        if (categories != null && !categories.isEmpty()) {
            rawQuery.append(categories.replace(",", " "));
        }
        if (brands != null && !brands.isEmpty()) {
            if (rawQuery.length() > 0) {
                rawQuery.append(" ");
            }
            rawQuery.append(brands.replace(",", " "));
        }
        if (priceRange != null) {
            rawQuery.append(" ").append(priceRange);
        }

        // 使用 LLM 改写 query（带降级）
        try {
            Map<String, Object> profileMap = new HashMap<>();
            profileMap.put("categories", categories);
            profileMap.put("brands", brands);
            profileMap.put("priceRange", priceRange);

            String rewritten = queryRewriteService.toVectorQuery(rawQuery.toString(), profileMap);
            if (rewritten != null && !rewritten.isEmpty()) {
                log.info("RecommendEngineServiceImpl.buildQueryFromProfile query改写: {} → {}",
                        rawQuery, rewritten);
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("RecommendEngineServiceImpl.buildQueryFromProfile LLM改写失败，使用原始query: {}",
                    e.getMessage());
        }

        // 降级：直接返回原始 query
        if (rawQuery.length() > 0) {
            return rawQuery.toString();
        } else {
            return "热门商品推荐";
        }
    }*/

    /**
     * 生成向量检索 metadata 过滤条件。
     */
    private Map<String, Object> buildVectorFilters(UserProfile profile) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (profile == null) {
            return filters;
        }
        if (profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null) {
            filters.put("price_gte", profile.getPriceRangeMin());
            filters.put("price_lte", profile.getPriceRangeMax());
        }
        if (profile.getPreferredCategories() != null && !profile.getPreferredCategories().isEmpty()) {
            filters.put("categoryName", profile.getPreferredCategories().split(",")[0]);
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
        if (CollectionUtils.isEmpty(products) || CollectionUtils.isEmpty(orderedIds)) {
            return CollectionUtils.isEmpty(products) ? Collections.EMPTY_LIST : products;
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
            if (profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null && product.getPrice() != null) {
                double min = profile.getPriceRangeMin().doubleValue();
                double max = profile.getPriceRangeMax().doubleValue();
                double price = product.getPrice().doubleValue();
                if (price >= min && price <= max) {
                    score += 15;
                } else if (price <= max * 1.2) {
                    score += 5;
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
                profile.getPriceRangeMin() != null ? profile.getPriceRangeMin().doubleValue() : 0,
                profile.getPriceRangeMax() != null ? profile.getPriceRangeMax().doubleValue() : 10000,
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
            String trimmed = raw.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String content = trimmed.substring(1, trimmed.length() - 1);
                for (String part : content.split(",")) {
                    String id = part.trim().replace("\"", "").replace("'", "");
                    if (!id.isEmpty()) {
                        result.add(id);
                    }
                }
            } else {
                for (String line : trimmed.split("\n")) {
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
        log.info("RecommendEngineService.recommend user={} 多路召回结果 vector={}, hot={}, new={}, category={}, merged={}",
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
