package com.ecommerce.service.impl;

import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;
import com.ecommerce.service.ConversationService;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.MemoryService;
import com.ecommerce.agent.ConversationAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 对话服务实现
 * 薄层委托 ConversationAgent 进行意图识别和子Agent路由
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ConversationAgent conversationAgent;

    @Resource
    private ConversationSessionService conversationSessionService;

    @Resource
    private MemoryService memoryService;

    @Override
    public ConversationResponse chat(ConversationRequest request) {
        // 步骤1: 提取参数
        String userId = request.getUserId();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        log.info("ConversationServiceImpl.chat - 用户={}, 会话={}, 消息={}", userId, sessionId, message);

        // 步骤2: 获取或创建会话（确保sessionId不为空）
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = createSession(userId);
        }
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null) {
            log.warn("ConversationServiceImpl.chat - 会话不存在, sessionId={}, 重新创建", sessionId);
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        } else if (!userId.equals(session.getUserId())) {
            log.warn("ConversationServiceImpl.chat - 会话归属校验失败, sessionId={} 属于 userId={}, 请求 userId={}",
                    sessionId, session.getUserId(), userId);
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        }

        // 步骤3: 获取短期记忆
        List<String> history = memoryService.deserializeHistory(session.getDialogueHistory());
        String summary = session.getSummary();

        // 步骤4: 调用 ConversationAgent 处理（sessionId 此时一定不为空）
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("sessionId", sessionId);
        params.put("message", message);
        params.put("history", history);
        params.put("summary", summary);

        AgentResult result = conversationAgent.runAsync(params).join();

        if (!result.isSuccess() || result.getData() == null) {
            log.error("ConversationServiceImpl.chat - 对话处理失败, userId: {}", userId);
            return ConversationResponse.builder()
                    .sessionId(sessionId)
                    .message("抱歉，我暂时无法处理您的请求，请稍后再试。")
                    .timestamp(java.time.Instant.now())
                    .build();
        }

        return buildResponse(result);
    }

    @Override
    public String createSession(String userId) {
        ConversationSession session = conversationSessionService.createSession(userId);
        log.info("ConversationServiceImpl.createSession - 创建会话, userId={}, sessionId={}", userId, session.getSessionId());
        return session.getSessionId();
    }

    @Override
    public List<String> getSessionHistory(String sessionId) {
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null) {
            return List.of();
        }
        return memoryService.deserializeHistory(session.getDialogueHistory());
    }

    @Override
    public boolean endSession(String sessionId) {
        log.info("ConversationServiceImpl.endSession - 结束会话, sessionId={}", sessionId);
        return conversationSessionService.endSession(sessionId);
    }

    @SuppressWarnings("unchecked")
    private ConversationResponse buildResponse(AgentResult result) {
        Map<String, Object> data = result.getData();

        String sessionId = (String) data.getOrDefault("sessionId", "");
        String reply = (String) data.getOrDefault("reply", "");
        String intent = (String) data.getOrDefault("intent", "chitchat");
        List<String> dialogueHistory = (List<String>) data.get("dialogueHistory");
        String summary = (String) data.get("summary");

        List<Product> products = Collections.emptyList();
        Object productsObj = data.get("products");
        if (productsObj instanceof List) {
            products = (List<Product>) productsObj;
        }

        Map<String, Object> extractedInfo = Collections.emptyMap();
        Object entitiesObj = data.get("entities");
        if (entitiesObj instanceof Map) {
            extractedInfo = (Map<String, Object>) entitiesObj;
        }

        return ConversationResponse.builder()
                .sessionId(sessionId)
                .message(reply)
                .intent(intent)
                .recommendedProducts(products)
                .extractedInfo(extractedInfo)
                .dialogueHistory(dialogueHistory)
                .summary(summary)
                .timestamp(java.time.Instant.now())
                .build();
    }
}
