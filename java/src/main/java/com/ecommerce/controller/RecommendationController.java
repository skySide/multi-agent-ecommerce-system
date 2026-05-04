package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.enums.ErrorCode;
import com.ecommerce.dto.RecommendationRequestDTO;
import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.RecommendationResponse;
import com.ecommerce.orchestrator.SupervisorOrchestrator;
import com.ecommerce.service.ABTestService;
import com.ecommerce.service.ShoppingCartService;
import com.ecommerce.service.UserFavoriteService;
import com.ecommerce.vo.AgentResultVO;
import com.ecommerce.vo.ProductVO;
import com.ecommerce.vo.RecommendResponseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
    @Resource
    private UserFavoriteService userFavoriteService;
    @Resource
    private ShoppingCartService shoppingCartService;

    /**
     * 获取推荐商品（无需登录；有userId时返回收藏/购物车标记）
     */
    @PostMapping("/recommend")
    public Result<RecommendResponseVO> recommend(@RequestBody @Valid RecommendationRequestDTO dto) {
        log.info("RecommendationController.recommend, 用户: {}, 场景: {}, 数量: {}",
                dto.getUserId(), dto.getScene(), dto.getNumItems());
        long startTime = System.currentTimeMillis();
        try {
            RecommendationRequest request = new RecommendationRequest();
            request.setUserId(dto.getUserId());
            request.setScene(dto.getScene());
            request.setNumItems(dto.getNumItems());

            if (dto.getContext() != null && !dto.getContext().isEmpty()) {
                try {
                    request.setContext(objectMapper.readValue(dto.getContext(), Map.class));
                } catch (JsonProcessingException e) {
                    log.warn("RecommendationController.recommend, context解析失败: {}", e.getMessage());
                }
            }

            RecommendationResponse response = orchestrator.recommend(request);
            log.info("RecommendationController.recommend, 成功, 耗时: {}ms", System.currentTimeMillis() - startTime);

            RecommendResponseVO vo = convertToVO(response);
            populateUserFlags(vo.getProductVOList(), dto.getUserId());
            return Result.success(vo);
        } catch (Exception e) {
            log.error("RecommendationController.recommend 错误, 用户: {}", dto.getUserId(), e);
            return Result.error(ErrorCode.RECOMMEND_ERROR, "推荐服务异常: " + e.getMessage());
        }
    }

    private RecommendResponseVO convertToVO(RecommendationResponse r) {
        List<ProductVO> productVOs = new ArrayList<>();
        if (r.getProducts() != null) {
            productVOs = r.getProducts().stream().map(this::modelProductToVO).collect(Collectors.toList());
        }
        Map<String, AgentResultVO> agentVOs = new HashMap<>();
        if (r.getAgentResults() != null) {
            r.getAgentResults().forEach((k, v) -> agentVOs.put(k, agentResultToVO(v)));
        }
        return RecommendResponseVO.builder()
                .requestId(r.getRequestId())
                .userId(r.getUserId())
                .productVOList(productVOs)
                .marketingCopies(r.getMarketingCopies())
                .experimentGroup(r.getExperimentGroup())
                .agentResults(agentVOs)
                .totalLatencyMs(r.getTotalLatencyMs())
                .build();
    }

    private ProductVO modelProductToVO(com.ecommerce.model.Product p) {
        return ProductVO.builder()
                .productId(p.getProductId())
                .productName(p.getName())
                .productDescription(p.getDescription())
                .price(java.math.BigDecimal.valueOf(p.getPrice()))
                .stock(p.getStock())
                .rating(java.math.BigDecimal.valueOf(p.getScore()))
                .brand(p.getBrand())
                .categoryName(p.getCategory())
                .build();
    }

    private AgentResultVO agentResultToVO(com.ecommerce.model.AgentResult a) {
        return AgentResultVO.builder()
                .agentName(a.getAgentName())
                .success(a.isSuccess())
                .latencyMs(a.getLatencyMs())
                .error(a.getError())
                .data(a.getData())
                .confidence(a.getConfidence())
                .build();
    }

    private void populateUserFlags(List<ProductVO> vos, String userId) {
        if (userId == null || userId.isEmpty() || vos == null || vos.isEmpty()) return;
        try {
            Set<String> favIds = userFavoriteService.getFavoritesWithProducts(userId).stream()
                    .map(m -> (String) m.get("productId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> cartIds = shoppingCartService.getCartWithProducts(userId).stream()
                    .map(m -> (String) m.get("productId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (ProductVO vo : vos) {
                if (vo.getProductId() != null) {
                    vo.setFavorited(favIds.contains(vo.getProductId()));
                    vo.setInCart(cartIds.contains(vo.getProductId()));
                }
            }
        } catch (Exception e) {
            log.warn("RecommendationController.populateUserFlags 失败: {}", e.getMessage());
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