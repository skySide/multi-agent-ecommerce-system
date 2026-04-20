package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String productId;

    private String productName;

    private String categoryId;

    private String categoryName;

    private String brand;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String productDescription;

    private String mainImage;

    private String images;

    private Integer stock;

    private Integer salesCount;

    private BigDecimal rating;

    private Integer productStatus;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 生成用于 Embedding 的文本
     */
    public String toEmbeddingText() {
        return String.format("%s %s %s %s",
                productName,
                categoryName,
                brand,
                productDescription
        );
    }
}
