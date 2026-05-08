package com.ecommerce.agent;

import com.ecommerce.dto.UserProfileAnalysisDTO;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.UserProfileService;
import com.ecommerce.tool.UserBehaviorTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户画像Agent
 * 实时特征提取 + RFM模型 + 用户分群
 * 通过 UserBehaviorTool 收集行为数据，LLM分析生成画像
 */
@Component
public class UserProfileAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private UserBehaviorTool userBehaviorTool;

    @Resource
    private UserProfileService userProfileService;

    /** 默认用户分群 */
    private static final String DEFAULT_SEGMENT = "active";

    /** 默认价格区间下限 */
    private static final double DEFAULT_PRICE_MIN = 0.0;

    /** 默认价格区间上限 */
    private static final double DEFAULT_PRICE_MAX = 10000.0;

    public UserProfileAgent() {
        super("user_profile", 5.0, 2);
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 获取用户ID
        String userId = (String) params.get("userId");
        log.info("UserProfileAgent.execute - 开始分析用户画像, userId: {}", userId);

        // 步骤2: 查询已有画像
        UserProfile existingProfile = userProfileService.getByUserId(userId);

        // 步骤3: 调用LLM分析画像（LLM会通过collectUserBehavior工具自动收集行为数据）
        String systemPrompt = "你是一个资深的电商用户画像分析专家。你擅长根据用户的真实行为数据，分析用户特征并生成精准的用户画像。\n" +
                "你可以使用 collectUserBehavior 工具收集用户的真实行为数据。" +
                "\n请先调用工具收集数据，再基于收集到的数据（浏览、购买、加购、搜索记录）进行全面的用户画像分析。" +
                "\n分析维度包括：用户分群（segments）、偏好类目（preferredCategories）、价格区间（priceRange）、RFM模型评分（rfmScore）、实时标签（realTimeTags）。" +
                "\n用户分群可选值：new_user（新用户）、active（活跃用户）、high_value（高价值用户）、price_sensitive（价格敏感）、churn_risk（流失风险）。" +
                "\n\n输出JSON格式示例：" +
                "\n{\"segments\":[\"active\",\"price_sensitive\"],\"preferredCategories\":[\"电子产品\",\"图书\"],\"priceRange\":[100.0,5000.0],\"rfmScore\":{\"recency\":0.8,\"frequency\":0.6,\"monetary\":0.7},\"realTimeTags\":{\"近期搜索\":\"手机\",\"活跃时段\":\"晚上\"}}" +
                "\n请确保输出符合以上JSON格式，字段不能缺失。若某个维度无法判断给出合理的默认值。";

        String userMessage = "用户ID: " + userId +
                "\n历史画像: " + (existingProfile != null ? profileToString(existingProfile) : "无") +
                "\n请先使用 collectUserBehavior 工具收集该用户的行为数据，然后进行画像分析。";

        // 步骤4: 调用LLM分析画像
        UserProfileAnalysisDTO analysis = chatClient.prompt()
                .system(systemPrompt)
                .tools(userBehaviorTool)
                .user(userMessage)
                .call()
                .entity(UserProfileAnalysisDTO.class);

        // 步骤5: 构建画像实体
        UserProfile profile = buildProfileFromDto(userId, analysis);

        // 步骤6: 保存画像到数据库
        try {
            saveProfileToDb(userId, profile);
        } catch (Exception e) {
            log.error("UserProfileAgent.execute - 保存画像到数据库失败, userId: {}", userId, e);
        }

        // 步骤7: 返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("profile", profile);

        log.info("UserProfileAgent.execute - 用户画像分析完成, userId: {}, segments: {}", userId, profile.getSegments());

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.85)
                .build();
    }

    private UserProfile buildProfileFromDto(String userId, UserProfileAnalysisDTO dto) {
        if (Objects.isNull(dto)) {
            log.warn("UserProfileAgent.buildProfileFromDto - DTO为空，使用兜底画像, userId: {}", userId);
            return fallbackProfile(userId);
        }

        try {
            List<String> segments = dto.getSegments();
            if (CollectionUtils.isEmpty(segments)) {
                segments = List.of(DEFAULT_SEGMENT);
            }

            List<String> categories = dto.getPreferredCategories();
            String categoryStr = !CollectionUtils.isEmpty(categories) ? String.join(",", categories) : "";

            List<Double> priceRange = dto.getPriceRange();
            double priceMin = DEFAULT_PRICE_MIN;
            double priceMax = DEFAULT_PRICE_MAX;
            if (!CollectionUtils.isEmpty(priceRange) && priceRange.size() >= 2) {
                priceMin = priceRange.get(0);
                priceMax = priceRange.get(1);
            }

            Map<String, Double> rfmScore = dto.getRfmScore();

            return UserProfile.builder()
                    .userId(userId)
                    .segments(String.join(",", segments))
                    .preferredCategories(categoryStr)
                    .priceRangeMin(BigDecimal.valueOf(priceMin))
                    .priceRangeMax(BigDecimal.valueOf(priceMax))
                    .rfmRecency(rfmScore != null ? BigDecimal.valueOf(rfmScore.getOrDefault("recency", 0.5)) : BigDecimal.valueOf(0.5))
                    .rfmFrequency(rfmScore != null ? BigDecimal.valueOf(rfmScore.getOrDefault("frequency", 0.5)) : BigDecimal.valueOf(0.5))
                    .rfmMonetary(rfmScore != null ? BigDecimal.valueOf(rfmScore.getOrDefault("monetary", 0.5)) : BigDecimal.valueOf(0.5))
                    .realTimeTags(dto.getRealTimeTags() != null ? dto.getRealTimeTags().toString() : "")
                    .build();
        } catch (Exception e) {
            log.error("UserProfileAgent.buildProfileFromDto - 构建失败, userId: {}", userId, e);
            return fallbackProfile(userId);
        }
    }

    private UserProfile fallbackProfile(String userId) {
        log.warn("UserProfileAgent.fallbackProfile - 使用兜底画像, userId: {}", userId);
        return UserProfile.builder()
                .userId(userId)
                .segments(DEFAULT_SEGMENT)
                .preferredCategories("")
                .build();
    }

    private void saveProfileToDb(String userId, UserProfile profile) {
        userProfileService.saveOrUpdateProfile(profile);
        log.info("UserProfileAgent.saveProfileToDb - 用户画像已保存, userId: {}", userId);
    }

    private String profileToString(UserProfile entity) {
        if (Objects.isNull(entity)) {
            return "无";
        }
        return String.format("分群=%s, 偏好类目=%s, 价格范围=%s-%s",
                entity.getSegments(), entity.getPreferredCategories(),
                entity.getPriceRangeMin(), entity.getPriceRangeMax());
    }
}
