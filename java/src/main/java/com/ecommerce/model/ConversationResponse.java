package com.ecommerce.model;

import com.ecommerce.entity.Product;
import com.ecommerce.vo.MarketingCopyVO;
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

    /** 推荐商品列表（使用 entity.Product 以包含完整字段如 mainImage） */
    private List<Product> recommendedProducts;

    private List<MarketingCopyVO> marketingCopies;

    private Map<String, Object> extractedInfo;

    private List<String> dialogueHistory;

    /** 对话摘要 */
    private String summary;

    /** 子任务执行结果列表（多意图场景，展示各子Agent执行状态和耗时） */
    private List<SubTaskResult> subTasks;

    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * 子任务执行结果
     * 用于前端展示多意图场景下各子Agent的执行状态
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskResult {

        /** Agent名称 */
        private String agentName;

        /** 是否执行成功 */
        private boolean success;

        /** 执行耗时（毫秒） */
        private double latencyMs;

        /** 子Agent返回的回复文本 */
        private String reply;
    }
}
