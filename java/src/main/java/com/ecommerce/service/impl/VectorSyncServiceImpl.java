package com.ecommerce.service.impl;

import com.ecommerce.entity.Product;
import com.ecommerce.service.MilvusService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.VectorSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量同步服务实现
 * 处理结构化数据到向量库的同步
 */
@Slf4j
@Service
public class VectorSyncServiceImpl implements VectorSyncService {

    @Resource
    private MilvusService milvusService;
    @Resource
    private ProductService productService;

    @Override
    public void syncAllProducts() {
        log.info("VectorSyncServiceImpl.syncAllProducts 开始同步所有商品到向量库");
        List<Product> products = productService.list();

        if (products.isEmpty()) {
            log.warn("VectorSyncServiceImpl.syncAllProducts 没有商品数据需要同步");
            return;
        }

        // 分批同步，每批50条
        int batchSize = 50;
        for (int i = 0; i < products.size(); i += batchSize) {
            List<Product> batch = products.subList(i, Math.min(i + batchSize, products.size()));
            List<Document> documents = batch.stream()
                    .map(this::convertProductToDocument)
                    .toList();
            milvusService.addProductDocuments(documents);
            log.info("VectorSyncServiceImpl.syncAllProducts 已同步 {}/{} 条商品", Math.min(i + batchSize, products.size()), products.size());
        }

        log.info("VectorSyncServiceImpl.syncAllProducts 商品同步完成，共 {} 条", products.size());
    }

    @Override
    public void syncProduct(String productId) {
        Product product = productService.getByProductId(productId);
        if (product == null) {
            log.warn("VectorSyncServiceImpl.syncProduct 商品不存在: {}", productId);
            return;
        }

        Document document = convertProductToDocument(product);
        milvusService.addProductDocuments(Collections.singletonList(document));
        log.info("VectorSyncServiceImpl.syncProduct 同步商品到向量库: {}", productId);
    }

    @Override
    public void syncProducts(List<String> productIds) {
        List<Product> products = productService.listByProductIds(productIds);
        List<Document> documents = products.stream()
                .map(this::convertProductToDocument)
                .toList();
        milvusService.addProductDocuments(documents);
        log.info("VectorSyncServiceImpl.syncProducts 批量同步 {} 条商品到向量库", documents.size());
    }

    @Override
    public void removeProductFromVector(String productId) {
        milvusService.deleteProductDocuments(Collections.singletonList(productId));
        log.info("VectorSyncServiceImpl.removeProductFromVector 从向量库删除商品: {}", productId);
    }

    @Override
    public void syncUserProfile(String userId) {
        // TODO: 实现用户画像向量化同步
        log.info("VectorSyncServiceImpl.syncUserProfile 同步用户画像到向量库: {}", userId);
    }

    @Override
    public Document convertProductToDocument(Product product) {
        // 构建用于 Embedding 的文本内容
        String content = String.format(
                "商品名称：%s\n品牌：%s\n类目：%s\n价格：%.2f元\n描述：%s",
                product.getProductName(),
                product.getBrand(),
                product.getCategoryName(),
                product.getPrice(),
                product.getProductDescription()
        );

        // 构建元数据（用于过滤和展示）
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("productId", product.getProductId());
        metadata.put("productName", product.getProductName());
        metadata.put("categoryId", product.getCategoryId());
        metadata.put("categoryName", product.getCategoryName());
        metadata.put("brand", product.getBrand());
        metadata.put("price", product.getPrice().doubleValue());
        metadata.put("originalPrice", product.getOriginalPrice() != null ? product.getOriginalPrice().doubleValue() : 0);
        metadata.put("salesCount", product.getSalesCount());
        metadata.put("rating", product.getRating() != null ? product.getRating().doubleValue() : 0);
        metadata.put("stock", product.getStock());

        return new Document(product.getProductId(), content, metadata);
    }
}
