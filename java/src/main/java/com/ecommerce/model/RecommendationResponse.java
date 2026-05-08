package com.ecommerce.model;

import com.ecommerce.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 推荐响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    private String requestId;

    private String userId;

    /** 推荐商品列表 */
    private List<Product> products;

    private String experimentGroup;

    private Map<String, AgentResult> agentResults;

    private double totalLatencyMs;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
