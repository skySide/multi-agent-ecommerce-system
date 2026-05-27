package com.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 知识分类结果 DTO
 * LLM 对用户问题涉及的知识类别进行分类的结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassifyResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命中的知识类别列表 */
    private List<CategoryHit> categories;

    /** 是否跨类别（涉及2个及以上大类） */
    @JsonProperty("is_cross_category")
    private boolean isCrossCategory;

    /** 分类置信度 0-1 */
    private Double confidence;
}
