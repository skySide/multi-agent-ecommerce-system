package com.ecommerce.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档向量服务接口
 * 负责 RAG 知识库文档的分块、Embedding 和存储
 */
public interface DocumentVectorService {

    /**
     * 添加文本内容到知识库（自动分块）
     */
    void addTextToKnowledgeBase(String content, String source);

    /**
     * 添加文档到知识库（自动分块）
     */
    void addDocumentToKnowledgeBase(String text, String source, String docType);

    /**
     * 批量添加文档到知识库
     */
    void addDocumentsToKnowledgeBase(List<Document> documents);

    /**
     * 从知识库搜索相关内容
     */
    List<Document> searchKnowledgeBase(String query, int topK);

    /**
     * 删除知识库中的文档
     */
    void removeFromKnowledgeBase(String source);
}
