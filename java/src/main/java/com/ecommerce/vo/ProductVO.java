package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO implements Serializable {

    /** 序列化ID */
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private String productId;

    /** 商品名称 */
    private String productName;

    /** 商品描述 */
    private String productDescription;

    /** 价格 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 库存 */
    private int stock;

    /** 销量 */
    private int salesCount;

    /** 评分 */
    private BigDecimal rating;

    /** 品牌 */
    private String brand;

    /** 类目ID */
    private String categoryId;

    /** 类目名称 */
    private String categoryName;

    /** 主图 */
    private String mainImage;

    /** 图片列表 */
    private List<String> images;

    /** 商品状态 */
    private int productStatus;

    /** 当前用户是否已收藏 */
    @Builder.Default
    private boolean favorited = false;

    /** 当前用户是否已加入购物车 */
    @Builder.Default
    private boolean inCart = false;
}