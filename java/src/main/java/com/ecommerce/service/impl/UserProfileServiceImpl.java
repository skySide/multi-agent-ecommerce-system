package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.mapper.UserProfileMapper;
import com.ecommerce.service.UserProfileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户画像服务实现类
 */
@Slf4j
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements UserProfileService {

    @Resource
    private UserProfileMapper userProfileMapper;

    @Override
    public UserProfile getByUserId(String userId) {
        return userProfileMapper.findByUserId(userId);
    }

    @Override
    public UserProfile getByVectorId(String vectorId) {
        return userProfileMapper.findByVectorId(vectorId);
    }

    @Override
    public boolean saveOrUpdateProfile(UserProfile profile) {
        UserProfile existing = getByUserId(profile.getUserId());
        if (existing != null) {
            profile.setId(existing.getId());
            return updateById(profile);
        }
        return save(profile);
    }
}
