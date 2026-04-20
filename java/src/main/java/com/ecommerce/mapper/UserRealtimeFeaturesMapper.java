package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.UserRealtimeFeatures;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户实时特征数据访问层
 */
@Mapper
public interface UserRealtimeFeaturesMapper extends BaseMapper<UserRealtimeFeatures> {

    /**
     * 根据用户ID查询实时特征
     */
    @Select("SELECT * FROM user_realtime_features WHERE user_id = #{userId} AND is_deleted = 0")
    UserRealtimeFeatures findByUserId(@Param("userId") String userId);
}
