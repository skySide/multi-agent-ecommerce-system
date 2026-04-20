package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.RecommendCache;

import java.util.List;

/**
 * 推荐结果缓存服务接口
 */
public interface RecommendCacheService extends IService<RecommendCache> {

    /**
     * 根据缓存键查询有效缓存
     */
    RecommendCache getValidByCacheKey(String cacheKey);

    /**
     * 查询用户的场景缓存
     */
    RecommendCache getValidByUserAndScene(String userId, String scene);

    /**
     * 查询过期的缓存
     */
    List<RecommendCache> listExpiredCaches();

    /**
     * 保存推荐缓存
     */
    boolean saveCache(String userId, String scene, String products, int expireMinutes);
}
