package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.service.UserBehaviorService;
import com.ecommerce.service.UserFavoriteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收藏 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    @Resource
    private UserFavoriteService userFavoriteService;
    @Resource
    private UserBehaviorService userBehaviorService;

    /** 查询收藏列表 */
    @GetMapping("/{userId}")
    public Result<List<Map<String, Object>>> getFavorites(@PathVariable String userId) {
        return Result.success(userFavoriteService.getFavoritesWithProducts(userId));
    }

    /** 添加收藏 */
    @PostMapping("/add")
    public Result<Map<String, Object>> addFavorite(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String productId = body.get("productId");
        boolean success = userFavoriteService.addFavorite(userId, productId);
        try {
            userBehaviorService.recordBehavior(userId, productId, "favorite", null, "product_detail");
        } catch (Exception e) {
            log.warn("记录收藏行为失败: {}", e.getMessage());
        }
        return Result.success(Map.of("success", success, "favorited", true));
    }

    /** 取消收藏 */
    @DeleteMapping("/remove")
    public Result<Map<String, Object>> removeFavorite(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String productId = body.get("productId");
        boolean success = userFavoriteService.removeFavorite(userId, productId);
        try {
            userBehaviorService.recordBehavior(userId, productId, "unfavorite", null, "product_detail");
        } catch (Exception e) {
            log.warn("记录取消收藏行为失败: {}", e.getMessage());
        }
        return Result.success(Map.of("success", success, "favorited", false));
    }

    /** 检查是否已收藏 */
    @GetMapping("/check")
    public Result<Map<String, Object>> checkFavorited(@RequestParam String userId, @RequestParam String productId) {
        boolean favorited = userFavoriteService.isFavorited(userId, productId);
        return Result.success(Map.of("favorited", favorited));
    }
}
