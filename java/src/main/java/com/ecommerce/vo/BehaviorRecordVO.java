package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 行为记录结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorRecordVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 用户ID */
    private String userId;
    
    /** 行为类型 */
    private String behaviorType;
    
    /** 是否成功 */
    private Boolean success;
}
