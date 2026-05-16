package com.ecommerce.common;

import com.ecommerce.common.enums.ErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一API返回结果封装
 * @param <T> 数据泛型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /** 状态码 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 数据载体 */
    private T data;
    /** 时间戳 */
    private long timestamp;

    /**
     * 成功返回结果
     * @param data 返回数据
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCodeEnum.SUCCESS.getCode(), ErrorCodeEnum.SUCCESS.getMessage(), data, System.currentTimeMillis());
    }

    /**
     * 成功返回结果
     * @param message 提示信息
     * @param data 返回数据
     * @return 成功结果
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ErrorCodeEnum.SUCCESS.getCode(), message, data, System.currentTimeMillis());
    }

    /**
     * 失败返回结果
     * @param errorCodeEnum 错误码枚举
     * @return 失败结果
     */
    public static <T> Result<T> error(ErrorCodeEnum errorCodeEnum) {
        return new Result<>(errorCodeEnum.getCode(), errorCodeEnum.getMessage(), null, System.currentTimeMillis());
    }

    /**
     * 失败返回结果（自定义错误信息）
     * @param errorCodeEnum 错误码枚举
     * @param message 错误信息
     * @return 失败结果
     */
    public static <T> Result<T> error(ErrorCodeEnum errorCodeEnum, String message) {
        return new Result<>(errorCodeEnum.getCode(), message, null, System.currentTimeMillis());
    }

    /**
     * 失败返回结果（自定义错误信息）
     * @param code 错误码
     * @param message 错误信息
     * @return 失败结果
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }

    /**
     * 失败返回结果
     * @param message 错误信息
     * @return 失败结果
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ErrorCodeEnum.SYSTEM_ERROR.getCode(), message, null, System.currentTimeMillis());
    }

    /**
     * 400错误返回结果
     * @param message 错误信息
     * @return 失败结果
     */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(ErrorCodeEnum.PARAM_ERROR.getCode(), message, null, System.currentTimeMillis());
    }

    /**
     * 401错误返回结果
     * @param message 错误信息
     * @return 失败结果
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(ErrorCodeEnum.UNAUTHORIZED.getCode(), message, null, System.currentTimeMillis());
    }

    /**
     * 403错误返回结果
     * @param message 错误信息
     * @return 失败结果
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(ErrorCodeEnum.FORBIDDEN.getCode(), message, null, System.currentTimeMillis());
    }

    /**
     * 404错误返回结果
     * @param message 错误信息
     * @return 失败结果
     */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(ErrorCodeEnum.NOT_FOUND.getCode(), message, null, System.currentTimeMillis());
    }
}