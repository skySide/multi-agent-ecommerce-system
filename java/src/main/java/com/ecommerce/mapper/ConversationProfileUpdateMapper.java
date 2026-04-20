package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.ConversationProfileUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话画像更新记录数据访问层
 */
@Mapper
public interface ConversationProfileUpdateMapper extends BaseMapper<ConversationProfileUpdate> {

    /**
     * 查询用户的画像更新记录
     */
    @Select("SELECT * FROM conversation_profile_update WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<ConversationProfileUpdate> findByUserId(@Param("userId") String userId);

    /**
     * 查询会话的画像更新记录
     */
    @Select("SELECT * FROM conversation_profile_update WHERE session_id = #{sessionId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<ConversationProfileUpdate> findBySessionId(@Param("sessionId") String sessionId);
}
