package com.ecommerce.tool;

import com.ecommerce.entity.UserBehavior;
import com.ecommerce.service.UserBehaviorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户行为收集工具
 * 用于 AI Agent 收集和分析用户行为数据
 * 自动过滤误操作行为（如单次浏览等无效行为）
 */
@Component
public class UserBehaviorTool {

    private static final Logger log = LoggerFactory.getLogger(UserBehaviorTool.class);

    @Resource
    private UserBehaviorService userBehaviorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最近行为数据查询数量 */
    private static final int RECENT_BEHAVIOR_LIMIT = 100;

    /** 最近浏览展示数量 */
    private static final int RECENT_VIEWS_LIMIT = 10;

    /** 最近购买展示数量 */
    private static final int RECENT_PURCHASE_LIMIT = 10;

    /** 最近加购展示数量 */
    private static final int RECENT_CART_LIMIT = 5;

    /** 最近搜索展示数量 */
    private static final int RECENT_SEARCH_LIMIT = 10;

    /** 有效浏览最低次数：低于此次数视为误操作过滤掉 */
    private static final int MIN_VIEW_COUNT_FOR_VALID = 2;

    /**
     * 收集指定用户的浏览、购买、加购、搜索等行为数据
     *
     * @param userId 用户ID
     * @return 用户行为数据的JSON字符串
     */
    @Tool(name = "collectUserBehavior",
            description = "收集指定用户的浏览、购买、加购、搜索等行为数据，用于用户画像分析")
    public String collectUserBehavior(
            @ToolParam(description = "用户ID，需要收集行为数据的用户标识") String userId) {
        // 步骤1: 参数校验
        if (Objects.isNull(userId)) {
            log.error("UserBehaviorTool.collectUserBehavior - 参数为空, userId: null");
            return "{}";
        }

        log.info("UserBehaviorTool.collectUserBehavior - 收集用户行为, userId: {}", userId);

        // 步骤2: 查询用户行为记录
        List<UserBehavior> allBehaviors = userBehaviorService.listRecentByUserId(userId, RECENT_BEHAVIOR_LIMIT);

        // 步骤3: 过滤误操作行为
        List<UserBehavior> filteredBehaviors = filterAccidentalBehaviors(allBehaviors);

        // 步骤4: 按行为类型分组整理
        Map<String, Object> behavior = new HashMap<>();
        behavior.put("user_id", userId);
        behavior.put("total_behavior_count", filteredBehaviors.size());
        behavior.put("filtered_count", allBehaviors.size() - filteredBehaviors.size());

        List<String> recentViews = filteredBehaviors.stream()
                .filter(b -> "view".equals(b.getBehaviorType()))
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(RECENT_VIEWS_LIMIT)
                .collect(Collectors.toList());

        List<String> recentPurchases = filteredBehaviors.stream()
                .filter(b -> "purchase".equals(b.getBehaviorType()))
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(RECENT_PURCHASE_LIMIT)
                .collect(Collectors.toList());

        List<String> recentCarts = filteredBehaviors.stream()
                .filter(b -> "cart".equals(b.getBehaviorType()))
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(RECENT_CART_LIMIT)
                .collect(Collectors.toList());

        List<String> searchKeywords = filteredBehaviors.stream()
                .map(UserBehavior::getSearchKeyword)
                .filter(Objects::nonNull)
                .distinct()
                .limit(RECENT_SEARCH_LIMIT)
                .collect(Collectors.toList());

        behavior.put("recent_views", recentViews);
        behavior.put("recent_purchases", recentPurchases);
        behavior.put("recent_carts", recentCarts);
        behavior.put("search_keywords", searchKeywords);

        if (!recentPurchases.isEmpty()) {
            behavior.put("purchase_count", recentPurchases.size());
        }

        log.info("UserBehaviorTool.collectUserBehavior - 收集完成, userId: {}, 原始:{}条, 过滤后:{}条, 浏览:{}条, 购买:{}条",
                userId, allBehaviors.size(), filteredBehaviors.size(), recentViews.size(), recentPurchases.size());

        // 步骤5: 序列化返回
        try {
            String jsonStr = objectMapper.writeValueAsString(behavior);
            log.info("UserBehaviorTool.collectUserBehavior end, behaviorJSONStr = {}", jsonStr);
            return jsonStr;
        } catch (JsonProcessingException e) {
            log.error("UserBehaviorTool.collectUserBehavior - 序列化失败, userId: {}", userId, e);
            return "{}";
        }
    }

    /**
     * 过滤误操作行为
     * 规则：
     * 1. 对于浏览行为：同一商品仅被浏览1次的视为误操作，过滤掉
     * 2. 购买和加购行为：属于强信号，保留
     * 3. 搜索行为：保留
     */
    private List<UserBehavior> filterAccidentalBehaviors(List<UserBehavior> behaviors) {
        if (behaviors.isEmpty()) {
            return behaviors;
        }

        // 步骤1: 统计每个商品被浏览的次数
        Map<String, Long> viewCountByProduct = behaviors.stream()
                .filter(b -> "view".equals(b.getBehaviorType()))
                .filter(b -> Objects.nonNull(b.getProductId()))
                .collect(Collectors.groupingBy(UserBehavior::getProductId, Collectors.counting()));

        // 步骤2: 标记需要过滤的商品ID（仅被浏览1次的）
        Set<String> singleViewProducts = viewCountByProduct.entrySet().stream()
                .filter(entry -> entry.getValue() < MIN_VIEW_COUNT_FOR_VALID)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // 步骤3: 过滤行为列表
        List<UserBehavior> filtered = new ArrayList<>();
        int filteredCount = 0;

        for (UserBehavior behavior : behaviors) {
            boolean shouldFilter = "view".equals(behavior.getBehaviorType())
                    && Objects.nonNull(behavior.getProductId())
                    && singleViewProducts.contains(behavior.getProductId());

            if (shouldFilter) {
                filteredCount++;
            } else {
                filtered.add(behavior);
            }
        }

        if (filteredCount > 0) {
            log.info("UserBehaviorTool.filterAccidentalBehaviors - 过滤了{}条误操作行为", filteredCount);
        }

        return filtered;
    }
}
