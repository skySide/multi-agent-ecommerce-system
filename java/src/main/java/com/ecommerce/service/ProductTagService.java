package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.ProductTag;

import java.util.List;

/**
 * 商品标签关系服务接口
 */
public interface ProductTagService extends IService<ProductTag> {

    /**
     * 根据商品ID查询标签关系
     */
    List<ProductTag> listByProductId(String productId, Integer isDeleted);

    /**
     * 根据标签ID查询商品关系
     */
    List<ProductTag> listByTagId(String tagId, Integer isDeleted);
}
