package com.ecommerce.service;

import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.UserProfile;

import java.util.List;
import java.util.Map;

/**
 * 记忆管理服务
 * 管理对话的短期记忆（历史 + 摘要 + 跨轮实体）和长期记忆（用户画像）
 */
public interface MemoryService {

    String serializeHistory(List<String> history);

    List<String> deserializeHistory(String historyJson);

    void saveHistory(ConversationSession session, List<String> history, Map<String, Object> entities);

    String buildHistoryContext(List<String> history, String summary);

    Map<String, Object> mergeWithSessionMemory(String sessionId, Map<String, Object> currentEntities);

    String buildLongTermContext(String userId);

    UserProfile buildProfileFromEntities(String userId, Map<String, Object> entities);

    /**
     * 更新每轮意图记录
     * 追加当前轮的 intent + entities 到 round_intents，保留最近10轮
     *
     * @param session  会话实体
     * @param round    当前轮次
     * @param intent   识别出的意图
     * @param entities 本轮实体
     */
    void updateRoundIntents(ConversationSession session, int round, String intent, Map<String, Object> entities);
}
