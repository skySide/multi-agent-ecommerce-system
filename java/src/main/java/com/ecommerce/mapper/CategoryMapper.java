package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 类目数据访问层
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 根据类目ID查询
     */
    @Select("SELECT * FROM category WHERE category_id = #{categoryId} AND is_deleted = 0")
    Category findByCategoryId(@Param("categoryId") String categoryId);

    /**
     * 根据父类目ID查询子类目
     */
    @Select("SELECT * FROM category WHERE parent_id = #{parentId} AND is_deleted = #{isDeleted}")
    List<Category> findByParentIdAndIsDeleted(@Param("parentId") String parentId, @Param("isDeleted") Integer isDeleted);

    /**
     * 根据层级查询类目
     */
    @Select("SELECT * FROM category WHERE category_level = #{level} AND is_deleted = #{isDeleted}")
    List<Category> findByCategoryLevelAndIsDeleted(@Param("level") Integer level, @Param("isDeleted") Integer isDeleted);
}
