package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequestDTO {

    private String userId;

    private String scene;

    @Min(value = 1, message = "推荐数量不能小于1")
    private int numItems;

    private String context;
}