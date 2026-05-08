package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话创建结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreateVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 会话ID */
    private String sessionId;
    
    /** 用户ID */
    private String userId;
}
