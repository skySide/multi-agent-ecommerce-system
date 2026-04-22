package com.ecommerce.controller;

import com.ecommerce.entity.User;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.UserProfileService;
import com.ecommerce.service.UserService;
import jakarta.annotation.Resource;
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
    public User getUser(@PathVariable String userId) {
        log.info("UserController.getUser userId={}", userId);
        return userService.getByUserId(userId);
    }

    /**
     * 获取用户画像
     */
    @GetMapping("/{userId}/profile")
    public UserProfile getUserProfile(@PathVariable String userId) {
        log.info("UserController.getUserProfile userId={}", userId);
        return userProfileService.getByUserId(userId);
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Map<String, Object> createUser(@RequestBody User user) {
        boolean success = userService.save(user);
        log.info("UserController.createUser 结果={} userId={}", success, user.getUserId());
        return Map.of("success", success, "userId", user.getUserId());
    }
}
