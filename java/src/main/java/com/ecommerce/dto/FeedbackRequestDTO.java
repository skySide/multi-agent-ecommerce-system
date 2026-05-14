package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI反馈请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 评分: 1-赞, -1-踩 */
    private Integer rating;

    /** 反馈原因标签，多选用逗号分隔 */
    private String feedbackReason;

    /** 用户自由填写的反馈内容 */
    private String feedbackComment;
}
