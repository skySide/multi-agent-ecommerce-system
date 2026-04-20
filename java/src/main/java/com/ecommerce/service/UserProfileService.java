package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.UserProfile;

/**
 * 用户画像服务接口
 */
public interface UserProfileService extends IService<UserProfile> {

    /**
     * 根据用户ID查询画像
     */
    UserProfile getByUserId(String userId);

    /**
     * 根据向量ID查询
     */
    UserProfile getByVectorId(String vectorId);

    /**
     * 创建或更新用户画像
     */
    boolean saveOrUpdateProfile(UserProfile profile);
}
