package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 知识库搜索结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResultVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 文档内容 */
    private String content;
    
    /** 元数据 */
    private Map<String, Object> metadata;
}
