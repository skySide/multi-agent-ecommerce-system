package com.ecommerce.service.impl;

import com.ecommerce.service.MilvusService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Milvus 向量数据库服务实现类
 * 完全基于 Spring AI VectorStore 抽象，不依赖 Milvus SDK
 */
@Slf4j
@Service
public class MilvusServiceImpl implements MilvusService {

    @Resource
    private VectorStore vectorStore;

    @Override
    public void addProductDocuments(List<Document> documents) {
        if (vectorStore == null) {
            log.warn("MilvusServiceImpl.addProductDocuments VectorStore不可用，跳过添加商品文档");
            return;
        }
        try {
            vectorStore.add(documents);
            log.info("MilvusServiceImpl.addProductDocuments 添加 {} 条商品文档到向量库", documents.size());
        } catch (Exception e) {
            log.error("MilvusServiceImpl.addProductDocuments 添加商品文档失败", e);
        }
    }

    @Override
    public void addUserDocuments(List<Document> documents) {
        if (vectorStore == null) {
            log.warn("MilvusServiceImpl.addUserDocuments VectorStore不可用，跳过添加用户文档");
            return;
        }
        try {
            vectorStore.add(documents);
            log.info("MilvusServiceImpl.addUserDocuments 添加 {} 条用户文档到向量库", documents.size());
        } catch (Exception e) {
            log.error("MilvusServiceImpl.addUserDocuments 添加用户文档失败", e);
        }
    }

    @Override
    public List<Document> searchSimilarProducts(String query, int topK) {
        if (vectorStore == null) {
            log.warn("MilvusServiceImpl.searchSimilarProducts VectorStore不可用，返回空结果");
            return new ArrayList<>();
        }
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();
            return vectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.error("MilvusServiceImpl.searchSimilarProducts 搜索相似商品失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Document> searchSimilarProducts(String query, int topK, Map<String, Object> filters) {
        if (vectorStore == null) {
            log.warn("MilvusServiceImpl.searchSimilarProducts VectorStore不可用，返回空结果");
            return new ArrayList<>();
        }
        try {
            String filterExpression = buildFilterExpression(filters);
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(filterExpression)
                    .build();
            return vectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.error("MilvusServiceImpl.searchSimilarProducts 搜索相似商品带过滤失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deleteProductDocuments(List<String> productIds) {
        if (vectorStore == null) {
            log.warn("MilvusServiceImpl.deleteProductDocuments VectorStore不可用，跳过删除商品文档");
            return;
        }
        try {
            vectorStore.delete(productIds);
            log.info("MilvusServiceImpl.deleteProductDocuments 删除 {} 条商品文档", productIds.size());
        } catch (Exception e) {
            log.error("MilvusServiceImpl.deleteProductDocuments 删除商品文档失败", e);
        }
    }

    @Override
    public void deleteUserDocuments(List<String> userIds) {
        if (vectorStore == null) {
            log.warn("MilvusServiceImpl.deleteUserDocuments VectorStore不可用，跳过删除用户文档");
            return;
        }
        try {
            vectorStore.delete(userIds);
            log.info("MilvusServiceImpl.deleteUserDocuments 删除 {} 条用户文档", userIds.size());
        } catch (Exception e) {
            log.error("MilvusServiceImpl.deleteUserDocuments 删除用户文档失败", e);
        }
    }

    /**
     * 构建过滤表达式
     */
    private String buildFilterExpression(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        return filters.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        return key + " == \"" + value + "\"";
                    } else {
                        return key + " == " + value;
                    }
                })
                .collect(Collectors.joining(" && "));
    }
}
