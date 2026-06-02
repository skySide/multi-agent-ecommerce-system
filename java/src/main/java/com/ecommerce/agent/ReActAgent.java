package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.service.MemoryService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ReAct Agent 抽象基类
 * 所有意图子Agent 统一继承此类，遵循 Observe → Think → Act 循环。
 *
 * 流程：
 *   ① 子类通过 buildSystemPrompt / buildUserMessage 构建提示词
 *   ② 通过 registerTool 注册可用工具
 *   ③ execute() 调用 LLM（Spring AI 内部处理 Tool Calling）
 *   ④ 上限 3 轮防死循环，每轮前检查线程中断标志
 *
 * 简单 Agent（如 ChitChat）一轮收敛，复杂 Agent（如 Recommend）多轮迭代。
 */
public abstract class ReActAgent extends BaseAgent {

    /** ReAct 最大迭代轮数 */
    protected static final int MAX_ITERATIONS = 3;

    @Resource
    protected ChatClient chatClient;

    @Resource
    protected MemoryService memoryService;

    /** 子类注册的工具列表 */
    private final List<Object> tools = new ArrayList<>();

    protected ReActAgent(String name, double timeoutSeconds, int maxRetries) {
        super(name, timeoutSeconds, maxRetries);
    }

    // ==================== 子类须实现的抽象方法 ====================

    /**
     * 构建系统提示词
     */
    protected abstract String buildSystemPrompt(Map<String, Object> params);

    /**
     * 构建用户消息（含上下文、实体等信息）
     */
    protected abstract String buildUserMessage(Map<String, Object> params);

    /**
     * 从 LLM 最终回复构建 AgentResult
     */
    protected abstract AgentResult buildResult(String llmResponse, Map<String, Object> params);

    // ==================== Tool 注册 ====================

    /**
     * 注册一个 Tool（子类在构造或 @PostConstruct 中调用）
     */
    protected void registerTool(Object tool) {
        this.tools.add(tool);
    }

    /**
     * 获取已注册的工具列表
     */
    protected List<Object> getTools() {
        return new ArrayList<>(tools);
    }

    // ==================== 子类可选重写的钩子方法 ====================

    /**
     * 调用 LLM 并提取响应文本
     * 子类可重写此方法以支持 .entity() 结构化输出（如 RecommendIntentAgent 返回 RecResult）
     * 默认使用 .call().content() 获取纯文本回复
     */
    protected String callLlm(ChatClient.ChatClientRequestSpec spec) {
        return spec.call().content();
    }

    // ==================== ReAct 循环入口 ====================

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 构建系统提示词和用户消息
        String systemPrompt = buildSystemPrompt(params);
        String userMessage = buildUserMessage(params);

        long startTime = System.currentTimeMillis();
        int iteration = 0;
        String currentInput = userMessage;

        // 步骤2: ReAct 循环
        while (iteration < MAX_ITERATIONS && !Thread.currentThread().isInterrupted()) {
            iteration++;
            log.info("ReActAgent.execute - 第{}轮 Think 开始, agentName: {}, maxIterations: {}",
                    iteration, name, MAX_ITERATIONS);

            // 步骤2a: 构建 ChatClient 请求
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                    .system(systemPrompt)
                    .user(currentInput);

            if (!tools.isEmpty()) {
                spec.tools(tools.toArray());
            }

            // 步骤2b: Think + Act → 调用 LLM
            // Spring AI 内部处理 Tool Calling：LLM 决定调 Tool → 框架执行 → 结果回填 → 最终输出
            String response = callLlm(spec);

            log.info("ReActAgent.execute - 第{}轮 Observe, agentName: {}, responseLength: {}",
                    iteration, name,
                    Objects.nonNull(response) ? response.length() : 0);

            // 步骤2c: 判断是否终止——响应非空则本轮收敛
            if (StringUtils.isNotBlank(response)) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("ReActAgent.execute - ReAct 循环完成, agentName: {}, totalIterations: {}, elapsedMs: {}",
                        name, iteration, elapsed);

                AgentResult result = buildResult(response.trim(), params);
                result.setAgentName(name);
                return result;
            }

            // 步骤2d: 响应为空，追加提示后继续循环
            log.warn("ReActAgent.execute - 第{}轮响应为空，继续循环, agentName: {}", iteration, name);
            currentInput = userMessage + "\n\n请提供一个明确的回答。";
        }

        // 步骤3: 处理异常终止
        if (Thread.currentThread().isInterrupted()) {
            log.warn("ReActAgent.execute - 检测到线程中断，终止执行, agentName: {}", name);
            return fallback(System.currentTimeMillis() - startTime,
                    new InterruptedException("Agent execution interrupted"));
        }

        log.warn("ReActAgent.execute - 达到最大迭代次数，强制终止, agentName: {}, maxIterations: {}",
                name, MAX_ITERATIONS);
        return buildResult("抱歉，处理您的请求超时，请稍后再试。", params);
    }
}
