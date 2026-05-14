package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.dto.FeedbackRequestDTO;
import com.ecommerce.entity.ChatFeedback;
import com.ecommerce.vo.SatisfactionStatsVO;

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
}
