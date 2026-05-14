package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话摘要VO
 * 用于会话列表展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 对话摘要 */
    private String summary;

    /** 状态: 0-结束, 1-进行中 */
    private Integer status;

    /** 消息轮数 */
    private Integer roundCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
