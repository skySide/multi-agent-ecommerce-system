package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.SessionQualityMetrics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话质量指标服务接口
 * 负责记录和统计会话质量事件
 */
public interface SessionQualityMetricsService extends IService<SessionQualityMetrics> {

    /**
     * 记录质量事件
     *
     * @param sessionId    会话ID
     * @param userId       用户ID
     * @param metricType   指标类型
     * @param metricValue  指标详情JSON
     * @param messageIndex 消息索引位置，用于定位问题轮次，可为null
     */
    void recordMetric(String sessionId, String userId, String metricType, String metricValue, Integer messageIndex);

    /**
     * 记录重复提问事件
     *
     * @param sessionId       会话ID
     * @param userId          用户ID
     * @param metricValueJson 指标详情JSON
     * @param messageIndex    消息索引位置，用于定位问题轮次，可为null
     */
    void recordRepeatedQuestion(String sessionId, String userId, String metricValueJson, Integer messageIndex);

    /**
     * 记录会话突然结束事件
     *
     * @param sessionId       会话ID
     * @param userId          用户ID
     * @param metricValueJson 指标详情JSON
     * @param messageIndex    消息索引位置，用于定位问题轮次，可为null
     */
    void recordAbruptEnd(String sessionId, String userId, String metricValueJson, Integer messageIndex);

    /**
     * 记录转人工事件
     *
     * @param sessionId       会话ID
     * @param userId          用户ID
     * @param metricValueJson 指标详情JSON
     * @param messageIndex    消息索引位置，用于定位问题轮次，可为null
     */
    void recordTransferToHuman(String sessionId, String userId, String metricValueJson, Integer messageIndex);

    /**
     * 按时间范围和指标类型列表查询质量事件
     *
     * @param start       开始时间
     * @param end         结束时间
     * @param metricTypes 指标类型列表（如 ["abrupt_end", "repeated_question"]）
     * @return 质量事件列表
     */
    List<SessionQualityMetrics> listByTimeRangeAndTypes(LocalDateTime start, LocalDateTime end, List<String> metricTypes);
}
