package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话结束结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEndVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 会话ID */
    private String sessionId;
    
    /** 是否成功 */
    private Boolean success;
}
