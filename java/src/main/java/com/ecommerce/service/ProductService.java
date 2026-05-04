package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.Product;
import com.ecommerce.vo.ProductVO;

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

    // ===== 以下方法返回 ProductVO（含当前用户的收藏/购物车标记） =====

    /**
     * 根据商品ID查询商品详情（含用户收藏/购物车标记）
     * @param productId 商品ID
     * @param userId    用户ID（可为null，为null时标记默认为false）
     */
    ProductVO getProductVOByProductId(String productId, String userId);

    /**
     * 查询热门商品（按销量降序，含用户收藏/购物车标记）
     * @param limit  返回数量
     * @param userId 用户ID（可为null）
     */
    List<ProductVO> listHotProductVOs(int limit, String userId);

    /**
     * 查询新品（按创建时间降序，含用户收藏/购物车标记）
     * @param limit  返回数量
     * @param userId 用户ID（可为null）
     */
    List<ProductVO> listNewArrivalVOs(int limit, String userId);

    /**
     * 根据关键词搜索商品（含用户收藏/购物车标记）
     * @param keyword 搜索关键词
     * @param limit   返回数量
     * @param userId  用户ID（可为null）
     */
    List<ProductVO> searchProductVOs(String keyword, int limit, String userId);

    /**
     * 根据类目ID查询商品（含用户收藏/购物车标记）
     * @param categoryId 类目ID
     * @param limit      返回数量
     * @param userId     用户ID（可为null）
     */
    List<ProductVO> listByCategoryVOs(String categoryId, int limit, String userId);
}
