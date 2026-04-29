package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.ShoppingCart;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {

    @Select("SELECT * FROM shopping_cart WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<ShoppingCart> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM shopping_cart WHERE user_id = #{userId} AND product_id = #{productId} AND is_deleted = 0")
    ShoppingCart findByUserIdAndProductId(@Param("userId") String userId, @Param("productId") String productId);

    @Update("UPDATE shopping_cart SET quantity = #{quantity}, update_time = NOW() WHERE user_id = #{userId} AND product_id = #{productId} AND is_deleted = 0")
    int updateQuantity(@Param("userId") String userId, @Param("productId") String productId, @Param("quantity") int quantity);

    @Update("UPDATE shopping_cart SET is_deleted = 1, update_time = NOW() WHERE user_id = #{userId} AND product_id = #{productId}")
    int removeItem(@Param("userId") String userId, @Param("productId") String productId);

    @Update("UPDATE shopping_cart SET is_deleted = 1, update_time = NOW() WHERE user_id = #{userId}")
    int clearCart(@Param("userId") String userId);
}
