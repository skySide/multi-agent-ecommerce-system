package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 健康检查结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 状态 */
    private String status;
    
    /** 语言 */
    private String language;
    
    /** 版本 */
    private String version;
}
