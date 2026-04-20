package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.Product;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final ProductMapper productMapper;

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
}
