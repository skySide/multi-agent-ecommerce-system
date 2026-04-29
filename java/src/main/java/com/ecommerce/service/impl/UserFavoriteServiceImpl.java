package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserFavorite;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserFavoriteMapper;
import com.ecommerce.service.UserFavoriteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class UserFavoriteServiceImpl extends ServiceImpl<UserFavoriteMapper, UserFavorite> implements UserFavoriteService {

    @Resource
    private UserFavoriteMapper userFavoriteMapper;
    @Resource
    private ProductMapper productMapper;

    @Override
    public boolean addFavorite(String userId, String productId) {
        if (isFavorited(userId, productId)) {
            return true; // 已收藏，幂等
        }
        UserFavorite favorite = UserFavorite.builder()
                .userId(userId)
                .productId(productId)
                .build();
        return save(favorite);
    }

    @Override
    public boolean removeFavorite(String userId, String productId) {
        return userFavoriteMapper.removeFavorite(userId, productId) > 0;
    }

    @Override
    public boolean isFavorited(String userId, String productId) {
        return userFavoriteMapper.findByUserIdAndProductId(userId, productId) != null;
    }

    @Override
    public List<Map<String, Object>> getFavoritesWithProducts(String userId) {
        List<UserFavorite> favorites = userFavoriteMapper.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserFavorite fav : favorites) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("productId", fav.getProductId());
            entry.put("favoriteTime", fav.getCreateTime());
            Product product = productMapper.findByProductId(fav.getProductId());
            if (product != null) {
                entry.put("productName", product.getProductName());
                entry.put("price", product.getPrice());
                entry.put("originalPrice", product.getOriginalPrice());
                entry.put("mainImage", product.getMainImage());
                entry.put("brand", product.getBrand());
                entry.put("rating", product.getRating());
            }
            result.add(entry);
        }
        return result;
    }
}
