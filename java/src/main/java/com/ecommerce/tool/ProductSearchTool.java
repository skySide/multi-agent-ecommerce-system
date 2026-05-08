package com.ecommerce.tool;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendEngineService;
import com.ecommerce.service.VectorStoreService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品检索工具
 * 用于 AI Agent 检索商品信息，支持向量召回、推荐引擎和热门商品查询
 */
@Component
public class ProductSearchTool {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchTool.class);

    @Resource
    private ProductService productService;

    @Resource
    private RecommendEngineService recommendEngineService;

    @Resource
    private VectorStoreService vectorStoreService;

    /** 向量召回倍数系数，用于多取候选再精确匹配 */
    private static final int RECALL_FACTOR = 2;

    /**
     * 通过向量相似度搜索召回商品
     */
    @Tool(name = "retrieveSimilarProducts", description = "【向量搜索】根据输入文本进行语义相似度搜索。需要用户提供具体的查询词(如商品名/品牌)，适用于用户明确提到具体商品、想搜索类似商品的场景。query参数必须有值不能为空")
    public List<Product> retrieveSimilarProducts(
            @ToolParam(description = "搜索关键词，从用户消息中提取的具体商品名、品牌名或类目名，。不能为空，如果用户没有提到具体商品则不要调用此工具") String query,
            @ToolParam(description = "返回商品数量，默认为10") int topK) {
        // 步骤1: 参数校验
        if (org.apache.commons.lang3.StringUtils.isBlank(query)) {
            log.error("ProductSearchTool.retrieveSimilarProducts - 参数为空, query: {}", query);
            return Collections.emptyList();
        }

        log.info("ProductSearchTool.retrieveSimilarProducts - 向量召回, query: {}, topK: {}", query, topK);

        // 步骤2: 向量检索
        List<Document> docs = vectorStoreService.searchSimilarProducts(query, topK * RECALL_FACTOR);
        if (CollectionUtils.isEmpty(docs)) {
            log.info("ProductSearchTool.retrieveSimilarProducts - 向量召回无结果, query: {}", query);
            return Collections.emptyList();
        }

        // 步骤3: 按向量相似度顺序提取商品ID（去重）
        List<String> orderedIds = new ArrayList<>();
        for (Document doc : docs) {
            Object id = doc.getMetadata().get("productId");
            if (Objects.nonNull(id)) {
                String pid = id.toString();
                if (!orderedIds.contains(pid)) {
                    orderedIds.add(pid);
                    if (orderedIds.size() >= topK) {
                        break;
                    }
                }
            }
        }
        if (orderedIds.isEmpty()) {
            log.info("ProductSearchTool.retrieveSimilarProducts - 未提取到商品ID, query: {}", query);
            return Collections.emptyList();
        }

        // 步骤4: 数据库回表，按向量召回顺序重排
        List<Product> products = productService.listByProductIds(orderedIds);
        if (CollectionUtils.isEmpty(products)) {
            log.info("ProductSearchTool.retrieveSimilarProducts - 数据库回表无结果, query: {}", query);
            return Collections.emptyList();
        }
        List<Product> result = reorderByIds(products, orderedIds);

        log.info("ProductSearchTool.retrieveSimilarProducts - 召回完成, query: {}, 结果: {}条", query, result.size());
        return result;
    }

    /**
     * 获取完整的推荐结果
     */
    @Tool(name = "recommendProducts", description = "【推荐引擎】多路召回+精排+多样性控制。根据用户画像和偏好类目推荐商品。最常用工具，优先选择")
    public List<Product> recommendProducts(
            @ToolParam(description = "用户ID，从当前登录用户获取") String userId,
            @ToolParam(description = "偏好类目，可选参数。用户提到想看的类目时传入，没有则传空字符串") String category,
            @ToolParam(description = "需要推荐的商品数量，默认为10") int numItems) {
        log.info("ProductSearchTool.recommendProducts - 推荐商品, userId: {}, category: {}, numItems: {}",
                userId, category, numItems);

        // 步骤1: 构建用户画像
        UserProfile profile = UserProfile.builder().userId(userId).build();
        if (Objects.nonNull(category)) {
            profile.setPreferredCategories(category);
        }

        // 步骤2: 调用推荐引擎
        Map<String, Object> context = new HashMap<>();
        List<Product> products = recommendEngineService.recommend(userId, profile, numItems, context);
        if (CollectionUtils.isEmpty(products)) {
            log.warn("ProductSearchTool.recommendProducts - 推荐引擎返回空, userId: {}", userId);
            return Collections.emptyList();
        }

        log.info("ProductSearchTool.recommendProducts - 推荐完成, userId: {}, products = {}", userId, products);
        return products;
    }

    /**
     * 获取热门商品列表
     */
    @Tool(name = "getHotProducts", description = "【热门商品】获取当前热门/畅销商品排行，按销量降序排列。适用于用户想看热门商品、畅销排行的场景")
    public List<Product> getHotProducts(
            @ToolParam(description = "返回商品数量，默认为10") int numItems) {
        log.info("ProductSearchTool.getHotProducts - 获取热门商品, numItems: {}", numItems);

        // 步骤1: 查询热门商品
        List<Product> products = productService.listHotProducts(numItems);
        if (CollectionUtils.isEmpty(products)) {
            log.warn("ProductSearchTool.getHotProducts - 无热门商品数据");
            return Collections.emptyList();
        }

        log.info("ProductSearchTool.getHotProducts - 获取完成, 结果: {}条", products.size());
        return products;
    }

    /**
     * 查询单个商品的详细信息
     */
    @Tool(name = "getProductInfo", description = "查询单个商品的详细信息，包括商品名称、类目、价格、品牌和描述")
    public Product getProductInfo(
            @ToolParam(description = "商品ID") String productId) {
        // 步骤1: 参数校验
        if (Objects.isNull(productId)) {
            log.error("ProductSearchTool.getProductInfo - 参数为空, productId: null");
            return null;
        }

        log.info("ProductSearchTool.getProductInfo - 查询商品信息, productId: {}", productId);

        // 步骤2: 查询商品
        Product product = productService.getByProductId(productId);
        if (Objects.isNull(product)) {
            log.warn("ProductSearchTool.getProductInfo - 商品不存在, productId: {}", productId);
            return null;
        }

        log.info("ProductSearchTool.getProductInfo - 查询完成, productId: {}, name: {}", productId, product.getProductName());
        return product;
    }

    /**
     * 按指定ID顺序重排商品列表
     */
    private List<Product> reorderByIds(List<Product> products, List<String> orderedIds) {
        Map<String, Product> productMap = products.stream()
                .filter(p -> Objects.nonNull(p) && Objects.nonNull(p.getProductId()))
                .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        List<Product> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            Product product = productMap.get(id);
            if (Objects.nonNull(product)) {
                ordered.add(product);
            }
        }
        return ordered;
    }
}
