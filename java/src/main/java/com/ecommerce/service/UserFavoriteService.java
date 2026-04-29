package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.UserFavorite;

import java.util.List;
import java.util.Map;

public interface UserFavoriteService extends IService<UserFavorite> {

    /** 添加收藏 */
    boolean addFavorite(String userId, String productId);

    /** 取消收藏 */
    boolean removeFavorite(String userId, String productId);

    /** 是否已收藏 */
    boolean isFavorited(String userId, String productId);

    /** 查询收藏列表（含商品信息） */
    List<Map<String, Object>> getFavoritesWithProducts(String userId);
}
