package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.RecommendCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 推荐结果缓存数据访问层
 */
@Mapper
public interface RecommendCacheMapper extends BaseMapper<RecommendCache> {

    /**
     * 根据缓存键查询
     */
    @Select("SELECT * FROM recommend_cache WHERE cache_key = #{cacheKey} AND is_deleted = 0 AND expire_time > NOW()")
    RecommendCache findValidByCacheKey(@Param("cacheKey") String cacheKey);

    /**
     * 查询用户的场景缓存
     */
    @Select("SELECT * FROM recommend_cache WHERE user_id = #{userId} AND scene = #{scene} AND is_deleted = 0 AND expire_time > NOW()")
    RecommendCache findValidByUserAndScene(@Param("userId") String userId, @Param("scene") String scene);

    /**
     * 查询过期的缓存
     */
    @Select("SELECT * FROM recommend_cache WHERE expire_time <= NOW() AND is_deleted = 0")
    List<RecommendCache> findExpiredCaches();
}
