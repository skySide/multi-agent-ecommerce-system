package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.Tag;

import java.util.List;

/**
 * 标签服务接口
 */
public interface TagService extends IService<Tag> {

    /**
     * 根据标签ID查询
     */
    Tag getByTagId(String tagId);

    /**
     * 根据标签类型查询
     */
    List<Tag> listByTagType(Integer tagType, Integer status, Integer isDeleted);

    /**
     * 根据商品ID查询标签
     */
    List<Tag> listByProductId(String productId);
}
