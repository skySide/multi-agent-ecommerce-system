package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 营销文案VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingCopyVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 商品ID */
    private String productId;
    
    /** 营销文案 */
    private String copy;
}
