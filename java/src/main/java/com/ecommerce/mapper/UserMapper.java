package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据访问层
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户ID查询
     */
    @Select("SELECT * FROM user WHERE user_id = #{userId} AND is_deleted = 0")
    User findByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID查询（包含已删除）
     */
    @Select("SELECT * FROM user WHERE user_id = #{userId} AND is_deleted = #{isDeleted}")
    User findByUserIdAndIsDeleted(@Param("userId") String userId, @Param("isDeleted") Integer isDeleted);

    /**
     * 检查用户是否存在
     */
    @Select("SELECT COUNT(*) FROM user WHERE user_id = #{userId} AND is_deleted = 0")
    boolean existsByUserId(@Param("userId") String userId);
}
