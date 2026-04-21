package com.ecommerce.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量同步服务接口
 * 负责将结构化数据（商品/用户）同步到向量数据库
 */
public interface VectorSyncService {

    /**
     * 同步所有商品到向量库
     */
    void syncAllProducts();

    /**
     * 同步指定商品到向量库
     */
    void syncProduct(String productId);

    /**
     * 批量同步商品到向量库
     */
    void syncProducts(List<String> productIds);

    /**
     * 从向量库删除商品
     */
    void removeProductFromVector(String productId);

    /**
     * 同步用户画像到向量库
     */
    void syncUserProfile(String userId);

    /**
     * 将商品转换为向量文档
     */
    Document convertProductToDocument(com.ecommerce.entity.Product product);
}
