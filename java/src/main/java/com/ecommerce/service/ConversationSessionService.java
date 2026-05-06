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

    /**
     * 查找需要摘要的会话
     * 条件：进行中且对话轮数超过阈值
     *
     * @param threshold 轮数阈值
     * @return 会话列表
     */
    List<ConversationSession> findSessionsNeedingSummary(int threshold);
}
