package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.enums.ErrorCode;
import com.ecommerce.dto.UserCreateDTO;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.UserProfileService;
import com.ecommerce.service.UserService;
import com.ecommerce.vo.UserVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
     * 创建用户
     */
    @PostMapping
    public Result<Map<String, String>> createUser(@RequestBody @Valid UserCreateDTO dto) {
        log.info("UserController.createUser, user: {}", dto.getUsername());
        User user = User.builder()
                .userId(generateUserId())
                .username(dto.getUsername())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .avatarUrl(dto.getAvatarUrl())
                .build();
        boolean success = userService.save(user);
        if (success) {
            return Result.success(Map.of("userId", user.getUserId()));
        }
        return Result.error(ErrorCode.USER_ERROR, "创建用户失败");
    }

    private String generateUserId() {
        return "U" + System.currentTimeMillis();
    }
}