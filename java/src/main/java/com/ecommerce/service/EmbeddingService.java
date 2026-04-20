package com.ecommerce.service;

import java.util.List;

/**
 * Embedding 服务接口
 * 调用 Python 服务生成向量
 */
public interface EmbeddingService {

    /**
     * 生成单个文本的 Embedding
     */
    List<Float> generateEmbedding(String text);

    /**
     * 批量生成 Embedding
     */
    List<List<Float>> generateEmbeddings(List<String> texts);

    /**
     * 同步所有商品到向量数据库
     */
    void syncAllProductsToVector();
}
