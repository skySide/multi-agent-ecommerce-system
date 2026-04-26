package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.dto.LoginRequestDTO;
import com.ecommerce.dto.UserCreateDTO;
import com.ecommerce.entity.User;
import com.ecommerce.vo.UserAuthVO;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户ID查询
     */
    User getByUserId(String userId);

    /**
     * 根据用户ID查询（包含已删除）
     */
    User getByUserIdIncludeDeleted(String userId, Integer isDeleted);

    /**
     * 检查用户是否存在
     */
    boolean checkUserExists(String userId);

    /**
     * 根据用户名查询（使用 QueryWrapper）
     */
    User getByUsername(String username);

    /**
     * 用户注册
     */
    UserAuthVO register(UserCreateDTO dto);

    /**
     * 用户登录
     */
    UserAuthVO login(LoginRequestDTO dto);
}
