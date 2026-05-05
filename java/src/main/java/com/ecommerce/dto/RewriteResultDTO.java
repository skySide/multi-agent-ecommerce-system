package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Query 改写结果 DTO
 * <p>用于封装 LLM 改写用户查询的结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewriteResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原始用户输入 */
    private String originalQuery;

    /** 改写后的检索 query */
    private String rewrittenQuery;

    /** 扩展的同义查询（用于多路召回） */
    private List<String> expandedQueries;

    /** 识别的意图：recommend/product_query/knowledge_query/compare/chitchat */
    private String intent;

    /** 抽取的实体：category, brand, price_min, price_max 等 */
    private Map<String, Object> entities;

    /** 置信度 0-1 */
    private Double confidence;
}
