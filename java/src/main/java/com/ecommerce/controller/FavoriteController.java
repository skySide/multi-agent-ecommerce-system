package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.AddFavoriteDTO;
import com.ecommerce.dto.RemoveFavoriteDTO;
import com.ecommerce.service.UserBehaviorService;
import com.ecommerce.service.UserFavoriteService;
import com.ecommerce.vo.FavoriteItemVO;
import com.ecommerce.vo.FavoriteOperationVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public Result<List<FavoriteItemVO>> getFavorites(@PathVariable String userId) {
        return Result.success(userFavoriteService.getFavoritesWithProducts(userId));
    }

    /** 添加收藏 */
    @PostMapping("/add")
    public Result<FavoriteOperationVO> addFavorite(@RequestBody @Valid AddFavoriteDTO dto) {
        boolean success = userFavoriteService.addFavorite(dto.getUserId(), dto.getProductId());
        try {
            userBehaviorService.recordBehavior(dto.getUserId(), dto.getProductId(), "favorite", null, "product_detail");
        } catch (Exception e) {
            log.warn("FavoriteController.addFavorite - 记录收藏行为失败: {}", e.getMessage());
        }
        return Result.success(FavoriteOperationVO.builder()
                .success(success)
                .favorited(true)
                .message("收藏成功")
                .build());
    }

    /** 取消收藏 */
    @DeleteMapping("/remove")
    public Result<FavoriteOperationVO> removeFavorite(@RequestBody @Valid RemoveFavoriteDTO dto) {
        boolean success = userFavoriteService.removeFavorite(dto.getUserId(), dto.getProductId());
        try {
            userBehaviorService.recordBehavior(dto.getUserId(), dto.getProductId(), "unfavorite", null, "product_detail");
        } catch (Exception e) {
            log.warn("FavoriteController.removeFavorite - 记录取消收藏行为失败: {}", e.getMessage());
        }
        return Result.success(FavoriteOperationVO.builder()
                .success(success)
                .favorited(false)
                .message("取消收藏成功")
                .build());
    }

    /** 检查是否已收藏 */
    @GetMapping("/check")
    public Result<FavoriteOperationVO> checkFavorited(@RequestParam String userId, @RequestParam String productId) {
        boolean favorited = userFavoriteService.isFavorited(userId, productId);
        return Result.success(FavoriteOperationVO.builder()
                .favorited(favorited)
                .build());
    }
}
