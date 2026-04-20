package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.Product;

import java.util.List;

/**
 * 商品服务接口
 */
public interface ProductService extends IService<Product> {

    /**
     * 根据商品ID查询
     */
    Product getByProductId(String productId);

    /**
     * 根据商品ID列表批量查询
     */
    List<Product> listByProductIds(List<String> productIds);

    /**
     * 根据类目ID查询商品
     */
    List<Product> listByCategoryId(String categoryId, Integer status, Integer isDeleted);

    /**
     * 查询热门商品
     */
    List<Product> listHotProducts(int limit);

    /**
     * 根据类目查询热门商品
     */
    List<Product> listHotByCategory(String categoryId, int limit);

    /**
     * 查询新品
     */
    List<Product> listNewArrivals(int limit);

    /**
     * 全文搜索商品
     */
    List<Product> searchByKeyword(String keyword, int limit);

    /**
     * 根据类目列表查询商品
     */
    List<Product> listByCategories(List<String> categoryIds, int limit);
}
