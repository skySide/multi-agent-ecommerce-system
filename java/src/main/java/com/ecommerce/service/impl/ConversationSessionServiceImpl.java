package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.ConversationSession;
import com.ecommerce.mapper.ConversationSessionMapper;
import com.ecommerce.service.ConversationSessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话会话服务实现类
 */
@Slf4j
@Service
public class ConversationSessionServiceImpl extends ServiceImpl<ConversationSessionMapper, ConversationSession> implements ConversationSessionService {

    @Resource
    private ConversationSessionMapper conversationSessionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 默认Agent名称，解析失败时返回 */
    private static final String DEFAULT_AGENT = "chitchat";

    @Override
    public ConversationSession getBySessionId(String sessionId) {
        return conversationSessionMapper.findBySessionId(sessionId);
    }

    @Override
    public List<ConversationSession> listActiveByUserId(String userId) {
        return conversationSessionMapper.findActiveByUserId(userId);
    }

    @Override
    public List<ConversationSession> listRecentByUserId(String userId, int limit) {
        return conversationSessionMapper.findRecentByUserId(userId, limit);
    }

    @Override
    public ConversationSession createSession(String userId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ConversationSession session = ConversationSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(1)
                .dialogueHistory("[]")
                .summary("")
                .extractedInfo("{}")
                .roundIntents("[]")
                .build();
        save(session);
        return session;
    }

    @Override
    public boolean endSession(String sessionId) {
        ConversationSession session = getBySessionId(sessionId);
        if (session != null) {
            session.setStatus(0);
            return updateById(session);
        }
        return false;
    }

    @Override
    public List<ConversationSession> findSessionsNeedingSummary(int threshold) {
        // 查询进行中且对话历史不为空的会话，然后在内存中过滤轮数
        List<ConversationSession> activeSessions = lambdaQuery()
                .eq(ConversationSession::getStatus, 1)
                .isNotNull(ConversationSession::getDialogueHistory)
                .list();

        // 过滤出对话轮数超过阈值的会话（每轮包含用户和助手两条消息）
        int messageThreshold = threshold * 2;
        return activeSessions.stream()
                .filter(session -> {
                    String history = session.getDialogueHistory();
                    if (history == null || history.isEmpty() || history.equals("[]")) {
                        return false;
                    }
                    // 简单计算消息条数（通过逗号数量估算）
                    int messageCount = countMessages(history);
                    return messageCount > messageThreshold;
                })
                .toList();
    }

    /**
     * 计算对话历史中的消息条数
     */
    private int countMessages(String historyJson) {
        if (historyJson == null || historyJson.isEmpty()) {
            return 0;
        }
        // 计算包含 "用户:" 或 "助手:" 的数量
        int count = 0;
        int index = 0;
        while ((index = historyJson.indexOf("用户:", index)) != -1) {
            count++;
            index++;
        }
        index = 0;
        while ((index = historyJson.indexOf("助手:", index)) != -1) {
            count++;
            index++;
        }
        return count;
    }

    @Override
    public Map<String, ConversationSession> batchGetBySessionIds(Set<String> sessionIdSet) {
        // 步骤1: 参数校验
        if (sessionIdSet == null || sessionIdSet.isEmpty()) {
            return Collections.emptyMap();
        }

        // 步骤2: 批量查询
        List<ConversationSession> sessions = lambdaQuery()
                .in(ConversationSession::getSessionId, sessionIdSet)
                .list();

        // 步骤3: 转为 Map
        return sessions.stream()
                .collect(Collectors.toMap(ConversationSession::getSessionId, s -> s, (a, b) -> a));
    }

    @Override
    public String resolveAgentByMessageIndex(ConversationSession session, Integer messageIndex) {
        // 步骤1: 校验参数
        if (session == null || session.getRoundIntents() == null || session.getRoundIntents().isEmpty()) {
            return DEFAULT_AGENT;
        }

        try {
            // 步骤2: 解析 round_intents JSON
            List<Map<String, Object>> roundIntents = objectMapper.readValue(
                    session.getRoundIntents(), new TypeReference<>() {}
            );
            if (roundIntents.isEmpty()) {
                return DEFAULT_AGENT;
            }

            // 步骤3: 确定目标轮次
            // 关联公式：round = (messageIndex - 1) / 2
            // 对话历史中 index=0 为欢迎语，index=1起每2条为一轮（用户+助手）
            int targetRound;
            if (messageIndex != null && messageIndex > 0) {
                targetRound = (messageIndex - 1) / 2;
            } else {
                targetRound = roundIntents.size() - 1;
            }

            // 步骤4: 找到最近不超过 targetRound 的意图记录
            String intent = DEFAULT_AGENT;
            for (Map<String, Object> ri : roundIntents) {
                Object roundObj = ri.get("round");
                int round = roundObj instanceof Number ? ((Number) roundObj).intValue() : 0;
                if (round <= targetRound) {
                    Object intentObj = ri.get("intent");
                    if (intentObj instanceof String) {
                        intent = (String) intentObj;
                    }
                }
            }

            // 步骤5: 转人工意图归入默认Agent
            if ("transfer_to_human".equals(intent)) {
                return DEFAULT_AGENT;
            }
            return intent;
        } catch (Exception e) {
            log.warn("ConversationSessionService.resolveAgentByMessageIndex - 解析失败, sessionId: {}",
                    session.getSessionId(), e);
            return DEFAULT_AGENT;
        }
    }

    @Override
    public String resolveAgent(ConversationSession session) {
        // 步骤1: 取最后一轮的意图，委托 resolveAgentByMessageIndex
        return resolveAgentByMessageIndex(session, null);
    }

    @Override
    public Integer resolveLatestMessageIndex(ConversationSession session) {
        // 步骤1: 校验参数
        if (session == null || session.getRoundIntents() == null || session.getRoundIntents().isEmpty()) {
            return null;
        }

        try {
            // 步骤2: 解析 round_intents JSON，找最大 round
            List<Map<String, Object>> roundIntents = objectMapper.readValue(
                    session.getRoundIntents(), new TypeReference<>() {}
            );
            if (roundIntents.isEmpty()) {
                return null;
            }

            // 步骤3: messageIndex = (最新round) * 2 + 1，即 size * 2 - 1
            return roundIntents.size() * 2 - 1;
        } catch (Exception e) {
            log.warn("ConversationSessionService.resolveLatestMessageIndex - 解析失败, sessionId: {}",
                    session.getSessionId(), e);
            return null;
        }
    }
}
