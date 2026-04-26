package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.enums.ErrorCode;
import com.ecommerce.dto.BehaviorRecordDTO;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.service.UserBehaviorService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
    public Result<Map<String, Object>> recordBehavior(@RequestBody @Valid BehaviorRecordDTO dto) {
        log.info("BehaviorController.recordBehavior, 用户: {}, 行为: {}, 商品: {}",
                dto.getUserId(), dto.getBehaviorType(), dto.getProductId());
        boolean success = userBehaviorService.recordBehavior(
                dto.getUserId(),
                dto.getProductId(),
                dto.getBehaviorType(),
                dto.getSearchKeyword(),
                dto.getReferrer()
        );
        log.info("BehaviorController.recordBehavior, 结果: {}", success);
        if (success) {
            return Result.success(Map.of("userId", dto.getUserId(), "behaviorType", dto.getBehaviorType()));
        }
        return Result.error(ErrorCode.BEHAVIOR_ERROR, "记录行为失败");
    }

    /**
     * 获取用户最近行为
     */
    @GetMapping("/{userId}/recent")
    public Result<List<UserBehavior>> getRecentBehaviors(@PathVariable String userId,
                                                         @RequestParam(defaultValue = "20") int limit) {
        log.info("BehaviorController.getRecentBehaviors, 用户: {}, 数量: {}", userId, limit);
        List<UserBehavior> behaviors = userBehaviorService.listRecentByUserId(userId, limit);
        return Result.success(behaviors);
    }

    /**
     * 获取用户特定类型行为
     */
    @GetMapping("/{userId}/type/{behaviorType}")
    public Result<List<UserBehavior>> getBehaviorsByType(@PathVariable String userId,
                                                         @PathVariable String behaviorType,
                                                         @RequestParam(defaultValue = "20") int limit) {
        log.info("BehaviorController.getBehaviorsByType, 用户: {}, 类型: {}, 数量: {}", userId, behaviorType, limit);
        List<UserBehavior> behaviors = userBehaviorService.listByUserIdAndType(userId, behaviorType, limit);
        return Result.success(behaviors);
    }
}