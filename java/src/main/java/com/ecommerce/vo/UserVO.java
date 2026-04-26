package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO implements Serializable {

    /** 序列化ID */
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 手机 */
    private String phone;

    /** 头像URL */
    private String avatarUrl;

    /** 注册时间 */
    private LocalDateTime registerTime;
}