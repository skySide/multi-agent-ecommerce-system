package com.ecommerce.model;

import com.ecommerce.vo.MarketingCopyVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    private String requestId;

    private String userId;

    /** 推荐商品列表（使用 entity.Product 以包含完整字段如 mainImage） */
    private List<com.ecommerce.entity.Product> products;

    private List<MarketingCopyVO> marketingCopies;

    private String experimentGroup;

    private Map<String, AgentResult> agentResults;

    private double totalLatencyMs;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
