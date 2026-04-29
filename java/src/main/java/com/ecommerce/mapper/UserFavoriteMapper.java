package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.UserFavorite;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    @Select("SELECT * FROM user_favorite WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<UserFavorite> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM user_favorite WHERE user_id = #{userId} AND product_id = #{productId} AND is_deleted = 0")
    UserFavorite findByUserIdAndProductId(@Param("userId") String userId, @Param("productId") String productId);

    @Update("UPDATE user_favorite SET is_deleted = 1, update_time = NOW() WHERE user_id = #{userId} AND product_id = #{productId}")
    int removeFavorite(@Param("userId") String userId, @Param("productId") String productId);
}
