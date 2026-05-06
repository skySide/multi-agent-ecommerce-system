package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.ChatFeedback;

import java.util.Map;

/**
 * AI回复反馈服务接口
 */
public interface ChatFeedbackService extends IService<ChatFeedback> {

    /**
     * 提交反馈
     *
     * @param userId       用户ID
     * @param sessionId    会话ID
     * @param messageIndex 消息索引
     * @param userMessage  用户消息
     * @param aiMessage    AI回复
     * @param rating       评分 (1:赞, -1:踩)
     * @return 是否成功
     */
    boolean submitFeedback(String userId, String sessionId, Integer messageIndex,
                           String userMessage, String aiMessage, Integer rating);

    /**
     * 获取满意度统计
     *
     * @return 统计结果
     */
    Map<String, Object> getSatisfactionStats();

    /**
     * 获取用户反馈历史
     *
     * @param userId 用户ID
     * @return 反馈列表
     */
    Map<String, Object> getUserFeedbackStats(String userId);
}
