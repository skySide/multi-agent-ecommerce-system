package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorRecordDTO {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    private String productId;

    @NotBlank(message = "行为类型不能为空")
    private String behaviorType;

    private String searchKeyword;
    private String referrer;
}