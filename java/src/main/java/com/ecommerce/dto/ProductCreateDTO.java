package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品创建DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateDTO {

    /** 序列化ID */
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "商品名称不能为空")
    private String productName;

    @NotBlank(message = "商品描述不能为空")
    private String productDescription;

    @Min(value = 0, message = "价格不能小于0")
    private BigDecimal price;

    @Min(value = 0, message = "原价不能小于0")
    private BigDecimal originalPrice;

    @Min(value = 0, message = "库存不能小于0")
    private int stock;

    private int salesCount;

    @Min(value = 0, message = "评分不能小于0")
    private BigDecimal rating;

    private String brand;
    private String categoryId;
    private String categoryName;
    private String mainImage;
    private List<String> images;
    private int productStatus;
}