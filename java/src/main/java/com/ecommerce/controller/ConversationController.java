package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.enums.ErrorCodeEnum;
import com.ecommerce.dto.ConversationRequestDTO;
import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;
import com.ecommerce.service.ConversationService;
import com.ecommerce.service.SessionQualityMetricsService;
import com.ecommerce.vo.SessionCreateVO;
import com.ecommerce.vo.SessionEndVO;
import com.ecommerce.vo.SessionSummaryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话 Controller
 * 多轮对话 + 对话式推荐 + RAG问答
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversation")
public class ConversationController {

    @Resource
    private ConversationService conversationService;
    @Resource
    private SessionQualityMetricsService sessionQualityMetricsService;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 发送对话消息
     */
    @PostMapping("/chat")
    public Result<ConversationResponse> chat(@RequestBody @Valid ConversationRequestDTO dto) {
        log.info("ConversationController.chat, 用户: {}, 消息: {}", dto.getUserId(), dto.getMessage());
        long startTime = System.currentTimeMillis();
        try {
            // 转换为原有模型
            ConversationRequest request = new ConversationRequest();
            request.setUserId(dto.getUserId());
            request.setMessage(dto.getMessage());
            request.setSessionId(dto.getSessionId());

            // 转换context类型
            if (dto.getContext() != null && !dto.getContext().isEmpty()) {
                try {
                    Map<String, Object> contextMap = objectMapper.readValue(dto.getContext(), Map.class);
                    request.setContext(contextMap);
                } catch (JsonProcessingException e) {
                    log.warn("ConversationController.chat, context解析失败: {}", e.getMessage());
                    // 解析失败时设置为null
                    request.setContext(null);
                }
            }

            ConversationResponse response = conversationService.chat(request);
            log.info("ConversationController.chat, 成功, 耗时: {}ms", System.currentTimeMillis() - startTime);
            return Result.success(response);
        } catch (Exception e) {
            log.error("ConversationController.chat 错误, 用户: {}", dto.getUserId(), e);
            return Result.error(ErrorCodeEnum.CONVERSATION_ERROR, "对话服务异常: " + e.getMessage());
        }
    }

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public Result<SessionCreateVO> createSession(@RequestParam @NotBlank String userId) {
        log.info("ConversationController.createSession, 用户: {}", userId);
        String sessionId = conversationService.createSession(userId);
        log.info("ConversationController.createSession, 会话: {}", sessionId);
        return Result.success(SessionCreateVO.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build());
    }

    /**
     * 获取用户的历史会话列表
     */
    @GetMapping("/sessions")
    public Result<List<SessionSummaryVO>> listSessions(@RequestParam @NotBlank String userId) {
        log.info("ConversationController.listSessions, 用户: {}", userId);
        List<SessionSummaryVO> sessions = conversationService.listUserSessions(userId);
        return Result.success(sessions);
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}/history")
    public Result<List<String>> getHistory(@PathVariable String sessionId) {
        log.info("ConversationController.getHistory, 会话: {}", sessionId);
        List<String> history = conversationService.getSessionHistory(sessionId);
        return Result.success(history);
    }

    /**
     * 结束会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<SessionEndVO> endSession(@PathVariable String sessionId) {
        log.info("ConversationController.endSession, 会话: {}", sessionId);
        boolean success = conversationService.endSession(sessionId);
        log.info("ConversationController.endSession, 结果: {}", success);
        if (success) {
            return Result.success(SessionEndVO.builder()
                    .sessionId(sessionId)
                    .success(true)
                    .build());
        }
        return Result.error(ErrorCodeEnum.CONVERSATION_ERROR, "结束会话失败");
    }

    /**
     * 取消正在生成的会话
     */
    @PostMapping("/{sessionId}/cancel")
    public Result<SessionEndVO> cancelGeneration(@PathVariable String sessionId) {
        log.info("ConversationController.cancelGeneration, 会话: {}", sessionId);
        boolean success = conversationService.cancelGeneration(sessionId);
        log.info("ConversationController.cancelGeneration, 结果: {}", success);
        return Result.success(SessionEndVO.builder()
                .sessionId(sessionId)
                .success(success)
                .build());
    }

    /**
     * 上报会话突然结束
     */
    @PostMapping("/{sessionId}/abandon")
    public Result<SessionEndVO> abandonSession(@PathVariable String sessionId,
                                                @RequestParam(required = false) String userId) {
        log.info("ConversationController.abandonSession, 会话: {}, 用户: {}", sessionId, userId);
        // 步骤1: 结束会话
        conversationService.endSession(sessionId);
        // 步骤2: 记录质量事件
        if (userId != null && !userId.isEmpty()) {
            sessionQualityMetricsService.recordAbruptEnd(sessionId, userId,
                    "{\"source\":\"frontend_unmount\"}");
        }
        return Result.success(SessionEndVO.builder()
                .sessionId(sessionId)
                .success(true)
                .build());
    }
}