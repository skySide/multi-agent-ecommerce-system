package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.UserRealtimeFeatures;
import com.ecommerce.mapper.UserRealtimeFeaturesMapper;
import com.ecommerce.service.UserRealtimeFeaturesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户实时特征服务实现类
 */
@Slf4j
@Service
public class UserRealtimeFeaturesServiceImpl extends ServiceImpl<UserRealtimeFeaturesMapper, UserRealtimeFeatures> implements UserRealtimeFeaturesService {

    @Resource
    private UserRealtimeFeaturesMapper userRealtimeFeaturesMapper;

    @Override
    public UserRealtimeFeatures getByUserId(String userId) {
        return userRealtimeFeaturesMapper.findByUserId(userId);
    }

    @Override
    public boolean updateFeatures(String userId, String behaviorType, String productId) {
        UserRealtimeFeatures features = getByUserId(userId);
        if (features == null) {
            features = UserRealtimeFeatures.builder()
                    .userId(userId)
                    .viewCount1h(0)
                    .viewCount24h(0)
                    .clickCount24h(0)
                    .cartCount24h(0)
                    .build();
        }

        // 根据行为类型更新计数
        switch (behaviorType) {
            case "view":
                features.setViewCount24h(features.getViewCount24h() + 1);
                features.setViewCount1h(features.getViewCount1h() + 1);
                break;
            case "click":
                features.setClickCount24h(features.getClickCount24h() + 1);
                break;
            case "cart":
                features.setCartCount24h(features.getCartCount24h() + 1);
                break;
        }

        features.setLastViewProductId(productId);
        features.setLastViewTime(LocalDateTime.now());

        return saveOrUpdate(features);
    }
}
