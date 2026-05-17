package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.constants.QualityConstants;
import com.ecommerce.entity.SessionQualityMetrics;
import com.ecommerce.mapper.SessionQualityMetricsMapper;
import com.ecommerce.service.SessionQualityMetricsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 会话质量指标服务实现类
 * 负责记录和统计会话质量事件
 */
@Slf4j
@Service
public class SessionQualityMetricsServiceImpl extends ServiceImpl<SessionQualityMetricsMapper, SessionQualityMetrics>
        implements SessionQualityMetricsService {

    @Resource
    private SessionQualityMetricsMapper sessionQualityMetricsMapper;

    @Override
    public void recordMetric(String sessionId, String userId, String metricType, String metricValue, Integer messageIndex) {
        // 步骤1: 参数校验
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(metricType)) {
            log.warn("SessionQualityMetricsService.recordMetric - 参数不完整, sessionId: {}, metricType: {}",
                    sessionId, metricType);
            return;
        }

        // 步骤2: 构建实体并保存
        try {
            SessionQualityMetrics metric = SessionQualityMetrics.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .metricType(metricType)
                    .metricValue(metricValue)
                    .messageIndex(messageIndex)
                    .build();
            save(metric);
            log.info("SessionQualityMetricsService.recordMetric - 质量事件记录成功, sessionId: {}, metricType: {}, messageIndex: {}",
                    sessionId, metricType, messageIndex);
        } catch (Exception e) {
            log.error("SessionQualityMetricsService.recordMetric - 质量事件记录失败, sessionId: {}, metricType: {}",
                    sessionId, metricType, e);
        }
    }

    @Override
    public void recordRepeatedQuestion(String sessionId, String userId, String metricValueJson, Integer messageIndex) {
        // 步骤1: 记录重复提问事件
        recordMetric(sessionId, userId, QualityConstants.METRIC_REPEATED_QUESTION, metricValueJson, messageIndex);
    }

    @Override
    public void recordAbruptEnd(String sessionId, String userId, String metricValueJson, Integer messageIndex) {
        // 步骤1: 记录会话突然结束事件
        recordMetric(sessionId, userId, QualityConstants.METRIC_ABRUPT_END, metricValueJson, messageIndex);
    }

    @Override
    public void recordTransferToHuman(String sessionId, String userId, String metricValueJson, Integer messageIndex) {
        // 步骤1: 记录转人工事件
        recordMetric(sessionId, userId, QualityConstants.METRIC_TRANSFER_TO_HUMAN, metricValueJson, messageIndex);
    }

    @Override
    public List<SessionQualityMetrics> listByTimeRangeAndTypes(LocalDateTime start, LocalDateTime end,
                                                                List<String> metricTypes) {
        // 步骤1: 参数校验
        if (start == null || end == null || metricTypes == null || metricTypes.isEmpty()) {
            log.warn("SessionQualityMetricsService.listByTimeRangeAndTypes - 参数不完整");
            return Collections.emptyList();
        }

        // 步骤2: 按时间范围和指标类型查询
        List<SessionQualityMetrics> result = lambdaQuery()
                .ge(SessionQualityMetrics::getCreateTime, start)
                .lt(SessionQualityMetrics::getCreateTime, end)
                .in(SessionQualityMetrics::getMetricType, metricTypes)
                .list();

        log.info("SessionQualityMetricsService.listByTimeRangeAndTypes - 查询完成, 记录数: {}", result.size());
        return result;
    }
}
