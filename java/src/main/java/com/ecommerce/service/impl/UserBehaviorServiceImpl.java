package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.agent.UserProfileAgent;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.service.UserBehaviorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户行为日志服务实现类
 */
@Slf4j
@Service
public class UserBehaviorServiceImpl extends ServiceImpl<UserBehaviorMapper, UserBehavior> implements UserBehaviorService {

    @Resource
    private UserBehaviorMapper userBehaviorMapper;

    @Resource
    @Lazy
    private UserProfileAgent userProfileAgent;

    // 用户画像更新防抖缓存：userId -> 最后触发时间戳
    private static final ConcurrentHashMap<String, Long> PROFILE_UPDATE_CACHE = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 5 * 60 * 1000; // 5分钟

    @Override
    public List<UserBehavior> listRecentByUserId(String userId, int days, int limit) {
        return userBehaviorMapper.findRecentByUserId(userId, days, limit);
    }

    @Override
    public List<UserBehavior> listByUserIdAndType(String userId, String behaviorType, int days, int limit) {
        return userBehaviorMapper.findByUserIdAndType(userId, behaviorType, days, limit);
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
        boolean success = save(behavior);

        // 行为入库后，异步触发用户画像更新（带5分钟防抖）
        if (success) {
            triggerAsyncProfileUpdate(userId);
        }
        return success;
    }

    /**
     * 异步触发用户画像更新，同一用户5分钟内最多触发一次
     */
    private void triggerAsyncProfileUpdate(String userId) {
        long now = System.currentTimeMillis();
        Long lastUpdate = PROFILE_UPDATE_CACHE.get(userId);
        if (lastUpdate != null && now - lastUpdate < DEBOUNCE_MS) {
            return; // 5分钟内已触发过，跳过
        }
        PROFILE_UPDATE_CACHE.put(userId, now);

        CompletableFuture.runAsync(() -> {
            try {
                log.info("UserBehaviorServiceImpl.triggerAsyncProfileUpdate 异步更新画像 userId={}", userId);
                userProfileAgent.runAsync(Map.of("userId", userId)).join();
                log.info("UserBehaviorServiceImpl.triggerAsyncProfileUpdate 画像更新完成 userId={}", userId);
            } catch (Exception e) {
                log.warn("UserBehaviorServiceImpl.triggerAsyncProfileUpdate 画像更新失败 userId={}: {}", userId, e.getMessage());
            }
        });
    }
}
