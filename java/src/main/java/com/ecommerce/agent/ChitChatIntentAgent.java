package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.service.MemoryService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 闲聊意图Agent
 * 处理非业务相关的自然对话
 */
@Component
public class ChitChatIntentAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private MemoryService memoryService;

    public ChitChatIntentAgent() {
        super("chitchat_intent", 5.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");

        log.info("ChitChatIntentAgent.execute - 闲聊, message: {}", message);

        // 步骤2: 构建历史上下文
        String historyContext = memoryService.buildHistoryContext(history, summary);

        // 步骤3: 调用LLM生成回复
        String prompt = String.format(
                "你是淘宝智能购物助手，友好、专业、幽默。和用户进行自然对话。\n\n" +
                        "%s用户：%s\n\n助手：",
                historyContext, message
        );

        String reply = chatClient.prompt().user(prompt).call().content();

        // 步骤4: 返回结果
        log.info("ChitChatIntentAgent.execute - 回复完成");
        return AgentResult.builder().agentName(name).success(true)
                .data(Map.of("reply", reply.trim())).confidence(1.0).build();
    }
}
