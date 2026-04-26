package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户行为日志数据访问层
 */
@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    /**
     * 查询用户最近的行为记录
     */
    @Select("SELECT * FROM user_behavior WHERE user_id = #{userId} AND is_deleted = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) ORDER BY create_time DESC LIMIT #{limit}")
    List<UserBehavior> findRecentByUserId(@Param("userId") String userId, @Param("days") int days, @Param("limit") int limit);

    /**
     * 查询用户的特定行为类型记录（可指定时间范围）
     */
    @Select("SELECT * FROM user_behavior WHERE user_id = #{userId} AND behavior_type = #{behaviorType} AND is_deleted = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) ORDER BY create_time DESC LIMIT #{limit}")
    List<UserBehavior> findByUserIdAndType(@Param("userId") String userId, @Param("behaviorType") String behaviorType, @Param("days") int days, @Param("limit") int limit);
}
