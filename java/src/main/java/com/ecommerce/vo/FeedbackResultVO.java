package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 反馈提交结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResultVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 提示消息 */
    private String message;
    
    /** 评分（1=点赞，-1=点踩） */
    private Integer rating;
}
