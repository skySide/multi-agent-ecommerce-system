package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.RecommendCache;
import com.ecommerce.mapper.RecommendCacheMapper;
import com.ecommerce.service.RecommendCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推荐结果缓存服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendCacheServiceImpl extends ServiceImpl<RecommendCacheMapper, RecommendCache> implements RecommendCacheService {

    private final RecommendCacheMapper recommendCacheMapper;

    @Override
    public RecommendCache getValidByCacheKey(String cacheKey) {
        return recommendCacheMapper.findValidByCacheKey(cacheKey);
    }

    @Override
    public RecommendCache getValidByUserAndScene(String userId, String scene) {
        return recommendCacheMapper.findValidByUserAndScene(userId, scene);
    }

    @Override
    public List<RecommendCache> listExpiredCaches() {
        return recommendCacheMapper.findExpiredCaches();
    }

    @Override
    public boolean saveCache(String userId, String scene, String products, int expireMinutes) {
        String cacheKey = userId + ":" + scene;
        RecommendCache cache = RecommendCache.builder()
                .cacheKey(cacheKey)
                .userId(userId)
                .scene(scene)
                .products(products)
                .expireTime(LocalDateTime.now().plusMinutes(expireMinutes))
                .build();
        return save(cache);
    }
}
