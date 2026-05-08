package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 推荐响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponseVO {
    private String requestId;
    private String userId;
    private List<ProductVO> productVOList;
    private String experimentGroup;
    private Map<String, AgentResultVO> agentResults;
    private double totalLatencyMs;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
