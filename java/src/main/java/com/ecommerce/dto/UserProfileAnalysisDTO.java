package com.ecommerce.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 用户画像LLM分析结构化输出DTO
 * 用于 Spring AI BeanOutputConverter 直接映射LLM返回的JSON
 */
@Data
public class UserProfileAnalysisDTO {

    private List<String> segments;

    private List<String> preferredCategories;

    private List<Double> priceRange;

    private Map<String, Double> rfmScore;

    private Map<String, Object> realTimeTags;
}
