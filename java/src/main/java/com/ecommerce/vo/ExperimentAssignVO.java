package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 实验分配结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentAssignVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 实验ID */
    private String experimentId;
    
    /** 分配的组 */
    private String group;
    
    /** 用户ID */
    private String userId;
}
