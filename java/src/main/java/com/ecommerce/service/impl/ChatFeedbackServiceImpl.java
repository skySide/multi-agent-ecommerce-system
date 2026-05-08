package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    public boolean submitFeedback(String userId, String sessionId, Integer messageIndex,
                                   String userMessage, String aiMessage, Integer rating) {
        log.info("ChatFeedbackServiceImpl.submitFeedback 用户={} 会话={} 索引={} 评分={}",
                userId, sessionId, messageIndex, rating);

        if (StringUtils.isBlank(userId) || StringUtils.isBlank(sessionId) || rating == null) {
            log.error("ChatFeedbackServiceImpl.submitFeedback 参数不完整");
            return false;
        }

        if (rating != 1 && rating != -1) {
            log.error("ChatFeedbackServiceImpl.submitFeedback 评分值无效: {}", rating);
            return false;
        }

        try {
            // 查找是否已有反馈记录
            ChatFeedback existing = lambdaQuery()
                    .eq(ChatFeedback::getUserId, userId)
                    .eq(ChatFeedback::getSessionId, sessionId)
                    .eq(ChatFeedback::getMessageIndex, messageIndex)
                    .one();

            if (existing != null) {
                // 更新现有记录
                existing.setRating(rating);
                existing.setFeedbackTime(LocalDateTime.now());
                return updateById(existing);
            } else {
                // 创建新记录
                ChatFeedback feedback = ChatFeedback.builder()
                        .userId(userId)
                        .sessionId(sessionId)
                        .messageIndex(messageIndex != null ? messageIndex : 0)
                        .userMessage(userMessage)
                        .aiMessage(aiMessage)
                        .rating(rating)
                        .feedbackTime(LocalDateTime.now())
                        .build();
                return save(feedback);
            }
        } catch (Exception e) {
            log.error("ChatFeedbackServiceImpl.submitFeedback 提交反馈失败", e);
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
                    if (rating == 1) {
                        thumbsUp = count;
                    } else if (rating == -1) {
                        thumbsDown = count;
                    }
                }
            }

            long total = thumbsUp + thumbsDown;
            double satisfactionRate = total > 0 ? (double) thumbsUp / total * 100 : 0;

            // 构建评分分布
            List<SatisfactionStatsVO.RatingDistribution> distribution = new ArrayList<>();
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(1)
                    .count(thumbsUp)
                    .build());
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(-1)
                    .count(thumbsDown)
                    .build());

            log.info("ChatFeedbackServiceImpl.getSatisfactionStats 点赞={} 点踩={} 满意度={}",
                    thumbsUp, thumbsDown, satisfactionRate);

            return SatisfactionStatsVO.builder()
                    .totalFeedback(total)
                    .likeCount(thumbsUp)
                    .dislikeCount(thumbsDown)
                    .satisfactionRate(satisfactionRate)
                    .ratingDistribution(distribution)
                    .build();
        } catch (Exception e) {
            log.error("ChatFeedbackServiceImpl.getSatisfactionStats 统计失败", e);
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
                    if (rating == 1) {
                        thumbsUp = count;
                    } else if (rating == -1) {
                        thumbsDown = count;
                    }
                }
            }

            long total = thumbsUp + thumbsDown;
            double satisfactionRate = total > 0 ? (double) thumbsUp / total * 100 : 0;

            // 构建评分分布
            List<SatisfactionStatsVO.RatingDistribution> distribution = new ArrayList<>();
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(1)
                    .count(thumbsUp)
                    .build());
            distribution.add(SatisfactionStatsVO.RatingDistribution.builder()
                    .rating(-1)
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
            log.error("ChatFeedbackServiceImpl.getUserFeedbackStats 统计失败 userId={}", userId, e);
            return SatisfactionStatsVO.builder()
                    .totalFeedback(0L)
                    .likeCount(0L)
                    .dislikeCount(0L)
                    .satisfactionRate(0.0)
                    .ratingDistribution(new ArrayList<>())
                    .build();
        }
    }
}
