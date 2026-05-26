package com.ecommerce.service.impl;

import com.ecommerce.service.DocumentVectorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ecommerce.service.VectorStoreService.DATA_TYPE_FIELD;
import static com.ecommerce.service.VectorStoreService.DATA_TYPE_KNOWLEDGE;

/**
 * 文档向量服务实现
 * 处理 RAG 知识库文档的分块和向量化
 */
@Slf4j
@Service
public class DocumentVectorServiceImpl implements DocumentVectorService {

    @Resource
    private VectorStore vectorStore;

    // 分块大小：每块最大字符数
    private static final int CHUNK_SIZE = 500;
    // 分块重叠：相邻块之间的重叠字符数
    private static final int CHUNK_OVERLAP = 50;
    // 知识分类元数据字段
    private static final String KNOWLEDGE_TYPE_FIELD = "knowledge_type";
    private static final String SUB_TYPE_FIELD = "sub_type";

    @Override
    public void addTextToKnowledgeBase(String content, String source) {
        addTextToKnowledgeBase(content, source, null, null);
    }

    @Override
    public void addTextToKnowledgeBase(String content, String source, String knowledgeType, String subType) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("DocumentVectorServiceImpl.addTextToKnowledgeBase 内容为空，跳过添加: {}", source);
            return;
        }

        // 文本分块
        List<String> chunks = splitText(content, CHUNK_SIZE, CHUNK_OVERLAP);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(DATA_TYPE_FIELD, DATA_TYPE_KNOWLEDGE);
            metadata.put("source", source);
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", chunks.size());
            metadata.put("docType", "text");
            if (StringUtils.isNotBlank(knowledgeType)) {
                metadata.put(KNOWLEDGE_TYPE_FIELD, knowledgeType);
            }
            if (StringUtils.isNotBlank(subType)) {
                metadata.put(SUB_TYPE_FIELD, subType);
            }

            Document doc = new Document(
                    source + "_chunk_" + i,
                    chunks.get(i),
                    metadata
            );
            documents.add(doc);
        }

        addDocumentsToKnowledgeBase(documents);
        log.info("DocumentVectorServiceImpl.addTextToKnowledgeBase 添加文档到知识库: {}, knowledgeType={}, subType={}, 分块数: {}",
                source, knowledgeType, subType, chunks.size());
    }

    @Override
    public void addDocumentToKnowledgeBase(String text, String source, String docType) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("DocumentVectorServiceImpl.addDocumentToKnowledgeBase 文档内容为空，跳过添加: {}", source);
            return;
        }

        List<String> chunks = splitText(text, CHUNK_SIZE, CHUNK_OVERLAP);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(DATA_TYPE_FIELD, DATA_TYPE_KNOWLEDGE);
            metadata.put("source", source);
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", chunks.size());
            metadata.put("docType", docType);

            Document doc = new Document(
                    source + "_chunk_" + i,
                    chunks.get(i),
                    metadata
            );
            documents.add(doc);
        }

        addDocumentsToKnowledgeBase(documents);
        log.info("DocumentVectorServiceImpl.addDocumentToKnowledgeBase 添加{}到知识库: {}, 分块数: {}", docType, source, chunks.size());
    }

    @Override
    public void addDocumentsToKnowledgeBase(List<Document> documents) {
        if (vectorStore == null) {
            log.warn("DocumentVectorServiceImpl.addDocumentsToKnowledgeBase VectorStore不可用，跳过添加文档");
            return;
        }

        try {
            // 分批添加，每批20条
            int batchSize = 20;
            for (int i = 0; i < documents.size(); i += batchSize) {
                List<Document> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
                vectorStore.add(batch);
                // 【硅基流动免费账户 RPM 限制保护】每批间隔 20 秒
                if (i + batchSize < documents.size()) {
                    log.info("DocumentVectorServiceImpl.addDocumentsToKnowledgeBase 等待20秒避免RPM限制...");
                    Thread.sleep(20000);
                }
            }
        } catch (Exception e) {
            log.error("DocumentVectorServiceImpl.addDocumentsToKnowledgeBase 添加文档到知识库失败", e);
        }
    }

    @Override
    public List<Document> searchKnowledgeBase(String query, int topK) {
        // 默认仅搜索知识库文档，不做类别过滤
        return searchKnowledgeBase(query, topK, null, null);
    }

    @Override
    public List<Document> searchKnowledgeBase(String query, int topK, List<String> knowledgeTypes, List<String> subTypes) {
        if (vectorStore == null) {
            log.warn("DocumentVectorServiceImpl.searchKnowledgeBase VectorStore不可用，返回空结果");
            return new ArrayList<>();
        }

        try {
            String filterExpression = buildKnowledgeFilterExpression(knowledgeTypes, subTypes);
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(filterExpression)
                    .build();
            log.info("DocumentVectorServiceImpl.searchKnowledgeBase 搜索知识库, query={}, filter={}", query, filterExpression);
            List<Document> results = vectorStore.similaritySearch(request);
            logSearchResults(results);
            return results;
        } catch (Exception e) {
            log.error("DocumentVectorServiceImpl.searchKnowledgeBase 搜索知识库失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 构建知识库检索的 filterExpression
     * 格式: data_type == 'knowledge' && knowledge_type == 'after_sales' && sub_type == 'return_policy'
     */
    private String buildKnowledgeFilterExpression(List<String> knowledgeTypes, List<String> subTypes) {
        StringBuilder filter = new StringBuilder();
        filter.append(DATA_TYPE_FIELD).append(" == \"").append(DATA_TYPE_KNOWLEDGE).append("\"");

        if (knowledgeTypes != null && !knowledgeTypes.isEmpty()) {
            if (knowledgeTypes.size() == 1) {
                filter.append(" && ").append(KNOWLEDGE_TYPE_FIELD).append(" == \"").append(knowledgeTypes.get(0)).append("\"");
            } else {
                // 多个大类用 OR 连接
                filter.append(" && (");
                for (int i = 0; i < knowledgeTypes.size(); i++) {
                    if (i > 0) filter.append(" || ");
                    filter.append(KNOWLEDGE_TYPE_FIELD).append(" == \"").append(knowledgeTypes.get(i)).append("\"");
                }
                filter.append(")");
            }
        }

        if (subTypes != null && !subTypes.isEmpty()) {
            if (subTypes.size() == 1) {
                filter.append(" && ").append(SUB_TYPE_FIELD).append(" == \"").append(subTypes.get(0)).append("\"");
            } else {
                filter.append(" && (");
                for (int i = 0; i < subTypes.size(); i++) {
                    if (i > 0) filter.append(" || ");
                    filter.append(SUB_TYPE_FIELD).append(" == \"").append(subTypes.get(i)).append("\"");
                }
                filter.append(")");
            }
        }

        return filter.toString();
    }

    @Override
    public void removeFromKnowledgeBase(String source) {
        if (vectorStore == null) {
            log.warn("DocumentVectorServiceImpl.removeFromKnowledgeBase VectorStore不可用，跳过删除");
            return;
        }

        try {
            // 根据 source 过滤删除
            // 注意：这里依赖具体的 VectorStore 实现支持按 metadata 删除
            log.info("DocumentVectorServiceImpl.removeFromKnowledgeBase 从知识库删除文档: {}", source);
        } catch (Exception e) {
            log.error("DocumentVectorServiceImpl.removeFromKnowledgeBase 删除知识库文档失败", e);
        }
    }

    /**
     * 打印检索结果日志，方便排查问题
     */
    private void logSearchResults(List<Document> results) {
        if (results.isEmpty()) {
            log.info("DocumentVectorServiceImpl 检索结果: 0条");
            return;
        }
        log.info("DocumentVectorServiceImpl 检索结果: 共{}条", results.size());
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            Map<String, Object> meta = doc.getMetadata();
            log.info("  [{}] source={}, knowledge_type={}, sub_type={}, text={}",
                    i + 1,
                    meta.get("source"),
                    meta.get("knowledge_type"),
                    meta.get("sub_type"),
                    doc.getText());
        }
    }

    /**
     * 文本分块
     * 使用滑动窗口方式，保证语义连贯性
     */
    private List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        // 按句子分割（简单实现，按句号、问号、感叹号分割）
        String[] sentences = text.split("(?<=[。！？.!?])");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > chunkSize) {
                // 保存当前块
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                // 考虑重叠，从上一个块的末尾开始
                if (currentChunk.length() > chunkOverlap) {
                    currentChunk = new StringBuilder(currentChunk.substring(
                            Math.max(0, currentChunk.length() - chunkOverlap)));
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(sentence);
        }

        // 保存最后一个块
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
