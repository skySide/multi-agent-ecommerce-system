package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserFavorite;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserFavoriteMapper;
import com.ecommerce.service.UserFavoriteService;
import com.ecommerce.vo.FavoriteItemVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    public List<FavoriteItemVO> getFavoritesWithProducts(String userId) {
        List<UserFavorite> favorites = userFavoriteMapper.findByUserId(userId);
        List<FavoriteItemVO> result = new ArrayList<>();
        for (UserFavorite fav : favorites) {
            Product product = productMapper.findByProductId(fav.getProductId());
            if (product != null) {
                FavoriteItemVO vo = FavoriteItemVO.builder()
                        .productId(fav.getProductId())
                        .favoriteTime(fav.getCreateTime())
                        .productName(product.getProductName())
                        .price(product.getPrice())
                        .originalPrice(product.getOriginalPrice())
                        .mainImage(product.getMainImage())
                        .brand(product.getBrand())
                        .rating(product.getRating())
                        .build();
                result.add(vo);
            }
        }
        return result;
    }
}
