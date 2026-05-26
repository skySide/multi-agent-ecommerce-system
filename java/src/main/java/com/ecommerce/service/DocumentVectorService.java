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
     * 添加文本内容到知识库（带知识分类标签）
     * @param knowledgeType 知识大类（after_sales/coupon/logistics/member/payment/product_guide/account）
     * @param subType 知识子类（return_policy/coupon_refund 等）
     */
    void addTextToKnowledgeBase(String content, String source, String knowledgeType, String subType);

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
     * 从知识库搜索相关内容（带知识分类过滤）
     * @param knowledgeTypes 知识大类列表，为空则不过滤大类
     * @param subTypes 知识子类列表，为空则不过滤子类
     */
    List<Document> searchKnowledgeBase(String query, int topK, List<String> knowledgeTypes, List<String> subTypes);

    /**
     * 从知识库删除文档
     */
    void removeFromKnowledgeBase(String source);
}
