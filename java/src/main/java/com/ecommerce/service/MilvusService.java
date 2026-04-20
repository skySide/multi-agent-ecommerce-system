package com.ecommerce.service;

import java.util.List;

/**
 * Milvus 向量数据库服务接口
 */
public interface MilvusService {

    // Collection 名称
    String PRODUCT_COLLECTION = "product_vectors";
    String USER_COLLECTION = "user_vectors";

    // 向量维度
    int VECTOR_DIM = 768;

    /**
     * 初始化商品向量 Collection
     */
    void initProductCollection();

    /**
     * 初始化用户向量 Collection
     */
    void initUserCollection();

    /**
     * 插入商品向量
     */
    void insertProductVectors(List<String> productIds,
                               List<List<Float>> embeddings,
                               List<String> categoryIds,
                               List<Float> prices,
                               List<Integer> salesCounts);

    /**
     * 搜索相似商品
     */
    List<String> searchSimilarProducts(List<Float> queryVector, int topK);

    /**
     * 搜索相似用户（用于 UserCF）
     */
    List<String> searchSimilarUsers(List<Float> userVector, int topK);
}
