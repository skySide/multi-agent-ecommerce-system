package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.Product;
import com.ecommerce.event.ProductChangeEvent;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.ShoppingCartService;
import com.ecommerce.service.UserFavoriteService;
import com.ecommerce.vo.CartItemVO;
import com.ecommerce.vo.FavoriteItemVO;
import com.ecommerce.vo.ProductVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 */
@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Resource
    private ProductMapper productMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private UserFavoriteService userFavoriteService;
    @Resource
    private ShoppingCartService shoppingCartService;

    @Override
    public boolean save(Product entity) {
        boolean result = super.save(entity);
        if (result && entity.getProductId() != null) {
            eventPublisher.publishEvent(new ProductChangeEvent(this, entity.getProductId(), ProductChangeEvent.ChangeType.CREATE));
        }
        return result;
    }

    @Override
    public boolean updateById(Product entity) {
        boolean result = super.updateById(entity);
        if (result && entity.getProductId() != null) {
            eventPublisher.publishEvent(new ProductChangeEvent(this, entity.getProductId(), ProductChangeEvent.ChangeType.UPDATE));
        }
        return result;
    }

    @Override
    public boolean removeById(Product entity) {
        String productId = entity.getProductId();
        boolean result = super.removeById(entity);
        if (result && productId != null) {
            eventPublisher.publishEvent(new ProductChangeEvent(this, productId, ProductChangeEvent.ChangeType.DELETE));
        }
        return result;
    }

    @Override
    public Product getByProductId(String productId) {
        return productMapper.findByProductId(productId);
    }

    @Override
    public List<Product> listByProductIds(List<String> productIds) {
        return productMapper.findByProductIdIn(productIds);
    }

    @Override
    public List<Product> listByCategoryId(String categoryId, Integer status, Integer isDeleted) {
        return productMapper.findByCategoryIdAndProductStatusAndIsDeleted(categoryId, status, isDeleted);
    }

    @Override
    public List<Product> listHotProducts(int limit) {
        return productMapper.findHotProducts(limit);
    }

    @Override
    public List<Product> listHotByCategory(String categoryId, int limit) {
        return productMapper.findHotByCategory(categoryId, limit);
    }

    @Override
    public List<Product> listNewArrivals(int limit) {
        return productMapper.findNewArrivals(limit);
    }

    @Override
    public List<Product> searchByKeyword(String keyword, int limit) {
        return productMapper.searchByKeyword(keyword, limit);
    }

    @Override
    public List<Product> listByCategories(List<String> categoryIds, int limit) {
        return productMapper.findByCategories(categoryIds, limit);
    }

    // ===== ProductVO 方法（含用户收藏/购物车标记） =====

    @Override
    public ProductVO getProductVOByProductId(String productId, String userId) {
        Product product = getByProductId(productId);
        if (product == null) return null;
        ProductVO vo = entityToVO(product);
        populateFlags(Collections.singletonList(vo), userId);
        return vo;
    }

    @Override
    public List<ProductVO> listHotProductVOs(int limit, String userId) {
        List<Product> products = listHotProducts(limit);
        List<ProductVO> vos = products.stream().map(this::entityToVO).collect(Collectors.toList());
        populateFlags(vos, userId);
        return vos;
    }

    @Override
    public List<ProductVO> listNewArrivalVOs(int limit, String userId) {
        List<Product> products = listNewArrivals(limit);
        List<ProductVO> vos = products.stream().map(this::entityToVO).collect(Collectors.toList());
        populateFlags(vos, userId);
        return vos;
    }

    @Override
    public List<ProductVO> searchProductVOs(String keyword, int limit, String userId) {
        List<Product> products = searchByKeyword(keyword, limit);
        List<ProductVO> vos = products.stream().map(this::entityToVO).collect(Collectors.toList());
        populateFlags(vos, userId);
        return vos;
    }

    @Override
    public List<ProductVO> listByCategoryVOs(String categoryId, int limit, String userId) {
        List<Product> products = listByCategoryId(categoryId, 1, 0);
        List<ProductVO> vos = products.stream().map(this::entityToVO).collect(Collectors.toList());
        populateFlags(vos, userId);
        return vos;
    }

    /**
     * entity.Product → ProductVO
     */
    private ProductVO entityToVO(Product product) {
        return ProductVO.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productDescription(product.getProductDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .stock(product.getStock() != null ? product.getStock() : 0)
                .salesCount(product.getSalesCount() != null ? product.getSalesCount() : 0)
                .rating(product.getRating() != null ? product.getRating() : BigDecimal.ZERO)
                .brand(product.getBrand())
                .categoryId(product.getCategoryId())
                .categoryName(product.getCategoryName())
                .mainImage(product.getMainImage())
                .images(product.getImages() != null ? List.of(product.getImages().split(",")) : null)
                .productStatus(product.getProductStatus() != null ? product.getProductStatus() : 0)
                .build();
    }

    /**
     * 批量填充用户收藏/购物车标记
     */
    private void populateFlags(List<ProductVO> vos, String userId) {
        if (userId == null || userId.isEmpty() || vos.isEmpty()) {
            return;
        }
        try {
            Set<String> favIds = userFavoriteService.getFavoritesWithProducts(userId).stream()
                    .map(FavoriteItemVO::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> cartIds = shoppingCartService.getCartWithProducts(userId).stream()
                    .map(CartItemVO::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (ProductVO vo : vos) {
                if (vo.getProductId() != null) {
                    vo.setFavorited(favIds.contains(vo.getProductId()));
                    vo.setInCart(cartIds.contains(vo.getProductId()));
                }
            }
        } catch (Exception e) {
            log.warn("ProductServiceImpl.populateFlags 失败: {}", e.getMessage());
        }
    }
}
