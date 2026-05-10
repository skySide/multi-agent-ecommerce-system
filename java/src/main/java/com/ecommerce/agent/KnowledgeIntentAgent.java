package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.service.DocumentVectorService;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.QueryRewriteService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识问答意图Agent
 * Query改写 + RAG向量搜索知识库 + LLM回复生成
 */
@Component
public class KnowledgeIntentAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private DocumentVectorService documentVectorService;

    @Resource
    private QueryRewriteService queryRewriteService;

    @Resource
    private MemoryService memoryService;

    public KnowledgeIntentAgent() {
        super("knowledge_intent", 8.0, 2);
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

        // 步骤2: Query改写（结合对话历史和摘要，降低幻觉）
        String rewrittenQuery = rewriteQuery(message, history, summary);

        // 步骤3: RAG向量搜索知识库
        List<Document> docs = documentVectorService.searchKnowledgeBase(rewrittenQuery, 3);

        // 步骤4: 构建知识库上下文
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 步骤5: 构建历史上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);

        // 步骤6: 调用LLM生成回答
        String prompt = String.format(
                "你是电商客服助手。请根据以下信息回答用户问题。\n\n" +
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
        data.put("sources", docs.stream().map(d -> d.getMetadata().get("source")).filter(Objects::nonNull).collect(Collectors.toList()));

        log.info("KnowledgeIntentAgent.execute - 回答完成, userId: {}", userId);
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.85).build();
    }

    private String rewriteQuery(String query, List<String> history, String summary) {
        try {
            String rewritten = queryRewriteService.rewriteWithContext(query, history, summary);
            if (org.apache.commons.lang3.StringUtils.isNotBlank(rewritten)) {
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("KnowledgeIntentAgent.rewriteQuery 改写失败", e);
        }
        return query;
    }
}
