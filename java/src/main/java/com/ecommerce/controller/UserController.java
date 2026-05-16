package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.enums.ErrorCodeEnum;
import com.ecommerce.dto.LoginRequestDTO;
import com.ecommerce.dto.UserCreateDTO;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.UserProfileService;
import com.ecommerce.service.UserService;
import com.ecommerce.vo.UserAuthVO;
import com.ecommerce.vo.UserVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private UserProfileService userProfileService;

    /**
     * 获取用户信息
     */
    @GetMapping("/{userId}")
    public Result<UserVO> getUser(@PathVariable String userId) {
        log.info("UserController.getUser, userId: {}", userId);
        User user = userService.getByUserId(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }
        // 转换为VO
        UserVO userVO = UserVO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .registerTime(user.getRegisterTime())
                .build();
        return Result.success(userVO);
    }

    /**
     * 获取用户画像
     */
    @GetMapping("/{userId}/profile")
    public Result<UserProfile> getUserProfile(@PathVariable String userId) {
        log.info("UserController.getUserProfile, userId: {}", userId);
        UserProfile profile = userProfileService.getByUserId(userId);
        if (profile == null) {
            return Result.notFound("用户画像不存在");
        }
        return Result.success(profile);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<UserAuthVO> register(@RequestBody @Valid UserCreateDTO dto) {
        log.info("UserController.register, user: {}", dto.getUsername());
        try {
            UserAuthVO authVO = userService.register(dto);
            return Result.success(authVO);
        } catch (RuntimeException e) {
            return Result.error(ErrorCodeEnum.USER_ERROR, e.getMessage());
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<UserAuthVO> login(@RequestBody @Valid LoginRequestDTO dto) {
        log.info("UserController.login, user: {}", dto.getUsername());
        try {
            UserAuthVO authVO = userService.login(dto);
            return Result.success(authVO);
        } catch (RuntimeException e) {
            return Result.error(ErrorCodeEnum.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * 创建用户（兼容旧接口，内部调用注册逻辑）
     */
    @PostMapping
    public Result<UserAuthVO> createUser(@RequestBody @Valid UserCreateDTO dto) {
        return register(dto);
    }
}