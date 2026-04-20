package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.Tag;
import com.ecommerce.mapper.TagMapper;
import com.ecommerce.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;

    @Override
    public Tag getByTagId(String tagId) {
        return tagMapper.findByTagId(tagId);
    }

    @Override
    public List<Tag> listByTagType(Integer tagType, Integer status, Integer isDeleted) {
        return tagMapper.findByTagTypeAndTagStatusAndIsDeleted(tagType, status, isDeleted);
    }

    @Override
    public List<Tag> listByProductId(String productId) {
        return tagMapper.findByProductId(productId);
    }
}
