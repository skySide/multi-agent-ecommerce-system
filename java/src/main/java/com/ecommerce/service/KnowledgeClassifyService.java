package com.ecommerce.service;

import com.ecommerce.dto.ClassifyResultDTO;

/**
 * 知识分类服务
 * 使用 LLM 对用户查询进行知识类别分类
 */
public interface KnowledgeClassifyService {

    /**
     * 对用户查询进行知识分类
     * @param query 用户原始查询
     * @param history 对话历史（可选）
     * @return 分类结果，包含命中的知识类别列表和是否跨类别标记
     */
    ClassifyResultDTO classify(String query, java.util.List<String> history);
}
