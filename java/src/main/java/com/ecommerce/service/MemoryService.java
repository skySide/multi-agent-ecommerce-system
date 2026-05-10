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
}
