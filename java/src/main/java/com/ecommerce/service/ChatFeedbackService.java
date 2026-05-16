package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.dto.FeedbackRequestDTO;
import com.ecommerce.entity.ChatFeedback;
import com.ecommerce.vo.SatisfactionStatsVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI回复反馈服务接口
 */
public interface ChatFeedbackService extends IService<ChatFeedback> {

    /**
     * 提交反馈
     *
     * @param dto 反馈请求DTO
     * @return 是否成功
     */
    boolean submitFeedback(FeedbackRequestDTO dto);

    /**
     * 获取满意度统计
     *
     * @return 统计结果
     */
    SatisfactionStatsVO getSatisfactionStats();

    /**
     * 获取用户反馈历史
     *
     * @param userId 用户ID
     * @return 反馈列表
     */
    SatisfactionStatsVO getUserFeedbackStats(String userId);

    /**
     * 按时间范围和评分列表查询反馈记录
     *
     * @param start   开始时间
     * @param end     结束时间
     * @param ratings 评分列表（如 [1, -1]）
     * @return 反馈记录列表
     */
    List<ChatFeedback> listByTimeRangeAndRatings(LocalDateTime start, LocalDateTime end, List<Integer> ratings);
}
