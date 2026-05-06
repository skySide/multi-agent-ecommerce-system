package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI回复反馈实体类
 * 用于记录用户对AI回复的点赞/点踩
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_feedback")
public class ChatFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private String userId;

    /** 会话ID */
    private String sessionId;

    /** 消息索引位置 */
    private Integer messageIndex;

    /** 用户消息 */
    private String userMessage;

    /** AI回复内容 */
    private String aiMessage;

    /** 评分: 1-赞, -1-踩, 0-未评价 */
    private Integer rating;

    /** 反馈时间 */
    private LocalDateTime feedbackTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
