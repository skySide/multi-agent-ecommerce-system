package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 添加收藏请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddFavoriteDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 用户ID */
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    /** 商品ID */
    @NotBlank(message = "商品ID不能为空")
    private String productId;
}
