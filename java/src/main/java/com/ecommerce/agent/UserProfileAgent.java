package com.ecommerce.agent;

import com.ecommerce.entity.UserBehavior;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.UserBehaviorService;
import com.ecommerce.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
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
            输出JSON格式:
            {"segments":["active"],"preferred_categories":["手机"],"price_range":[0,10000],
             "rfm_score":{"recency":0.8,"frequency":0.5,"monetary":0.6},
             "real_time_tags":{"活跃时段":"晚间"}}
            只输出JSON。""";

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

        // 3. 构建 LLM 分析提示
        String behaviorJson = objectMapper.writeValueAsString(behavior);
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("用户ID: " + userId + "\n历史画像: " + (existingProfile != null ? profileToString(existingProfile) : "无") + "\n行为数据: " + behaviorJson)
                .call()
                .content();

        // 4. 解析并构建画像
        com.ecommerce.model.UserProfile profile = parseProfile(userId, response, behavior);

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
     * 解析 LLM 输出，构建 UserProfile 模型
     */
    @SuppressWarnings("unchecked")
    private com.ecommerce.model.UserProfile parseProfile(String userId, String raw, Map<String, Object> behavior) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }
            Map<String, Object> data = objectMapper.readValue(cleaned, Map.class);

            List<String> segments = (List<String>) data.getOrDefault("segments", List.of("active"));
            List<String> categories = (List<String>) data.getOrDefault("preferred_categories", List.of());
            List<?> priceRaw = (List<?>) data.getOrDefault("price_range", List.of(0, 10000));
            Map<String, Double> rfm = (Map<String, Double>) data.getOrDefault("rfm_score", Map.of());
            Map<String, Object> tags = (Map<String, Object>) data.getOrDefault("real_time_tags", Map.of());

            // 从行为中提取最近浏览和购买
            List<String> recentViews = (List<String>) behavior.getOrDefault("recent_views", List.of());
            List<String> recentPurchases = (List<String>) behavior.getOrDefault("recent_purchases", List.of());

            return com.ecommerce.model.UserProfile.builder()
                    .userId(userId)
                    .segments(segments)
                    .preferredCategories(categories)
                    .priceRange(new double[]{
                            ((Number) priceRaw.get(0)).doubleValue(),
                            priceRaw.size() > 1 ? ((Number) priceRaw.get(1)).doubleValue() : 10000
                    })
                    .recentViews(recentViews)
                    .recentPurchases(recentPurchases)
                    .rfmScore(rfm)
                    .realTimeTags(tags)
                    .build();
        } catch (Exception e) {
            log.warn("UserProfileAgent.parseProfile 解析失败 userId={}: {}", userId, e.getMessage());
            return com.ecommerce.model.UserProfile.builder()
                    .userId(userId)
                    .segments(List.of("active"))
                    .recentViews((List<String>) behavior.getOrDefault("recent_views", List.of()))
                    .recentPurchases((List<String>) behavior.getOrDefault("recent_purchases", List.of()))
                    .build();
        }
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
