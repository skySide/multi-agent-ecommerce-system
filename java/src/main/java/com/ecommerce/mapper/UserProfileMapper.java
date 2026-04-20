package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户画像数据访问层
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    /**
     * 根据用户ID查询画像
     */
    @Select("SELECT * FROM user_profile WHERE user_id = #{userId} AND is_deleted = 0")
    UserProfile findByUserId(@Param("userId") String userId);

    /**
     * 根据向量ID查询
     */
    @Select("SELECT * FROM user_profile WHERE vector_id = #{vectorId} AND is_deleted = 0")
    UserProfile findByVectorId(@Param("vectorId") String vectorId);
}
