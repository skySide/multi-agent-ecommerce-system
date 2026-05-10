package com.ecommerce.service.impl;

import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryServiceImpl implements MemoryService {

    @Resource
    private ConversationSessionService conversationSessionService;

    @Resource
    private UserProfileService userProfileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String serializeHistory(List<String> history) {
        try {
            return objectMapper.writeValueAsString(history);
        } catch (Exception e) {
            log.warn("MemoryService.serializeHistory 序列化失败", e);
            return "[]";
        }
    }

    @Override
    public List<String> deserializeHistory(String historyJson) {
        if (historyJson == null || historyJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(historyJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("MemoryService.deserializeHistory 解析失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveHistory(ConversationSession session, List<String> history, Map<String, Object> entities) {
        try {
            session.setDialogueHistory(objectMapper.writeValueAsString(history));
            Map<String, Object> merged = mergeExtractedInfo(session.getExtractedInfo(), entities);
            session.setExtractedInfo(objectMapper.writeValueAsString(merged));
            session.setUpdateTime(LocalDateTime.now());
            conversationSessionService.updateById(session);
        } catch (Exception e) {
            log.warn("MemoryService.saveHistory 保存历史失败", e);
        }
    }

    @Override
    public String buildHistoryContext(List<String> history, String summary) {
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isEmpty()) {
            sb.append("对话摘要：").append(summary).append("\n\n");
        }
        if (!history.isEmpty()) {
            List<String> recent = history.subList(Math.max(0, history.size() - 6), history.size());
            sb.append("最近对话：\n").append(String.join("\n", recent)).append("\n\n");
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeWithSessionMemory(String sessionId, Map<String, Object> currentEntities) {
        try {
            ConversationSession session = conversationSessionService.getBySessionId(sessionId);
            if (session == null || session.getExtractedInfo() == null) {
                return currentEntities;
            }
            Map<String, Object> sessionMemory = objectMapper.readValue(session.getExtractedInfo(), Map.class);
            Map<String, Object> merged = new HashMap<>(sessionMemory);
            merged.putAll(currentEntities);
            return merged;
        } catch (Exception e) {
            log.warn("MemoryService.mergeWithSessionMemory 失败", e);
            return currentEntities;
        }
    }

    @Override
    public String buildLongTermContext(String userId) {
        try {
            UserProfile profile = userProfileService.getByUserId(userId);
            if (profile == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder("用户历史偏好：");
            if (profile.getPreferredCategories() != null && !profile.getPreferredCategories().isEmpty()) {
                sb.append("偏好类目=").append(profile.getPreferredCategories()).append("；");
            }
            if (profile.getPreferredBrands() != null && !profile.getPreferredBrands().isEmpty()) {
                sb.append("偏好品牌=").append(profile.getPreferredBrands()).append("；");
            }
            if (profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null) {
                sb.append("价格区间=¥").append(profile.getPriceRangeMin())
                        .append("-¥").append(profile.getPriceRangeMax()).append("；");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("MemoryService.buildLongTermContext 失败 userId={}", userId, e);
            return "";
        }
    }

    @Override
    public UserProfile buildProfileFromEntities(String userId, Map<String, Object> entities) {
        UserProfile profile = UserProfile.builder().userId(userId).build();

        if (entities.get("category") instanceof String) {
            profile.setPreferredCategories((String) entities.get("category"));
        } else if (entities.get("category") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> cats = (List<String>) entities.get("category");
            profile.setPreferredCategories(String.join(",", cats));
        }

        if (entities.get("brand") instanceof String) {
            profile.setPreferredBrands((String) entities.get("brand"));
        }

        BigDecimal priceMin = BigDecimal.ZERO;
        BigDecimal priceMax = BigDecimal.valueOf(100000);
        if (entities.get("price_min") instanceof Number) {
            priceMin = BigDecimal.valueOf(((Number) entities.get("price_min")).doubleValue());
        }
        if (entities.get("price_max") instanceof Number) {
            priceMax = BigDecimal.valueOf(((Number) entities.get("price_max")).doubleValue());
        }
        profile.setPriceRangeMin(priceMin);
        profile.setPriceRangeMax(priceMax);

        return profile;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeExtractedInfo(String existingJson, Map<String, Object> newEntities) {
        Map<String, Object> existing = new HashMap<>();
        if (existingJson != null && !existingJson.isEmpty() && !existingJson.equals("{}")) {
            try {
                existing = objectMapper.readValue(existingJson, Map.class);
            } catch (Exception e) {
                log.warn("MemoryService.mergeExtractedInfo 解析失败", e);
            }
        }
        existing.putAll(newEntities);
        return existing;
    }
}
