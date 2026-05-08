package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 收藏操作结果VO
 * 用于添加/取消收藏操作的返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteOperationVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 操作是否成功 */
    private Boolean success;
    
    /** 当前收藏状态 */
    private Boolean favorited;
    
    /** 提示消息 */
    private String message;
}
