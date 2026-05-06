package com.ecommerce.task;

import com.ecommerce.entity.ConversationSession;
import com.ecommerce.service.ConversationSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对话摘要定时任务
 * 当对话轮数超过阈值时，使用 LLM 生成摘要
 * 
 * 触发条件：对话轮数 > 5 轮
 * 执行频率：每 10 分钟检查一次
 */
@Slf4j
@Component
public class ConversationSummaryTask {

    @Resource
    private ConversationSessionService conversationSessionService;

    @Resource
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 触发摘要的轮数阈值 */
    private static final int SUMMARY_THRESHOLD = 5;

    /**
     * 每 10 分钟执行一次摘要任务
     */
    @Scheduled(fixedRate = 600000)
    public void summarizeConversations() {
        log.info("ConversationSummaryTask.summarizeConversations 开始执行摘要任务");

        try {
            // 查询进行中且历史轮数超过阈值的会话
            List<ConversationSession> sessions = conversationSessionService.findSessionsNeedingSummary(SUMMARY_THRESHOLD);

            if (sessions.isEmpty()) {
                log.info("ConversationSummaryTask.summarizeConversations 没有需要摘要的会话");
                return;
            }

            log.info("ConversationSummaryTask.summarizeConversations 需要摘要的会话 = {}", sessions);

            int successCount = 0;
            for (ConversationSession session : sessions) {
                try {
                    boolean success = summarizeSession(session);
                    if (success) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("ConversationSummaryTask.summarizeConversations 摘要会话失败 sessionId={}",
                            session.getSessionId(), e);
                }
            }

            log.info("ConversationSummaryTask.summarizeConversations 完成，成功{}/{}个",
                    successCount, sessions.size());

        } catch (Exception e) {
            log.error("ConversationSummaryTask.summarizeConversations 执行失败", e);
        }
    }

    /**
     * 对单个会话生成摘要
     */
    private boolean summarizeSession(ConversationSession session) {
        try {
            String historyJson = session.getDialogueHistory();
            if (historyJson == null || historyJson.isEmpty()) {
                return false;
            }

            // 解析对话历史
            List<String> history = objectMapper.readValue(historyJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            if (history.size() <= SUMMARY_THRESHOLD * 2) {
                return false; // 轮数不足，跳过
            }

            // 构建摘要提示词
            String historyText = String.join("\n", history);
            String existingSummary = session.getSummary();

            String prompt = buildSummaryPrompt(historyText, existingSummary);

            // 调用 LLM 生成摘要
            String newSummary = chatClient.prompt().user(prompt).call().content();

            if (newSummary != null && !newSummary.trim().isEmpty()) {
                // 保存摘要
                session.setSummary(newSummary.trim());
                session.setUpdateTime(LocalDateTime.now());
                conversationSessionService.updateById(session);

                log.info("ConversationSummaryTask.summarizeSession 摘要成功 sessionId={}, 摘要长度={}",
                        session.getSessionId(), newSummary.length());
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("ConversationSummaryTask.summarizeSession 失败 sessionId={}",
                    session.getSessionId(), e);
            return false;
        }
    }

    /**
     * 构建摘要提示词
     */
    private String buildSummaryPrompt(String historyText, String existingSummary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对以下电商客服对话进行简洁摘要，提取关键信息：\n\n");

        if (existingSummary != null && !existingSummary.isEmpty()) {
            prompt.append("之前摘要：").append(existingSummary).append("\n\n");
        }

        prompt.append("对话内容：\n").append(historyText).append("\n\n");
        prompt.append("摘要要求：\n");
        prompt.append("1. 提取用户的购物需求（类目、品牌、预算等）\n");
        prompt.append("2. 记录用户关注的商品\n");
        prompt.append("3. 总结尚未解决的问题\n");
        prompt.append("4. 摘要控制在100字以内\n\n");
        prompt.append("请输出摘要内容：");

        return prompt.toString();
    }
}
