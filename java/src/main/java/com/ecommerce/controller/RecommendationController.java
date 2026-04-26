package com.ecommerce.controller;

import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.RecommendationResponse;
import com.ecommerce.orchestrator.SupervisorOrchestrator;
import com.ecommerce.service.ABTestService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 推荐 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    @Resource
    private SupervisorOrchestrator orchestrator;
    @Resource
    private ABTestService abTestService;

    /**
     * 获取推荐商品
     */
    @PostMapping("/recommend")
    public RecommendationResponse recommend(@RequestBody RecommendationRequest request) {
        log.info("RecommendationController.recommend 用户={} 场景={} 数量={}",
                request.getUserId(), request.getScene(), request.getNumItems());
        return orchestrator.recommend(request);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "language", "java", "version", "1.0.0");
    }

    /**
     * 获取实验配置
     */
    @GetMapping("/experiments")
    public Map<String, Object> getExperiments() {
        return Map.of(
                "rec_strategy", Map.of(
                        "name", "推荐策略实验",
                        "groups", Map.of("control", "rule_based", "treatment_llm", "llm_rerank")
                )
        );
    }

    /**
     * 查询用户实验组
     */
    @GetMapping("/experiments/assign")
    public Map<String, Object> assignExperiment(@RequestParam String userId,
                                                 @RequestParam(required = false) String experimentId) {
        log.info("RecommendationController.assignExperiment 用户={} 实验={}", userId, experimentId);
        if (experimentId != null && !experimentId.isEmpty()) {
            return abTestService.assign(userId, experimentId);
        }
        return abTestService.assign(userId);
    }
}
