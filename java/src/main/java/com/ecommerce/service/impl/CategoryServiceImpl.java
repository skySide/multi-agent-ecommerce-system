package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.Category;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 类目服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public Category getByCategoryId(String categoryId) {
        return categoryMapper.findByCategoryId(categoryId);
    }

    @Override
    public List<Category> listByParentId(String parentId, Integer isDeleted) {
        return categoryMapper.findByParentIdAndIsDeleted(parentId, isDeleted);
    }

    @Override
    public List<Category> listByLevel(Integer level, Integer isDeleted) {
        return categoryMapper.findByCategoryLevelAndIsDeleted(level, isDeleted);
    }
}
