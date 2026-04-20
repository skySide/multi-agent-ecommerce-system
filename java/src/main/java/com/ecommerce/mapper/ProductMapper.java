package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品数据访问层
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 根据商品ID查询
     */
    @Select("SELECT * FROM product WHERE product_id = #{productId} AND is_deleted = 0")
    Product findByProductId(@Param("productId") String productId);

    /**
     * 根据商品ID列表批量查询
     */
    @Select("<script>" +
            "SELECT * FROM product WHERE product_id IN " +
            "<foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " AND is_deleted = 0" +
            "</script>")
    List<Product> findByProductIdIn(@Param("productIds") List<String> productIds);

    /**
     * 根据类目ID查询商品
     */
    @Select("SELECT * FROM product WHERE category_id = #{categoryId} AND product_status = #{status} AND is_deleted = #{isDeleted}")
    List<Product> findByCategoryIdAndProductStatusAndIsDeleted(
            @Param("categoryId") String categoryId, 
            @Param("status") Integer status, 
            @Param("isDeleted") Integer isDeleted);

    /**
     * 查询热门商品
     */
    @Select("SELECT * FROM product WHERE product_status = 1 AND is_deleted = 0 ORDER BY sales_count DESC LIMIT #{limit}")
    List<Product> findHotProducts(@Param("limit") int limit);

    /**
     * 根据类目查询热门商品
     */
    @Select("SELECT * FROM product WHERE category_id = #{categoryId} AND product_status = 1 AND is_deleted = 0 ORDER BY sales_count DESC LIMIT #{limit}")
    List<Product> findHotByCategory(@Param("categoryId") String categoryId, @Param("limit") int limit);

    /**
     * 查询新品（按创建时间倒序）
     */
    @Select("SELECT * FROM product WHERE product_status = 1 AND is_deleted = 0 ORDER BY create_time DESC LIMIT #{limit}")
    List<Product> findNewArrivals(@Param("limit") int limit);

    /**
     * 全文搜索商品名称
     */
    @Select("SELECT * FROM product WHERE MATCH(product_name) AGAINST(#{keyword} IN BOOLEAN MODE) AND product_status = 1 AND is_deleted = 0 LIMIT #{limit}")
    List<Product> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 根据类目列表查询商品
     */
    @Select("<script>" +
            "SELECT * FROM product WHERE category_id IN " +
            "<foreach collection='categoryIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " AND product_status = 1 AND is_deleted = 0 LIMIT #{limit}" +
            "</script>")
    List<Product> findByCategories(@Param("categoryIds") List<String> categoryIds, @Param("limit") int limit);
}
