package com.ecommerce.agent;

import com.ecommerce.dto.UserProfileAnalysisDTO;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.UserBehaviorService;
import com.ecommerce.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户画像Agent — 实时特征提取 + RFM模型 + 用户分群
 * 接入真实用户行为数据
 */
@Component
public class UserProfileAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;
    @Resource
    private UserBehaviorService userBehaviorService;
    @Resource
    private UserProfileService userProfileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是一个电商用户画像分析专家。根据用户的行为数据,分析用户特征并生成画像。
            请严格按照要求的JSON格式输出，不要包含任何其他文字。""";

    public UserProfileAgent() {
        super("user_profile", 5.0, 2);
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        String userId = (String) params.get("userId");
        log.info("UserProfileAgent.execute 开始分析用户画像 userId={}", userId);

        // 1. 从数据库读取真实行为数据
        Map<String, Object> behavior = collectBehavior(userId);

        // 2. 读取已有画像（如果有）
        UserProfile existingProfile = userProfileService.getByUserId(userId);

        // 3. 构建 LLM 分析提示，使用结构化输出直接映射到DTO
        String behaviorJson = objectMapper.writeValueAsString(behavior);
        BeanOutputConverter<UserProfileAnalysisDTO> converter = new BeanOutputConverter<>(UserProfileAnalysisDTO.class);
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT + "\n" + converter.getFormat())
                .user("用户ID: " + userId + "\n历史画像: " + (existingProfile != null ? profileToString(existingProfile) : "无") + "\n行为数据: " + behaviorJson)
                .call()
                .content();

        // 4. 解析并构建画像（结构化输出，无需手动parse JSON字符串）
        UserProfileAnalysisDTO analysis = converter.convert(response);
        com.ecommerce.model.UserProfile profile = buildProfileFromDto(userId, analysis, behavior);

        // 5. 保存画像到数据库
        try {
            saveProfileToDb(userId, profile, behavior);
        } catch (Exception e) {
            log.warn("UserProfileAgent.execute 保存画像到数据库失败: {}", e.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("raw_analysis", response);
        data.put("profile", profile);
        data.put("behavior_summary", behavior);

        log.info("UserProfileAgent.execute 用户画像分析完成 userId={} segments={}", userId, profile.getSegments());

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.85)
                .build();
    }

    /**
     * 从数据库收集用户真实行为数据
     */
    private Map<String, Object> collectBehavior(String userId) {
        Map<String, Object> behavior = new HashMap<>();
        behavior.put("user_id", userId);

        try {
            // 最近浏览记录
            List<UserBehavior> recentBehaviors = userBehaviorService.listRecentByUserId(userId, 50);

            // 按行为类型分组
            List<String> recentViews = recentBehaviors.stream()
                    .filter(b -> "view".equals(b.getBehaviorType()))
                    .map(UserBehavior::getProductId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(10)
                    .collect(Collectors.toList());

            List<String> recentPurchases = recentBehaviors.stream()
                    .filter(b -> "purchase".equals(b.getBehaviorType()))
                    .map(UserBehavior::getProductId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(10)
                    .collect(Collectors.toList());

            List<String> recentCarts = recentBehaviors.stream()
                    .filter(b -> "cart".equals(b.getBehaviorType()))
                    .map(UserBehavior::getProductId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(5)
                    .collect(Collectors.toList());

            List<String> searchKeywords = recentBehaviors.stream()
                    .map(UserBehavior::getSearchKeyword)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(10)
                    .collect(Collectors.toList());

            behavior.put("recent_views", recentViews);
            behavior.put("recent_purchases", recentPurchases);
            behavior.put("recent_carts", recentCarts);
            behavior.put("search_keywords", searchKeywords);
            behavior.put("total_behavior_count", recentBehaviors.size());
            behavior.put("view_count_7d", recentViews.size());
            behavior.put("purchase_count_30d", recentPurchases.size());

            // 如果有购买，计算平均订单金额（简化）
            if (!recentPurchases.isEmpty()) {
                behavior.put("purchase_count", recentPurchases.size());
            }

            log.info("UserProfileAgent.collectBehavior 用户={} 行为数据: 浏览{}条 购买{}条 搜索{}个关键词",
                    userId, recentViews.size(), recentPurchases.size(), searchKeywords.size());

        } catch (Exception e) {
            log.warn("UserProfileAgent.collectBehavior 读取用户行为失败: {}", e.getMessage());
            behavior.put("recent_views", List.of());
            behavior.put("recent_purchases", List.of());
            behavior.put("search_keywords", List.of());
        }

        return behavior;
    }

    /**
     * 从结构化DTO构建 UserProfile 模型
     */
    private com.ecommerce.model.UserProfile buildProfileFromDto(String userId, UserProfileAnalysisDTO dto, Map<String, Object> behavior) {
        if (dto == null) {
            return fallbackProfile(userId, behavior);
        }
        try {
            List<String> segments = dto.getSegments() != null ? dto.getSegments() : List.of("active");
            List<String> categories = dto.getPreferredCategories() != null ? dto.getPreferredCategories() : List.of();
            List<Double> priceRange = dto.getPriceRange() != null && dto.getPriceRange().size() >= 2
                    ? dto.getPriceRange() : List.of(0.0, 10000.0);

            // 从行为中提取最近浏览和购买
            @SuppressWarnings("unchecked")
            List<String> recentViews = (List<String>) behavior.getOrDefault("recent_views", List.of());
            @SuppressWarnings("unchecked")
            List<String> recentPurchases = (List<String>) behavior.getOrDefault("recent_purchases", List.of());

            return com.ecommerce.model.UserProfile.builder()
                    .userId(userId)
                    .segments(segments)
                    .preferredCategories(categories)
                    .priceRange(new double[]{priceRange.get(0), priceRange.get(1)})
                    .recentViews(recentViews)
                    .recentPurchases(recentPurchases)
                    .rfmScore(dto.getRfmScore())
                    .realTimeTags(dto.getRealTimeTags())
                    .build();
        } catch (Exception e) {
            log.warn("UserProfileAgent.buildProfileFromDto 构建失败 userId={}: {}", userId, e.getMessage());
            return fallbackProfile(userId, behavior);
        }
    }

    @SuppressWarnings("unchecked")
    private com.ecommerce.model.UserProfile fallbackProfile(String userId, Map<String, Object> behavior) {
        return com.ecommerce.model.UserProfile.builder()
                .userId(userId)
                .segments(List.of("active"))
                .recentViews((List<String>) behavior.getOrDefault("recent_views", List.of()))
                .recentPurchases((List<String>) behavior.getOrDefault("recent_purchases", List.of()))
                .build();
    }

    /**
     * 将画像保存到数据库
     */
    private void saveProfileToDb(String userId, com.ecommerce.model.UserProfile profile, Map<String, Object> behavior) {
        UserProfile entity = UserProfile.builder()
                .userId(userId)
                .segments(profile.getSegments() != null ? String.join(",", profile.getSegments()) : "active")
                .preferredCategories(profile.getPreferredCategories() != null ? String.join(",", profile.getPreferredCategories()) : "")
                .priceRangeMin(BigDecimal.valueOf(profile.getPriceRange()[0]))
                .priceRangeMax(BigDecimal.valueOf(profile.getPriceRange()[1]))
                .realTimeTags(profile.getRealTimeTags() != null ? profile.getRealTimeTags().toString() : "")
                .build();

        // 设置 RFM 分数
        if (profile.getRfmScore() != null) {
            entity.setRfmRecency(BigDecimal.valueOf(profile.getRfmScore().getOrDefault("recency", 0.5)));
            entity.setRfmFrequency(BigDecimal.valueOf(profile.getRfmScore().getOrDefault("frequency", 0.5)));
            entity.setRfmMonetary(BigDecimal.valueOf(profile.getRfmScore().getOrDefault("monetary", 0.5)));
        }

        userProfileService.saveOrUpdateProfile(entity);
        log.info("UserProfileAgent.saveProfileToDb 用户画像已保存 userId={}", userId);
    }

    private String profileToString(UserProfile entity) {
        if (entity == null) return "无";
        return String.format("分群=%s, 偏好类目=%s, 价格范围=%s-%s",
                entity.getSegments(), entity.getPreferredCategories(),
                entity.getPriceRangeMin(), entity.getPriceRangeMax());
    }
}
