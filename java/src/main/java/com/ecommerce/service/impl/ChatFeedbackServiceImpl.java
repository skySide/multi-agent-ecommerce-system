package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.enums.FeedbackRatingEnum;
import com.ecommerce.dto.FeedbackRequestDTO;
import com.ecommerce.entity.ChatFeedback;
import com.ecommerce.mapper.ChatFeedbackMapper;
import com.ecommerce.service.ChatFeedbackService;
import com.ecommerce.vo.SatisfactionStatsVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI回复反馈服务实现
 */
@Slf4j
@Service
public class ChatFeedbackServiceImpl extends ServiceImpl<ChatFeedbackMapper, ChatFeedback> implements ChatFeedbackService {

    @Resource
    private ChatFeedbackMapper chatFeedbackMapper;

    @Override
    public boolean submitFeedback(FeedbackRequestDTO dto) {
        log.info("ChatFeedbackService.submitFeedback 用户={} 会话={} 索引={} 评分={} 原因={}",
                dto.getUserId(), dto.getSessionId(), dto.getMessageIndex(), dto.getRating(), dto.getFeedbackReason());

        // 步骤1: 参数校验
        if (StringUtils.isBlank(dto.getUserId()) || StringUtils.isBlank(dto.getSessionId()) || dto.getRating() == null) {
            log.error("ChatFeedbackService.submitFeedback - 参数不完整");
            return false;
        }

        // 步骤2: 校验评分值
        if (FeedbackRatingEnum.getByCode(dto.getRating()) == null) {
            log.error("ChatFeedbackService.submitFeedback - 评分值无效: {}", dto.getRating());
            return false;
        }

        try {
            // 步骤3: 查找是否已有反馈记录
            ChatFeedback existing = lambdaQuery()
                    .eq(ChatFeedback::getUserId, dto.getUserId())
                    .eq(ChatFeedback::getSessionId, dto.getSessionId())
                    .eq(ChatFeedback::getMessageIndex, dto.getMessageIndex())
                    .one();

            if (existing != null) {
                // 步骤4: 更新现有记录
                existing.setRating(dto.getRating());
                existing.setFeedbackReason(dto.getFeedbackReason());
                existing.setFeedbackComment(dto.getFeedbackComment());
                existing.setFeedbackTime(LocalDateTime.now());
                return updateById(existing);
            } else {
                // 步骤5: 创建新记录
                ChatFeedback feedback = ChatFeedback.builder()
                        .userId(dto.getUserId())
                        .sessionId(dto.getSessionId())
                        .messageIndex(dto.getMessageIndex() != null ? dto.getMessageIndex() : 0)
                        .userMessage(dto.getUserMessage())
                        .aiMessage(dto.getAiMessage())
                        .rating(dto.getRating())
                        .feedbackReason(dto.getFeedbackReason())
                        .feedbackComment(dto.getFeedbackComment())
                        .feedbackTime(LocalDateTime.now())
                        .build();
                return save(feedback);
            }
        } catch (Exception e) {
            log.error("ChatFeedbackService.submitFeedback - 提交反馈失败", e);
            return false;
        }
    }

    @Override
    public SatisfactionStatsVO getSatisfactionStats() {
        try {
            List<Map<String, Object>> stats = chatFeedbackMapper.countByRating();
            long thumbsUp = 0;
            long thumbsDown = 0;

            for (Map<String, Object> stat : stats) {
                Integer rating = (Integer) stat.get("rating");
                Long count = (Long) stat.get("count");
                if (rating != null && count != null) {
                    if (FeedbackRatingEnum.LIKE.getCode().equals(rating)) {
                        thumbsUp = count;
                    } else if (FeedbackRatingEnum.DISLIKE.getCode().equals(rating)) {
                        thumbsDown = count;
                    }
                }
            }

            long total = thumbsUp + thumbsDown;
            double satisfactionRate = total > 0 ? (double) thumbsUp / total * 100 : 0;

            // 构建评分分布
            List<SatisfactionStatsVO.RatingDistribution> distribution = new ArrayList<>();
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(FeedbackRatingEnum.LIKE.getCode())
                    .count(thumbsUp)
                    .build());
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(FeedbackRatingEnum.DISLIKE.getCode())
                    .count(thumbsDown)
                    .build());

