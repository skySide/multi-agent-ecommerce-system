package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户认证返回VO（登录/注册）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthVO {
    private String userId;
    private String username;
    private String token;
}
