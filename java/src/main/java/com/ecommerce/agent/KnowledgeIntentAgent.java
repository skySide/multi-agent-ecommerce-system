package com.ecommerce.agent;

import com.ecommerce.dto.CategoryHit;
import com.ecommerce.dto.ClassifyResultDTO;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.DocumentVectorService;
import com.ecommerce.service.KnowledgeClassifyService;
import com.ecommerce.service.QueryRewriteService;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识问答意图Agent
 * 基于 ReAct 模式：Think（Query改写 + 分类）→ Act（RAG检索）→ Observe（LLM生成回答）
 * Query改写 + RAG向量搜索知识库 + LLM回复生成
 */
@Component
public class KnowledgeIntentAgent extends ReActAgent {

    @Resource
    private DocumentVectorService documentVectorService;

    @Resource
    private QueryRewriteService queryRewriteService;

    @Resource
    private KnowledgeClassifyService knowledgeClassifyService;

    public KnowledgeIntentAgent() {
        super("knowledge_intent", 8.0, 2);
    }

    // ==================== ReActAgent 抽象方法实现 ====================

    @Override
    protected String buildSystemPrompt(Map<String, Object> params) {
        return "你是电商客服助手。请根据知识库信息回答用户问题。";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildUserMessage(Map<String, Object> params) {
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        // 构建历史上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);
        return historyContext + "用户问题：" + message;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String userId = (String) params.get("userId");
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        log.info("KnowledgeIntentAgent.execute - 知识问答, userId: {}, message: {}", userId, message);

        // 步骤2: Think → Query改写
        String rewrittenQuery = rewriteQuery(message, history, summary);

        // 步骤3: Think → LLM知识分类
        ClassifyResultDTO classifyResult = classifyQuery(rewrittenQuery, history);

        // 步骤4: Act → 带分类过滤的RAG检索
        List<Document> docs = searchWithClassification(rewrittenQuery, classifyResult);

        // 步骤5: Observe → 构建知识库上下文
        String context = docs.stream()
                .map(d -> {
                    String source = (String) d.getMetadata().get("source");
                    String kt = (String) d.getMetadata().get("knowledge_type");
                    String st = (String) d.getMetadata().get("sub_type");
                    String label = (kt != null) ? "【" + kt + (st != null ? "-" + st : "") + "】" : "";
                    return label + d.getText();
                })
                .collect(Collectors.joining("\n---\n"));

        // 步骤6: Act → 调用LLM生成回答（单轮 ReAct）
        String historyContext = memoryService.buildHistoryContext(history, summary);
        String prompt = String.format(
                "你是电商客服助手。请根据以下知识库信息回答用户问题。\n\n" +
                        "%s知识库内容：\n%s\n\n用户问题：%s\n\n" +
                        "要求：\n" +
                        "1. 如果知识库中有相关信息，请基于知识库回答\n" +
                        "2. 如果知识库中没有相关信息，请明确告知用户，不要编造\n" +
                        "3. 回答要简洁友好，使用中文",
                historyContext,
                context.isEmpty() ? "（知识库中暂无相关信息）" : context,
                message
        );

        String reply = chatClient.prompt().user(prompt).call().content();

        // 步骤7: 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("reply", reply.trim());
        data.put("sources", docs.stream().map(d -> d.getMetadata().get("source"))
                .filter(Objects::nonNull).collect(Collectors.toList()));

        log.info("KnowledgeIntentAgent.execute - 回答完成, userId: {}", userId);
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.85).build();
    }

    @Override
    protected AgentResult buildResult(String llmResponse, Map<String, Object> params) {
        // 默认实现（由 execute() 覆盖时不会调用至此）
        return AgentResult.builder().agentName(name).success(true)
                .data(Map.of("reply", llmResponse)).confidence(1.0).build();
    }

    // ==================== 私有方法 ====================

    private String rewriteQuery(String query, List<String> history, String summary) {
        try {
            String rewritten = queryRewriteService.rewriteWithContext(query, history, summary);
            if (org.apache.commons.lang3.StringUtils.isNotBlank(rewritten)) {
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("KnowledgeIntentAgent.rewriteQuery - 改写失败", e);
        }
        return query;
    }

    private ClassifyResultDTO classifyQuery(String query, List<String> history) {
        try {
            ClassifyResultDTO result = knowledgeClassifyService.classify(query, history);
            if (result == null || result.getCategories() == null || result.getCategories().isEmpty()) {
                log.info("KnowledgeIntentAgent.classifyQuery - 分类结果为空，将不过滤检索");
                return ClassifyResultDTO.builder()
                        .categories(Collections.emptyList())
                        .isCrossCategory(false)
                        .confidence(0.0)
                        .build();
            }
            return result;
        } catch (Exception e) {
            log.warn("KnowledgeIntentAgent.classifyQuery - 分类失败，将不过滤检索", e);
            return ClassifyResultDTO.builder()
                    .categories(Collections.emptyList())
                    .isCrossCategory(false)
                    .confidence(0.0)
                    .build();
        }
    }

    private List<Document> searchWithClassification(String query, ClassifyResultDTO classifyResult) {
        List<CategoryHit> categories = classifyResult.getCategories();
        if (categories == null || categories.isEmpty()) {
            log.info("KnowledgeIntentAgent.searchWithClassification - 未分类，退化为知识库全量检索");
            return documentVectorService.searchKnowledgeBase(query, 3);
        }

        // 按 knowledge_type 分组
        Map<String, List<String>> groupedByType = new LinkedHashMap<>();
        for (CategoryHit hit : categories) {
            groupedByType.computeIfAbsent(hit.getKnowledgeType(), k -> new ArrayList<>())
                    .add(hit.getSubType());
        }

        if (!classifyResult.isCrossCategory()) {
            // 同大类多个子类：合并所有子类一起检索
            String knowledgeType = groupedByType.keySet().iterator().next();
            List<String> subTypes = groupedByType.get(knowledgeType);
            log.info("KnowledgeIntentAgent.searchWithClassification - 同大类检索, knowledgeType: {}, subTypes: {}",
                    knowledgeType, subTypes);
            return documentVectorService.searchKnowledgeBase(query, 5, List.of(knowledgeType), subTypes);
        }

        // 跨大类：分别检索，合并去重
        log.info("KnowledgeIntentAgent.searchWithClassification - 跨大类检索, groups: {}", groupedByType);
        Set<String> seenIds = new HashSet<>();
        List<Document> merged = new ArrayList<>();
        int perGroupTopK = Math.max(2, 5 / groupedByType.size());

        for (Map.Entry<String, List<String>> entry : groupedByType.entrySet()) {
            List<Document> groupDocs = documentVectorService.searchKnowledgeBase(
                    query, perGroupTopK, List.of(entry.getKey()), entry.getValue());
            for (Document doc : groupDocs) {
                if (seenIds.add(doc.getId())) {
                    merged.add(doc);
                }
            }
        }

        return merged.size() > 5 ? merged.subList(0, 5) : merged;
    }
}
