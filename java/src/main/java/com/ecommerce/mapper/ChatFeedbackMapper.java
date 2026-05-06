package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.ChatFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * AI回复反馈 Mapper
 */
@Mapper
public interface ChatFeedbackMapper extends BaseMapper<ChatFeedback> {

    /**
     * 统计用户满意度
     */
    @Select("SELECT rating, COUNT(*) as count FROM chat_feedback " +
            "WHERE is_deleted = 0 GROUP BY rating")
    List<Map<String, Object>> countByRating();

    /**
     * 统计某用户的反馈
     */
    @Select("SELECT rating, COUNT(*) as count FROM chat_feedback " +
            "WHERE user_id = #{userId} AND is_deleted = 0 GROUP BY rating")
    List<Map<String, Object>> countByUserId(String userId);
}
