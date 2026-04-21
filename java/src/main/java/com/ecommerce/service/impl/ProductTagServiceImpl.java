package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.ProductTag;
import com.ecommerce.mapper.ProductTagMapper;
import com.ecommerce.service.ProductTagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品标签关系服务实现类
 */
@Slf4j
@Service
public class ProductTagServiceImpl extends ServiceImpl<ProductTagMapper, ProductTag> implements ProductTagService {

    @Resource
    private ProductTagMapper productTagMapper;

    @Override
    public List<ProductTag> listByProductId(String productId, Integer isDeleted) {
        return productTagMapper.findByProductIdAndIsDeleted(productId, isDeleted);
    }

    @Override
    public List<ProductTag> listByTagId(String tagId, Integer isDeleted) {
        return productTagMapper.findByTagIdAndIsDeleted(tagId, isDeleted);
    }
}
