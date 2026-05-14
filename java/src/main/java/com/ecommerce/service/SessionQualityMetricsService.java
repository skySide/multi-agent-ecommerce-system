package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.SessionQualityMetrics;

/**
 * 会话质量指标服务接口
 * 负责记录和统计会话质量事件
 */
public interface SessionQualityMetricsService extends IService<SessionQualityMetrics> {

    /**
     * 记录质量事件
     *
     * @param sessionId   会话ID
     * @param userId      用户ID
     * @param metricType  指标类型
     * @param metricValue 指标详情JSON
     */
    void recordMetric(String sessionId, String userId, String metricType, String metricValue);

    /**
     * 记录重复提问事件
     *
     * @param sessionId  会话ID
     * @param userId     用户ID
     * @param metricValueJson 指标详情JSON
     */
    void recordRepeatedQuestion(String sessionId, String userId, String metricValueJson);

    /**
     * 记录会话突然结束事件
     *
     * @param sessionId  会话ID
     * @param userId     用户ID
     * @param metricValueJson 指标详情JSON
     */
    void recordAbruptEnd(String sessionId, String userId, String metricValueJson);

    /**
     * 记录转人工事件
     *
     * @param sessionId  会话ID
     * @param userId     用户ID
     * @param metricValueJson 指标详情JSON
     */
    void recordTransferToHuman(String sessionId, String userId, String metricValueJson);
}
