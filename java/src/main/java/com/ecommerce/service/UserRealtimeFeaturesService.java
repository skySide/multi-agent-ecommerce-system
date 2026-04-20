package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.UserRealtimeFeatures;

/**
 * 用户实时特征服务接口
 */
public interface UserRealtimeFeaturesService extends IService<UserRealtimeFeatures> {

    /**
     * 根据用户ID查询实时特征
     */
    UserRealtimeFeatures getByUserId(String userId);

    /**
     * 更新用户实时特征
     */
    boolean updateFeatures(String userId, String behaviorType, String productId);
}
