package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.Category;

import java.util.List;

/**
 * 类目服务接口
 */
public interface CategoryService extends IService<Category> {

    /**
     * 根据类目ID查询
     */
    Category getByCategoryId(String categoryId);

    /**
     * 根据父类目ID查询子类目
     */
    List<Category> listByParentId(String parentId, Integer isDeleted);

    /**
     * 根据层级查询类目
     */
    List<Category> listByLevel(Integer level, Integer isDeleted);
}
