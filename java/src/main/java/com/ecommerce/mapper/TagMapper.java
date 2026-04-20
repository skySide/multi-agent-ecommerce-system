package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 标签数据访问层
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 根据标签ID查询
     */
    @Select("SELECT * FROM tag WHERE tag_id = #{tagId} AND is_deleted = 0")
    Tag findByTagId(@Param("tagId") String tagId);

    /**
     * 根据标签类型查询
     */
    @Select("SELECT * FROM tag WHERE tag_type = #{tagType} AND tag_status = #{status} AND is_deleted = #{isDeleted}")
    List<Tag> findByTagTypeAndTagStatusAndIsDeleted(@Param("tagType") Integer tagType, @Param("status") Integer status, @Param("isDeleted") Integer isDeleted);

    /**
     * 根据商品ID查询标签
     */
    @Select("SELECT t.* FROM tag t INNER JOIN product_tag pt ON t.tag_id = pt.tag_id " +
            "WHERE pt.product_id = #{productId} AND pt.is_deleted = 0 AND t.is_deleted = 0")
    List<Tag> findByProductId(@Param("productId") String productId);
}
