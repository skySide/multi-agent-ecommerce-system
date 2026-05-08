package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.ShoppingCart;
import com.ecommerce.vo.CartItemVO;

import java.util.List;

public interface ShoppingCartService extends IService<ShoppingCart> {

    /** 添加或更新购物车（已存在则数量+1） */
    boolean addToCart(String userId, String productId);

    /** 更新数量 */
    boolean updateQuantity(String userId, String productId, int quantity);

    /** 移除单项 */
    boolean removeItem(String userId, String productId);

    /** 清空购物车 */
    boolean clearCart(String userId);

    /** 查询购物车（含商品信息） */
    List<CartItemVO> getCartWithProducts(String userId);

    /** 是否已在购物车 */
    boolean isInCart(String userId, String productId);
}
