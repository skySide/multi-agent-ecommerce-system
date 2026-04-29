package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 购物车实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shopping_cart")
public class ShoppingCart {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String productId;

    @Builder.Default
    private Integer quantity = 1;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
