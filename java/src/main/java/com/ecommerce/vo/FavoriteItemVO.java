package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏项VO
 * 包含收藏商品的基本信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteItemVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
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
    
    /** 评分 */
    private BigDecimal rating;
    
    /** 收藏时间 */
    private LocalDateTime favoriteTime;
}
