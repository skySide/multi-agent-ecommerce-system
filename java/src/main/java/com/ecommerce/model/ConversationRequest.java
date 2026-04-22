package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 对话请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRequest {

    private String userId;

    private String sessionId;

    private String message;

    @Builder.Default
    private String scene = "chat";

    private Map<String, Object> context;
}
