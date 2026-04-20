package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.ConversationSession;

import java.util.List;

/**
 * 对话会话服务接口
 */
public interface ConversationSessionService extends IService<ConversationSession> {

    /**
     * 根据会话ID查询
     */
    ConversationSession getBySessionId(String sessionId);

    /**
     * 查询用户的进行中的会话
     */
    List<ConversationSession> listActiveByUserId(String userId);

    /**
     * 查询用户最近的会话
     */
    List<ConversationSession> listRecentByUserId(String userId, int limit);

    /**
     * 创建新会话
     */
    ConversationSession createSession(String userId);

    /**
     * 结束会话
     */
    boolean endSession(String sessionId);
}
