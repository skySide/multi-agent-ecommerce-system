package com.ecommerce.service.impl;

import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;
import com.ecommerce.common.constants.QualityConstants;
import com.ecommerce.service.ConversationService;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.SessionQualityMetricsService;
import com.ecommerce.agent.ConversationAgent;
import com.ecommerce.vo.SessionSummaryVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 * 薄层委托 ConversationAgent 进行意图识别和子Agent路由
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ConversationAgent conversationAgent;

    @Resource
    private ConversationSessionService conversationSessionService;

    @Resource
    private MemoryService memoryService;

    @Resource
    private SessionQualityMetricsService sessionQualityMetricsService;

    @Override
    public ConversationResponse chat(ConversationRequest request) {
        // 步骤1: 提取参数
        String userId = request.getUserId();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        log.info("ConversationService.chat - 用户={}, 会话={}, 消息={}", userId, sessionId, message);

        // 步骤2: 获取或创建会话（确保sessionId不为空）
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = createSession(userId);
        }
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null) {
            log.warn("ConversationService.chat - 会话不存在, sessionId={}, 重新创建", sessionId);
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        } else if (!userId.equals(session.getUserId())) {
            log.warn("ConversationService.chat - 会话归属校验失败, sessionId={} 属于 userId={}, 请求 userId={}",
                    sessionId, session.getUserId(), userId);
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        }

        // 步骤2.5: 会话恢复检测（status=0 的会话收到新消息时重新激活）
        if (session.getStatus() != null && session.getStatus() == 0) {
            LocalDateTime lastUpdate = session.getUpdateTime();
            long gapMinutes = lastUpdate != null
                    ? Duration.between(lastUpdate, LocalDateTime.now()).toMinutes()
                    : 0;
            session.setStatus(1);
            conversationSessionService.updateById(session);
            log.info("ConversationService.chat - 会话重新激活, sessionId={}, gapMinutes={}", sessionId, gapMinutes);
            if (gapMinutes > QualityConstants.SESSION_TIMEOUT_MINUTES) {
                String metricValue = String.format(
                        "{\"gap_minutes\":%d,\"previous_status\":0}", gapMinutes);
                Integer messageIndex = conversationSessionService.resolveLatestMessageIndex(session);
                sessionQualityMetricsService.recordMetric(sessionId, userId,
                        QualityConstants.METRIC_SESSION_RESUMED, metricValue, messageIndex);
            }
        }

        // 步骤3: 获取短期记忆
        List<String> history = memoryService.deserializeHistory(session.getDialogueHistory());
        String summary = session.getSummary();

        // 步骤4: 调用 ConversationAgent 处理（sessionId 此时一定不为空）
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("sessionId", sessionId);
        params.put("message", message);
        params.put("history", history);
        params.put("summary", summary);

        AgentResult result;
        try {
            result = conversationAgent.runAndTrack(sessionId, params).join();
        } catch (CancellationException e) {
            log.info("ConversationService.chat - 用户取消生成, sessionId: {}", sessionId);
            return ConversationResponse.builder()
                    .sessionId(sessionId)
                    .message("已停止生成")
                    .timestamp(java.time.Instant.now())
                    .build();
        }

        if (!result.isSuccess() || result.getData() == null) {
            log.error("ConversationService.chat - 对话处理失败, userId: {}", userId);
            return ConversationResponse.builder()
                    .sessionId(sessionId)
                    .message("抱歉，我暂时无法处理您的请求，请稍后再试。")
                    .timestamp(java.time.Instant.now())
                    .build();
        }

        return buildResponse(result);
    }

    @Override
    public String createSession(String userId) {
        ConversationSession session = conversationSessionService.createSession(userId);
        log.info("ConversationService.createSession - 创建会话, userId={}, sessionId={}", userId, session.getSessionId());
        return session.getSessionId();
    }

    @Override
    public List<String> getSessionHistory(String sessionId) {
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null) {
            return List.of();
        }
        return memoryService.deserializeHistory(session.getDialogueHistory());
    }

    @Override
    public boolean endSession(String sessionId) {
        log.info("ConversationService.endSession - 结束会话, sessionId={}", sessionId);
        return conversationSessionService.endSession(sessionId);
    }

    @Override
    public boolean cancelGeneration(String sessionId) {
        // 步骤1: 委托 ConversationAgent 取消执行
        log.info("ConversationService.cancelGeneration - 取消生成, sessionId={}", sessionId);
        return conversationAgent.cancelGeneration(sessionId);
    }

    @Override
    public List<SessionSummaryVO> listUserSessions(String userId) {
        // 步骤1: 参数校验
        if (userId == null || userId.isEmpty()) {
            log.warn("ConversationService.listUserSessions - userId为空");
            return Collections.emptyList();
        }

        // 步骤2: 查询用户最近20条会话
        List<ConversationSession> sessions = conversationSessionService.listRecentByUserId(userId, 20);

        // 步骤3: 转换为VO，计算每轮消息数
        List<SessionSummaryVO> result = sessions.stream().map(session -> {
            List<String> history = memoryService.deserializeHistory(session.getDialogueHistory());
            int roundCount = history.size() / 2; // 每轮包含用户+助手两条消息

            return SessionSummaryVO.builder()
                    .sessionId(session.getSessionId())
                    .userId(session.getUserId())
                    .summary(session.getSummary())
                    .status(session.getStatus())
                    .roundCount(roundCount)
                    .createTime(session.getCreateTime())
                    .updateTime(session.getUpdateTime())
                    .build();
        }).collect(Collectors.toList());

        log.info("ConversationService.listUserSessions - 查询成功, userId={}, 会话数={}", userId, result.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private ConversationResponse buildResponse(AgentResult result) {
        Map<String, Object> data = result.getData();

        String sessionId = (String) data.getOrDefault("sessionId", "");
        String reply = (String) data.getOrDefault("reply", "");
        String intent = (String) data.getOrDefault("intent", "chitchat");
        List<String> dialogueHistory = (List<String>) data.get("dialogueHistory");
        String summary = (String) data.get("summary");

        List<Product> products = Collections.emptyList();
        Object productsObj = data.get("products");
        if (productsObj instanceof List) {
            products = (List<Product>) productsObj;
        }

        Map<String, Object> extractedInfo = Collections.emptyMap();
        Object entitiesObj = data.get("entities");
        if (entitiesObj instanceof Map) {
            extractedInfo = (Map<String, Object>) entitiesObj;
        }

        // 步骤1: 提取子任务结果（多意图场景）
        List<ConversationResponse.SubTaskResult> subTasks = Collections.emptyList();
        Object subTasksObj = data.get("subTasks");
        if (subTasksObj instanceof List) {
            List<?> rawList = (List<?>) subTasksObj;
            subTasks = rawList.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> {
                        Map<String, Object> taskMap = (Map<String, Object>) item;
                        return ConversationResponse.SubTaskResult.builder()
                                .agentName((String) taskMap.getOrDefault("agentName", ""))
                                .success((boolean) taskMap.getOrDefault("success", false))
                                .latencyMs(((Number) taskMap.getOrDefault("latencyMs", 0)).doubleValue())
                                .reply((String) taskMap.getOrDefault("reply", ""))
                                .build();
                    })
                    .toList();
        }

        return ConversationResponse.builder()
                .sessionId(sessionId)
                .message(reply)
                .intent(intent)
                .recommendedProducts(products)
                .extractedInfo(extractedInfo)
                .dialogueHistory(dialogueHistory)
                .summary(summary)
                .subTasks(subTasks)
                .timestamp(java.time.Instant.now())
                .build();
    }
}
