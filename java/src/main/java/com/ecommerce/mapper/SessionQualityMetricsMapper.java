package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.SessionQualityMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 会话质量指标 Mapper
 */
@Mapper
public interface SessionQualityMetricsMapper extends BaseMapper<SessionQualityMetrics> {

    /**
     * 按指标类型分组统计
     */
    @Select("SELECT metric_type, COUNT(*) as count FROM session_quality_metrics " +
            "GROUP BY metric_type")
    List<Map<String, Object>> countByMetricType();

    /**
     * 统计某会话的质量指标
     */
    @Select("SELECT metric_type, COUNT(*) as count FROM session_quality_metrics " +
            "WHERE session_id = #{sessionId} GROUP BY metric_type")
    List<Map<String, Object>> countBySessionId(String sessionId);

    /**
     * 统计某用户的质量指标
     */
    @Select("SELECT metric_type, COUNT(*) as count FROM session_quality_metrics " +
            "WHERE user_id = #{userId} GROUP BY metric_type")
    List<Map<String, Object>> countByUserId(String userId);
}
