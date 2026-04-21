package com.ecommerce.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * Milvus 向量数据库服务接口
 * 基于 Spring AI VectorStore 抽象，完全屏蔽底层 Milvus SDK
 */
public interface MilvusService {

    // Collection 名称
    String PRODUCT_COLLECTION = "product_vectors";
    String USER_COLLECTION = "user_vectors";

    // 向量维度
    int VECTOR_DIM = 768;

    /**
     * 添加商品文档到向量库
     */
    void addProductDocuments(List<Document> documents);

    /**
     * 添加用户文档到向量库
     */
    void addUserDocuments(List<Document> documents);

    /**
     * 搜索相似商品（基于向量）
     */
    List<Document> searchSimilarProducts(String query, int topK);

    /**
     * 搜索相似商品（基于向量，带过滤条件）
     */
    List<Document> searchSimilarProducts(String query, int topK, Map<String, Object> filters);

    /**
     * 删除商品文档
     */
    void deleteProductDocuments(List<String> productIds);

    /**
     * 删除用户文档
     */
    void deleteUserDocuments(List<String> userIds);
}
