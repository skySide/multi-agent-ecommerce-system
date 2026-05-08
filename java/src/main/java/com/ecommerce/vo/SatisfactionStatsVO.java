package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 满意度统计VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionStatsVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 总反馈数 */
    private Long totalFeedback;
    
    /** 点赞数 */
    private Long likeCount;
    
    /** 点踩数 */
    private Long dislikeCount;
    
    /** 满意度（点赞比例） */
    private Double satisfactionRate;
    
    /** 各评分分布 */
    private List<RatingDistribution> ratingDistribution;
    
    /**
     * 评分分布
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingDistribution implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        /** 评分值 */
        private Integer rating;
        
        /** 数量 */
        private Long count;
    }
}
