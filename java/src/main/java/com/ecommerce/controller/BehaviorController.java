package com.ecommerce.controller;

import com.ecommerce.entity.UserBehavior;
import com.ecommerce.service.UserBehaviorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户行为 Controller
 * 行为采集 + 查询
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/behaviors")
public class BehaviorController {

    @Resource
    private UserBehaviorService userBehaviorService;

    /**
     * 记录用户行为
     */
    @PostMapping("/record")
    public Map<String, Object> recordBehavior(@RequestParam String userId,
                                               @RequestParam(required = false) String productId,
                                               @RequestParam String behaviorType,
                                               @RequestParam(required = false) String searchKeyword,
                                               @RequestParam(required = false) String referrer) {
        boolean success = userBehaviorService.recordBehavior(userId, productId, behaviorType, searchKeyword, referrer);
        log.info("BehaviorController.recordBehavior 用户={} 行为={} 商品={} 结果={}",
                userId, behaviorType, productId, success);
        return Map.of("success", success, "userId", userId, "behaviorType", behaviorType);
    }

    /**
     * 获取用户最近行为
     */
    @GetMapping("/{userId}/recent")
    public List<UserBehavior> getRecentBehaviors(@PathVariable String userId,
                                                  @RequestParam(defaultValue = "20") int limit) {
        log.info("BehaviorController.getRecentBehaviors 用户={} 数量={}", userId, limit);
        return userBehaviorService.listRecentByUserId(userId, limit);
    }

    /**
     * 获取用户特定类型行为
     */
    @GetMapping("/{userId}/type/{behaviorType}")
    public List<UserBehavior> getBehaviorsByType(@PathVariable String userId,
                                                  @PathVariable String behaviorType,
                                                  @RequestParam(defaultValue = "20") int limit) {
        log.info("BehaviorController.getBehaviorsByType 用户={} 类型={} 数量={}", userId, behaviorType, limit);
        return userBehaviorService.listByUserIdAndType(userId, behaviorType, limit);
    }
}
