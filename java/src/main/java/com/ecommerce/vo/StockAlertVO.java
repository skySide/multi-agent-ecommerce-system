package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存预警VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 商品ID */
    private String productId;
    
    /** 商品名称 */
    private String name;
    
    /** 当前库存 */
    private Integer currentStock;
    
    /** 预警级别：critical(临界), warning(警告) */
    private String level;
    
    /** 建议操作：urgent_restock(紧急补货), plan_restock(计划补货) */
    private String action;
}
