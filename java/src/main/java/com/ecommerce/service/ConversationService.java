package com.ecommerce.service;

import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;
import com.ecommerce.vo.SessionSummaryVO;

/**
 * 对话服务接口
 * 负责多轮对话管理、意图识别、对话式推荐
 */
public interface ConversationService {

    /**
     * 处理对话消息
     */
    ConversationResponse chat(ConversationRequest request);

    /**
     * 创建新会话
     */
    String createSession(String userId);

    /**
     * 获取会话历史
     */
    java.util.List<String> getSessionHistory(String sessionId);

    /**
     * 结束会话
     */
    boolean endSession(String sessionId);

    /**
     * 获取用户的会话列表
     *
     * @param userId 用户ID
     * @return 会话摘要列表
     */
    java.util.List<SessionSummaryVO> listUserSessions(String userId);

    /**
     * 取消正在生成的会话
     *
     * @param sessionId 会话ID
     * @return 是否成功取消
     */
    boolean cancelGeneration(String sessionId);
}
