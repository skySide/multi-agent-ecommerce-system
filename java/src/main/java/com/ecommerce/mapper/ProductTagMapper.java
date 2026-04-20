package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.ProductTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品标签关系数据访问层
 */
@Mapper
public interface ProductTagMapper extends BaseMapper<ProductTag> {

    /**
     * 根据商品ID查询标签关系
     */
    @Select("SELECT * FROM product_tag WHERE product_id = #{productId} AND is_deleted = #{isDeleted}")
    List<ProductTag> findByProductIdAndIsDeleted(@Param("productId") String productId, @Param("isDeleted") Integer isDeleted);

    /**
     * 根据标签ID查询商品关系
     */
    @Select("SELECT * FROM product_tag WHERE tag_id = #{tagId} AND is_deleted = #{isDeleted}")
    List<ProductTag> findByTagIdAndIsDeleted(@Param("tagId") String tagId, @Param("isDeleted") Integer isDeleted);
}
