package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品创建结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 商品ID */
    private String productId;
}
