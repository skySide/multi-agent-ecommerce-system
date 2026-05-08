package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 购物车操作结果VO
 * 用于添加/更新/删除购物车操作的返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartOperationVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 操作是否成功 */
    private Boolean success;
    
    /** 当前是否在购物车中 */
    private Boolean inCart;
    
    /** 提示消息 */
    private String message;
}
