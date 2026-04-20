package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.ConversationSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话会话数据访问层
 */
@Mapper
public interface ConversationSessionMapper extends BaseMapper<ConversationSession> {

    /**
     * 根据会话ID查询
     */
    @Select("SELECT * FROM conversation_session WHERE session_id = #{sessionId} AND is_deleted = 0")
    ConversationSession findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询用户的进行中的会话
     */
    @Select("SELECT * FROM conversation_session WHERE user_id = #{userId} AND status = 1 AND is_deleted = 0 ORDER BY create_time DESC")
    List<ConversationSession> findActiveByUserId(@Param("userId") String userId);

    /**
     * 查询用户最近的会话
     */
    @Select("SELECT * FROM conversation_session WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC LIMIT #{limit}")
    List<ConversationSession> findRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);
}
