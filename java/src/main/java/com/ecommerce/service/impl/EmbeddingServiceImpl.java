package com.ecommerce.service.impl;

import com.ecommerce.service.EmbeddingService;
import com.ecommerce.service.VectorSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Embedding 服务实现类
 * 基于 Spring AI EmbeddingModel（SiliconFlow BAAI/bge-large-zh-v1.5）生成向量
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private VectorSyncService vectorSyncService;

    @Override
    public List<Float> generateEmbedding(String text) {
        try {
            float[] vector = embeddingModel.embed(text);
            if (vector == null || vector.length == 0) {
                log.warn("EmbeddingServiceImpl.generateEmbedding - 返回空向量, text: {}", text);
                return null;
            }
            List<Float> result = new ArrayList<>(vector.length);
            for (float v : vector) {
                result.add(v);
            }
            return result;
        } catch (Exception e) {
            log.error("EmbeddingServiceImpl.generateEmbedding - 生成失败, text: {}", text, e);
            return null;
        }
    }

    @Override
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        log.info("EmbeddingService.generateEmbeddings begin, texts = {}", texts);
        try {
            List<float[]> vectors = embeddingModel.embed(texts);
            if (vectors == null || vectors.isEmpty()) {
                log.warn("EmbeddingService.generateEmbeddings - 返回空向量列表");
                return Collections.emptyList();
            }
            List<List<Float>> results = new ArrayList<>(vectors.size());
            for (float[] vector : vectors) {
                if (vector == null || vector.length == 0) {
                    results.add(null);
                } else {
                    List<Float> list = new ArrayList<>(vector.length);
                    for (float v : vector) {
                        list.add(v);
                    }
                    results.add(list);
                }
            }
            log.info("EmbeddingService.generateEmbeddings end, texts.length = {}, results.size = {}", texts.size(), results.size());
            return results;
        } catch (Exception e) {
            log.error("EmbeddingService.generateEmbeddings - 批量生成失败, texts = {}", texts, e);
            return null;
        }
    }

    @Override
    public void syncAllProductsToVector() {
        try {
            vectorSyncService.syncAllProducts();
            log.info("EmbeddingServiceImpl.syncAllProductsToVector - 触发商品向量同步完成");
        } catch (Exception e) {
            log.error("EmbeddingServiceImpl.syncAllProductsToVector - 同步商品向量失败", e);
        }
    }
}
