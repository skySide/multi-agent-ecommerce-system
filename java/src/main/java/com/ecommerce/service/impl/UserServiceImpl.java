package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.dto.LoginRequestDTO;
import com.ecommerce.dto.UserCreateDTO;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.UserService;
import com.ecommerce.util.JwtUtil;
import com.ecommerce.vo.UserAuthVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User getByUserId(String userId) {
        return getBaseMapper().findByUserId(userId);
    }

    @Override
    public User getByUserIdIncludeDeleted(String userId, Integer isDeleted) {
        return getBaseMapper().findByUserIdAndIsDeleted(userId, isDeleted);
    }

    @Override
    public boolean checkUserExists(String userId) {
        return getBaseMapper().existsByUserId(userId);
    }

    /**
     * 根据用户名查询（使用 MyBatis-Plus lambdaQuery）
     */
    @Override
    public User getByUsername(String username) {
        return lambdaQuery()
                .eq(User::getUsername, username)
                .eq(User::getIsDeleted, 0)
                .one();
    }

    @Override
    public UserAuthVO register(UserCreateDTO dto) {
        log.info("UserServiceImpl.register 用户名={}", dto.getUsername());

        // 检查用户名是否已存在
        User existUser = getByUsername(dto.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        String userId = generateUserId();
        User user = User.builder()
                .userId(userId)
                .username(dto.getUsername())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .avatarUrl(dto.getAvatarUrl())
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();

        boolean success = save(user);
        if (!success) {
            throw new RuntimeException("注册失败");
        }

        String token = jwtUtil.generateToken(userId, dto.getUsername());
        log.info("UserServiceImpl.register 成功 userId={}", userId);

        return UserAuthVO.builder()
                .userId(userId)
                .username(dto.getUsername())
                .token(token)
                .build();
    }

    @Override
    public UserAuthVO login(LoginRequestDTO dto) {
        log.info("UserServiceImpl.login 用户名={}", dto.getUsername());

        User user = getByUsername(dto.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername());
        log.info("UserServiceImpl.login 成功 userId={}", user.getUserId());

        return UserAuthVO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .token(token)
                .build();
    }

    private String generateUserId() {
        return "U" + System.currentTimeMillis();
    }
}
