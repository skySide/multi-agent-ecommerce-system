package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base agent with retry, timeout, fallback, and metrics.
 * All agents extend this class.
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final String name;
    protected final double timeoutSeconds;
    protected final int maxRetries;

    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    protected BaseAgent(String name, double timeoutSeconds, int maxRetries) {
        this.name = name;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }

    /**
     * 获取 Agent 名称
     */
    public String getName() {
        return name;
    }

    protected abstract AgentResult execute(Map<String, Object> params) throws Exception;

    /**
     * 返回 Agent Card（A2A Protocol）
     * 声明 Agent 的能力、输入输出模式，供 AgentRegistry 注册和 LLM 能力匹配。
     *
     * 默认返回 null——不需要参与 A2A 编排的 Agent 无需重写。
     * 需要被 A2AOrchestrator 调度的 Agent 必须重写此方法。
     */
    public com.ecommerce.model.AgentCard getAgentCard() {
        return null;
    }

    public CompletableFuture<AgentResult> runAsync(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            callCount.incrementAndGet();
            long start = System.nanoTime();
            int attempt = 0;
            Exception lastError = null;

            while (attempt < maxRetries) {
                try {
                    AgentResult result = execute(params);
                    double latency = (System.nanoTime() - start) / 1_000_000.0;
                    result.setLatencyMs(latency);
                    log.info("BaseAgent.runAsync {}.execute success in {:.1f}ms", name, latency);
                    return result;
                } catch (Exception e) {
                    lastError = e;
                    attempt++;
                    log.warn("BaseAgent.runAsync {}.execute attempt {} failed: {}", name, attempt, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep((long) (500 * Math.pow(2, attempt - 1)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            errorCount.incrementAndGet();
            double latency = (System.nanoTime() - start) / 1_000_000.0;
            return fallback(latency, lastError);
        });
    }

    protected AgentResult fallback(double latencyMs, Exception e) {
        return AgentResult.builder()
                .agentName(name)
                .success(false)
                .latencyMs(latencyMs)
                .error(e != null ? e.getMessage() : "unknown error")
                .confidence(0.0)
                .build();
    }

    public double getErrorRate() {
        int calls = callCount.get();
        return calls == 0 ? 0.0 : (double) errorCount.get() / calls;
    }
}
