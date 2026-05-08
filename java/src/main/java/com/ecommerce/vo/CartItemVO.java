package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车项VO
 * 包含购物车商品的基本信息和数量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 购物车记录ID */
    private Long cartId;
    
    /** 商品ID */
    private String productId;
    
    /** 商品名称 */
    private String productName;
    
    /** 商品价格 */
    private BigDecimal price;
    
    /** 原价 */
    private BigDecimal originalPrice;
    
    /** 主图 */
    private String mainImage;
    
    /** 品牌 */
    private String brand;
    
    /** 库存 */
    private Integer stock;
    
    /** 数量 */
    private Integer quantity;
    
    /** 加入购物车时间 */
    private LocalDateTime addedTime;
}
