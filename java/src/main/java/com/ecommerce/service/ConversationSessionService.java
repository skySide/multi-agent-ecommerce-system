package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.ConversationSession;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对话会话服务接口
 */
public interface ConversationSessionService extends IService<ConversationSession> {

    /**
     * 根据会话ID查询
     */
    ConversationSession getBySessionId(String sessionId);

    /**
     * 批量根据会话ID查询
     *
     * @param sessionIdSet 会话ID集合
     * @return sessionId → ConversationSession 的Map
     */
    Map<String, ConversationSession> batchGetBySessionIds(Set<String> sessionIdSet);

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

    /**
     * 根据会话和消息索引解析对应的Agent名称
     * 通过 round_intents 找到 messageIndex 对应轮次的意图，即为Agent名称
     *
     * @param session      会话记录
     * @param messageIndex 消息在对话历史中的索引（来自 chat_feedback.message_index）
     * @return Agent名称（如 recommend/chitchat）
     */
    String resolveAgentByMessageIndex(ConversationSession session, Integer messageIndex);

    /**
     * 根据会话解析对应的Agent名称
     * 取会话最后一轮的意图作为Agent名称，用于指标数据关联
     *
     * @param session 会话记录
     * @return Agent名称
     */
    String resolveAgent(ConversationSession session);
}
