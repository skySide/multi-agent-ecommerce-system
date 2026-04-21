package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.service.UserBehaviorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户行为日志服务实现类
 */
@Slf4j
@Service
public class UserBehaviorServiceImpl extends ServiceImpl<UserBehaviorMapper, UserBehavior> implements UserBehaviorService {

    @Resource
    private UserBehaviorMapper userBehaviorMapper;

    @Override
    public List<UserBehavior> listRecentByUserId(String userId, int limit) {
        return userBehaviorMapper.findRecentByUserId(userId, limit);
    }

    @Override
    public List<UserBehavior> listByUserIdAndType(String userId, String behaviorType, int limit) {
        return userBehaviorMapper.findByUserIdAndType(userId, behaviorType, limit);
    }

    @Override
    public boolean recordBehavior(String userId, String productId, String behaviorType, String searchKeyword, String referrer) {
        UserBehavior behavior = UserBehavior.builder()
                .userId(userId)
                .productId(productId)
                .behaviorType(behaviorType)
                .searchKeyword(searchKeyword)
                .referrer(referrer)
                .build();
        return save(behavior);
    }
}
