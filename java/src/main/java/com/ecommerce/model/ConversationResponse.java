package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.ecommerce.entity.Product;

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

    /** 推荐商品列表（使用 entity.Product 以包含完整字段如 mainImage） */
    private List<Product> recommendedProducts;

    private List<Map<String, String>> marketingCopies;

    private Map<String, Object> extractedInfo;

    private List<String> dialogueHistory;

    /** 对话摘要 */
    private String summary;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
