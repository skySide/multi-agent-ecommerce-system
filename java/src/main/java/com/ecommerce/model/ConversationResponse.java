package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private String sessionId;

    private String message;

    private String intent;

    private List<Product> recommendedProducts;

    private List<Map<String, String>> marketingCopies;

    private Map<String, Object> extractedInfo;

    private List<String> dialogueHistory;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
