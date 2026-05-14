package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.ConversationSession;
import com.ecommerce.mapper.ConversationSessionMapper;
import com.ecommerce.service.ConversationSessionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 对话会话服务实现类
 */
@Slf4j
@Service
public class ConversationSessionServiceImpl extends ServiceImpl<ConversationSessionMapper, ConversationSession> implements ConversationSessionService {

    @Resource
    private ConversationSessionMapper conversationSessionMapper;

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
}
