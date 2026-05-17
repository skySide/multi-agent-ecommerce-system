package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话质量指标实体类
 * 记录会话过程中的质量事件：重复提问、突然结束、转人工等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("session_quality_metrics")
public class SessionQualityMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 指标类型: repeated_question/abrupt_end/transfer_to_human/low_engagement */
    private String metricType;

    /** 指标详情JSON */
    private String metricValue;

    /** 消息索引位置，关联chat_feedback.message_index，用于定位问题轮次 */
    private Integer messageIndex;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