            log.info("ChatFeedbackService.getSatisfactionStats 点赞={} 点踩={} 满意度={}",
                    thumbsUp, thumbsDown, satisfactionRate);

            return SatisfactionStatsVO.builder()
                    .totalFeedback(total)
                    .likeCount(thumbsUp)
                    .dislikeCount(thumbsDown)
                    .satisfactionRate(satisfactionRate)
                    .ratingDistribution(distribution)
                    .build();
        } catch (Exception e) {
            log.error("ChatFeedbackService.getSatisfactionStats 统计失败", e);
            return SatisfactionStatsVO.builder()
                    .totalFeedback(0L)
                    .likeCount(0L)
                    .dislikeCount(0L)
                    .satisfactionRate(0.0)
                    .ratingDistribution(new ArrayList<>())
                    .build();
        }
    }

    @Override
    public SatisfactionStatsVO getUserFeedbackStats(String userId) {
        try {
            List<Map<String, Object>> stats = chatFeedbackMapper.countByUserId(userId);
            long thumbsUp = 0;
            long thumbsDown = 0;

            for (Map<String, Object> stat : stats) {
                Integer rating = (Integer) stat.get("rating");
                Long count = (Long) stat.get("count");
                if (rating != null && count != null) {
                    if (FeedbackRatingEnum.LIKE.getCode().equals(rating)) {
                        thumbsUp = count;
                    } else if (FeedbackRatingEnum.DISLIKE.getCode().equals(rating)) {
                        thumbsDown = count;
                    }
                }
            }

            long total = thumbsUp + thumbsDown;
            double satisfactionRate = total > 0 ? (double) thumbsUp / total * 100 : 0;

            // 构建评分分布
            List<SatisfactionStatsVO.RatingDistribution> distribution = new ArrayList<>();
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(FeedbackRatingEnum.LIKE.getCode())
                    .count(thumbsUp)
                    .build());
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(FeedbackRatingEnum.DISLIKE.getCode())
                    .count(thumbsDown)
                    .build());

            return SatisfactionStatsVO.builder()
                    .totalFeedback(total)
                    .likeCount(thumbsUp)
                    .dislikeCount(thumbsDown)
                    .satisfactionRate(satisfactionRate)
                    .ratingDistribution(distribution)
                    .build();
        } catch (Exception e) {
            log.error("ChatFeedbackService.getUserFeedbackStats 统计失败 userId={}", userId, e);
            return SatisfactionStatsVO.builder()
                    .totalFeedback(0L)
                    .likeCount(0L)
                    .dislikeCount(0L)
                    .satisfactionRate(0.0)
                    .ratingDistribution(new ArrayList<>())
                    .build();
        }
    }

    @Override
    public List<ChatFeedback> listByTimeRangeAndRatings(LocalDateTime start, LocalDateTime end, List<Integer> ratings) {
        // 步骤1: 参数校验
        if (start == null || end == null || ratings == null || ratings.isEmpty()) {
            log.warn("ChatFeedbackService.listByTimeRangeAndRatings - 参数不完整, start: {}, end: {}, ratings: {}",
                    start, end, ratings);
            return Collections.emptyList();
        }

        // 步骤2: 按时间范围和评分查询
        List<ChatFeedback> result = lambdaQuery()
                .ge(ChatFeedback::getCreateTime, start)
                .lt(ChatFeedback::getCreateTime, end)
                .in(ChatFeedback::getRating, ratings)
                .list();

        log.info("ChatFeedbackService.listByTimeRangeAndRatings - 查询完成, 记录数: {}", result.size());
        return result;
    }
}
