package com.ecommerce.service;

import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;

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
}
