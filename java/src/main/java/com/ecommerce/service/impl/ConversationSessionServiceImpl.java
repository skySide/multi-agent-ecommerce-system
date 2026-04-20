package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.ConversationSession;
import com.ecommerce.mapper.ConversationSessionMapper;
import com.ecommerce.service.ConversationSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 对话会话服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSessionServiceImpl extends ServiceImpl<ConversationSessionMapper, ConversationSession> implements ConversationSessionService {

    private final ConversationSessionMapper conversationSessionMapper;

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
                .extractedInfo("{}")
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
}
