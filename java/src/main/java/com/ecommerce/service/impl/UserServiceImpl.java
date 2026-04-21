package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User getByUserId(String userId) {
        return userMapper.findByUserId(userId);
    }

    @Override
    public User getByUserIdIncludeDeleted(String userId, Integer isDeleted) {
        return userMapper.findByUserIdAndIsDeleted(userId, isDeleted);
    }

    @Override
    public boolean checkUserExists(String userId) {
        return userMapper.existsByUserId(userId);
    }
}
