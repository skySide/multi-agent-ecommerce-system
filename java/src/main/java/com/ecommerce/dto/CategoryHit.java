package com.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 知识分类命中项
 * 描述 LLM 分类结果中命中的单个知识类别
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryHit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 知识大类 */
    @JsonProperty("knowledge_type")
    private String knowledgeType;

    /** 知识子类 */
    @JsonProperty("sub_type")
    private String subType;

    /** 该类别与用户问题的相关度 0-1 */
    private Double relevance;
}
