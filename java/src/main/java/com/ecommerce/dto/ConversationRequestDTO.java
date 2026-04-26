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
public class ConversationRequestDTO {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private String sessionId;
    private String context;
}