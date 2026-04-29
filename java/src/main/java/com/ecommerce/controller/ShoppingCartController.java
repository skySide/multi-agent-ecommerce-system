package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.service.ShoppingCartService;
import com.ecommerce.service.UserBehaviorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 购物车 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cart")
public class ShoppingCartController {

    @Resource
    private ShoppingCartService shoppingCartService;
    @Resource
    private UserBehaviorService userBehaviorService;

    /** 查询购物车 */
    @GetMapping("/{userId}")
    public Result<List<Map<String, Object>>> getCart(@PathVariable String userId) {
        return Result.success(shoppingCartService.getCartWithProducts(userId));
    }

    /** 添加到购物车 */
    @PostMapping("/add")
    public Result<Map<String, Object>> addToCart(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String productId = body.get("productId");
        boolean success = shoppingCartService.addToCart(userId, productId);
        // 同时记录行为
        try {
            userBehaviorService.recordBehavior(userId, productId, "cart", null, "cart_page");
        } catch (Exception e) {
            log.warn("记录购物车行为失败: {}", e.getMessage());
        }
        return Result.success(Map.of("success", success, "inCart", true));
    }

    /** 更新数量 */
    @PutMapping("/quantity")
    public Result<Map<String, Object>> updateQuantity(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        String productId = (String) body.get("productId");
        int quantity = ((Number) body.get("quantity")).intValue();
        boolean success = shoppingCartService.updateQuantity(userId, productId, quantity);
        return Result.success(Map.of("success", success));
    }

    /** 移除单项 */
    @DeleteMapping("/remove")
    public Result<Map<String, Object>> removeItem(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String productId = body.get("productId");
        boolean success = shoppingCartService.removeItem(userId, productId);
        return Result.success(Map.of("success", success, "inCart", false));
    }

    /** 清空购物车 */
    @DeleteMapping("/clear/{userId}")
    public Result<Map<String, Object>> clearCart(@PathVariable String userId) {
        boolean success = shoppingCartService.clearCart(userId);
        return Result.success(Map.of("success", success));
    }

    /** 检查是否在购物车 */
    @GetMapping("/check")
    public Result<Map<String, Object>> checkInCart(@RequestParam String userId, @RequestParam String productId) {
        boolean inCart = shoppingCartService.isInCart(userId, productId);
        return Result.success(Map.of("inCart", inCart));
    }
}
