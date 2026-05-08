package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ShoppingCart;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.ShoppingCartMapper;
import com.ecommerce.service.ShoppingCartService;
import com.ecommerce.vo.CartItemVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper, ShoppingCart> implements ShoppingCartService {

    @Resource
    private ShoppingCartMapper shoppingCartMapper;
    @Resource
    private ProductMapper productMapper;

    @Override
    public boolean addToCart(String userId, String productId) {
        ShoppingCart existing = shoppingCartMapper.findByUserIdAndProductId(userId, productId);
        if (existing != null) {
            // 已存在，数量+1
            return shoppingCartMapper.updateQuantity(userId, productId, existing.getQuantity() + 1) > 0;
        }
        ShoppingCart cart = ShoppingCart.builder()
                .userId(userId)
                .productId(productId)
                .quantity(1)
                .build();
        return save(cart);
    }

    @Override
    public boolean updateQuantity(String userId, String productId, int quantity) {
        if (quantity <= 0) {
            return removeItem(userId, productId);
        }
        return shoppingCartMapper.updateQuantity(userId, productId, quantity) > 0;
    }

    @Override
    public boolean removeItem(String userId, String productId) {
        return shoppingCartMapper.removeItem(userId, productId) > 0;
    }

    @Override
    public boolean clearCart(String userId) {
        return shoppingCartMapper.clearCart(userId) >= 0;
    }

    @Override
    public List<CartItemVO> getCartWithProducts(String userId) {
        List<ShoppingCart> cartItems = shoppingCartMapper.findByUserId(userId);
        List<CartItemVO> result = new ArrayList<>();
        for (ShoppingCart item : cartItems) {
            Product product = productMapper.findByProductId(item.getProductId());
            if (product != null) {
                CartItemVO vo = CartItemVO.builder()
                        .cartId(item.getId())
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .addedTime(item.getCreateTime())
                        .productName(product.getProductName())
                        .price(product.getPrice())
                        .originalPrice(product.getOriginalPrice())
                        .mainImage(product.getMainImage())
                        .brand(product.getBrand())
                        .stock(product.getStock())
                        .build();
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public boolean isInCart(String userId, String productId) {
        return shoppingCartMapper.findByUserIdAndProductId(userId, productId) != null;
    }
}
