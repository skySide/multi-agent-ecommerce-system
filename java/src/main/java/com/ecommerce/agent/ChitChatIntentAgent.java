package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 闲聊意图Agent
 * 基于 ReAct 模式（单轮 Think→Act→Observe）：
 * Think：理解用户语境 → Act：调用 LLM 生成闲聊回复 → Observe：返回结果
 * 处理非业务相关的自然对话
 */
@Component
public class ChitChatIntentAgent extends ReActAgent {

    public ChitChatIntentAgent() {
        super("chitchat_intent", 5.0, 2);
    }

    @Override
    public com.ecommerce.model.AgentCard getAgentCard() {
        return com.ecommerce.model.AgentCard.builder()
                .name("chitchat_intent")
                .description("处理闲聊对话，友好、专业地回应用户的非业务问题")
                .capabilities(List.of(
                        com.ecommerce.model.AgentCapability.builder()
                                .id("chitchat")
                                .name("闲聊对话")
                                .description("【端到端】处理问候、感谢、闲聊等非业务相关的自然对话。无需任何前置任务")
                                .tags(List.of("chitchat", "greeting"))
                                .build()
                ))
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "message", Map.of("type", "string", "description", "用户消息")
                        ),
                        "required", List.of()
                ))
                .outputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "reply", Map.of("type", "string", "description", "闲聊回复")
                        )
                ))
                .build();
    }

    // ==================== ReActAgent 抽象方法实现 ====================

    @Override
    protected String buildSystemPrompt(Map<String, Object> params) {
        return "你是淘宝智能购物助手，友好、专业、幽默。和用户进行自然对话。";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildUserMessage(Map<String, Object> params) {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        // 步骤2: 构建历史上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);

        return String.format("%s用户：%s\n\n助手：", historyContext, message);
    }

    @Override
    protected AgentResult buildResult(String llmResponse, Map<String, Object> params) {
        // 步骤1: 提取回复文本
        String reply = llmResponse != null ? llmResponse : "抱歉，我不太明白您的意思。";

        log.info("ChitChatIntentAgent.buildResult - 回复完成, replyLength: {}", reply.length());
        return AgentResult.builder().agentName(name).success(true)
                .data(Map.of("reply", reply)).confidence(1.0).build();
    }
}
