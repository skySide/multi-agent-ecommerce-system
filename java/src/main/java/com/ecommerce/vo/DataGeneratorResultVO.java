package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据生成器结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataGeneratorResultVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 生成/更新数量 */
    private Integer count;
    
    /** 提示消息 */
    private String message;
    
    /** 是否启用 */
    private Boolean enabled;
}
