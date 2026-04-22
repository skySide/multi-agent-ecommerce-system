package com.ecommerce.controller;

import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;
import com.ecommerce.service.ConversationService;
import jakarta.annotation.Resource;
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

    /**
     * 发送对话消息
     */
    @PostMapping("/chat")
    public ConversationResponse chat(@RequestBody ConversationRequest request) {
        log.info("ConversationController.chat 用户={} 消息={}", request.getUserId(), request.getMessage());
        return conversationService.chat(request);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public Map<String, String> createSession(@RequestParam String userId) {
        String sessionId = conversationService.createSession(userId);
        log.info("ConversationController.createSession 用户={} 会话={}", userId, sessionId);
        return Map.of("sessionId", sessionId, "userId", userId);
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}/history")
    public List<String> getHistory(@PathVariable String sessionId) {
        log.info("ConversationController.getHistory 会话={}", sessionId);
        return conversationService.getSessionHistory(sessionId);
    }

    /**
     * 结束会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> endSession(@PathVariable String sessionId) {
        boolean success = conversationService.endSession(sessionId);
        log.info("ConversationController.endSession 会话={} 结果={}", sessionId, success);
        return Map.of("sessionId", sessionId, "success", success);
    }
}
