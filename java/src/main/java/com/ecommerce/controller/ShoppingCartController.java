package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.AddToCartDTO;
import com.ecommerce.dto.RemoveCartItemDTO;
import com.ecommerce.dto.UpdateCartQuantityDTO;
import com.ecommerce.service.ShoppingCartService;
import com.ecommerce.service.UserBehaviorService;
import com.ecommerce.vo.CartItemVO;
import com.ecommerce.vo.CartOperationVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public Result<List<CartItemVO>> getCart(@PathVariable String userId) {
        return Result.success(shoppingCartService.getCartWithProducts(userId));
    }

    /** 添加到购物车 */
    @PostMapping("/add")
    public Result<CartOperationVO> addToCart(@RequestBody @Valid AddToCartDTO dto) {
        boolean success = shoppingCartService.addToCart(dto.getUserId(), dto.getProductId());
        // 同时记录行为
        try {
            userBehaviorService.recordBehavior(dto.getUserId(), dto.getProductId(), "cart", null, "cart_page");
        } catch (Exception e) {
            log.warn("ShoppingCartController.addToCart - 记录购物车行为失败: {}", e.getMessage());
        }
        return Result.success(CartOperationVO.builder()
                .success(success)
                .inCart(true)
                .message("添加成功")
                .build());
    }

    /** 更新数量 */
    @PutMapping("/quantity")
    public Result<CartOperationVO> updateQuantity(@RequestBody @Valid UpdateCartQuantityDTO dto) {
        boolean success = shoppingCartService.updateQuantity(dto.getUserId(), dto.getProductId(), dto.getQuantity());
        return Result.success(CartOperationVO.builder()
                .success(success)
                .message("更新成功")
                .build());
    }

    /** 移除单项 */
    @DeleteMapping("/remove")
    public Result<CartOperationVO> removeItem(@RequestBody @Valid RemoveCartItemDTO dto) {
        boolean success = shoppingCartService.removeItem(dto.getUserId(), dto.getProductId());
        try {
            userBehaviorService.recordBehavior(dto.getUserId(), dto.getProductId(), "remove_from_cart", null, "cart_page");
        } catch (Exception e) {
            log.warn("ShoppingCartController.removeItem - 记录移除购物车行为失败: {}", e.getMessage());
        }
        return Result.success(CartOperationVO.builder()
                .success(success)
                .inCart(false)
                .message("移除成功")
                .build());
    }

    /** 清空购物车 */
    @DeleteMapping("/clear/{userId}")
    public Result<CartOperationVO> clearCart(@PathVariable String userId) {
        boolean success = shoppingCartService.clearCart(userId);
        return Result.success(CartOperationVO.builder()
                .success(success)
                .message("清空成功")
                .build());
    }

    /** 检查是否在购物车 */
    @GetMapping("/check")
    public Result<CartOperationVO> checkInCart(@RequestParam String userId, @RequestParam String productId) {
        boolean inCart = shoppingCartService.isInCart(userId, productId);
        return Result.success(CartOperationVO.builder()
                .inCart(inCart)
                .build());
    }
}
