package com.ecommerce.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {

    /** 成功 */
    SUCCESS(200, "操作成功"),
    
    /** 参数错误 */
    PARAM_ERROR(400, "参数错误"),
    
    /** 未授权 */
    UNAUTHORIZED(401, "未授权"),
    
    /** 禁止访问 */
    FORBIDDEN(403, "禁止访问"),
    
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    
    /** 系统内部错误 */
    SYSTEM_ERROR(500, "系统内部错误"),
    
    /** 推荐服务异常 */
    RECOMMEND_ERROR(5001, "推荐服务异常"),
    
    /** 对话服务异常 */
    CONVERSATION_ERROR(5002, "对话服务异常"),
    
    /** 行为记录异常 */
    BEHAVIOR_ERROR(5003, "行为记录异常"),
    
    /** 商品服务异常 */
    PRODUCT_ERROR(5004, "商品服务异常"),
    
    /** 用户服务异常 */
    USER_ERROR(5005, "用户服务异常");

    /** 错误码 */
    private final Integer code;

    /** 错误信息 */
    private final String message;
}