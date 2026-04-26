package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.enums.ErrorCode;
import com.ecommerce.dto.RecommendationRequestDTO;
import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.RecommendationResponse;
import com.ecommerce.orchestrator.SupervisorOrchestrator;
import com.ecommerce.service.ABTestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 获取推荐商品
     */
    @PostMapping("/recommend")
    public Result<RecommendationResponse> recommend(@RequestBody @Valid RecommendationRequestDTO dto) {
        log.info("RecommendationController.recommend, 用户: {}, 场景: {}, 数量: {}",
                dto.getUserId(), dto.getScene(), dto.getNumItems());
        long startTime = System.currentTimeMillis();
        try {
            // 转换为原有模型
            RecommendationRequest request = new RecommendationRequest();
            request.setUserId(dto.getUserId());
            request.setScene(dto.getScene());
            request.setNumItems(dto.getNumItems());
            
            // 转换context类型
            if (dto.getContext() != null && !dto.getContext().isEmpty()) {
                try {
                    Map<String, Object> contextMap = objectMapper.readValue(dto.getContext(), Map.class);
                    request.setContext(contextMap);
                } catch (JsonProcessingException e) {
                    log.warn("RecommendationController.recommend, context解析失败: {}", e.getMessage());
                    // 解析失败时设置为null
                    request.setContext(null);
                }
            }
            
            RecommendationResponse response = orchestrator.recommend(request);
            log.info("RecommendationController.recommend, 成功, 耗时: {}ms", System.currentTimeMillis() - startTime);
            return Result.success(response);
        } catch (Exception e) {
            log.error("RecommendationController.recommend 错误, 用户: {}", dto.getUserId(), e);
            return Result.error(ErrorCode.RECOMMEND_ERROR, "推荐服务异常: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        log.info("RecommendationController.health");
        return Result.success(Map.of("status", "healthy", "language", "java", "version", "1.0.0"));
    }

    /**
     * 获取实验配置
     */
    @GetMapping("/experiments")
    public Result<Map<String, Object>> getExperiments() {
        log.info("RecommendationController.getExperiments");
        return Result.success(Map.of(
                "rec_strategy", Map.of(
                        "name", "推荐策略实验",
                        "groups", Map.of("control", "rule_based", "treatment_llm", "llm_rerank")
                )
        ));
    }

    /**
     * 查询用户实验组
     */
    @GetMapping("/experiments/assign")
    public Result<Map<String, Object>> assignExperiment(@RequestParam String userId,
                                                 @RequestParam(required = false) String experimentId) {
        log.info("RecommendationController.assignExperiment, 用户: {}, 实验: {}", userId, experimentId);
        if (experimentId != null && !experimentId.isEmpty()) {
            return Result.success(abTestService.assign(userId, experimentId));
        }
        return Result.success(abTestService.assign(userId));
    }
}