package com.ecommerce.agent;

import com.ecommerce.model.AgentCapability;
import com.ecommerce.model.AgentCard;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 注册中心（A2A Protocol）
 *
 * 所有 Agent 通过 getAgentCard() 声明能力，
 * AgentRegistry 在启动时自动收集，支持按 capabilityId 或标签查找 Agent。
 *
 * 新增 Agent 只需：
 * 1. 实现 getAgentCard() 返回 AgentCard
 * 2. 标注 @Component 让 Spring 管理
 * → AgentRegistry 自动发现，无需修改任何配置。
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    @Resource
    private List<BaseAgent> agents;

    /** agentName → AgentCard */
    private final Map<String, AgentCard> cards = new ConcurrentHashMap<>();

    /** agentName → BaseAgent */
    private final Map<String, BaseAgent> agentInstances = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (BaseAgent agent : agents) {
            AgentCard card = agent.getAgentCard();
            if (card != null) {
                cards.put(card.getName(), card);
                agentInstances.put(card.getName(), agent);
                log.info("AgentRegistry.init - 注册 Agent: name={}, capabilities={}",
                        card.getName(),
                        card.getCapabilities() != null
                                ? card.getCapabilities().stream().map(AgentCapability::getId).toList()
                                : "[]");
            }
        }
        log.info("AgentRegistry.init - 注册完成, registeredAgents: {}", agentInstances.size());
    }

    /**
     * 根据 capabilityId 查找匹配的 Agent Card
     * 优先精确匹配 capability.id，其次匹配 capability.tags
     */
    public List<AgentCard> findByCapability(String capabilityId) {
        if (capabilityId == null || capabilityId.isBlank()) {
            return Collections.emptyList();
        }

        List<AgentCard> result = cards.values().stream()
                .filter(card -> card.getCapabilities() != null && card.getCapabilities().stream()
                        .anyMatch(cap -> capabilityId.equals(cap.getId())
                                || (cap.getTags() != null && cap.getTags().contains(capabilityId))))
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            log.warn("AgentRegistry.findByCapability - 未找到匹配的 Agent, capabilityId: {}", capabilityId);
        }
        return result;
    }

    /**
     * 根据 Agent 名称获取 Agent 实例
     */
    public BaseAgent getAgent(String agentName) {
        BaseAgent agent = agentInstances.get(agentName);
        if (agent == null) {
            log.warn("AgentRegistry.getAgent - 未找到 Agent, agentName: {}", agentName);
        }
        return agent;
    }

    /**
     * 获取所有已注册的 Agent Card（供 LLM Prompt 使用）
     */
    public List<AgentCard> getAllCards() {
        return new ArrayList<>(cards.values());
    }

    /**
     * 获取所有已注册的能力 ID 列表
     */
    public List<String> getAllCapabilityIds() {
        return cards.values().stream()
                .filter(card -> card.getCapabilities() != null)
                .flatMap(card -> card.getCapabilities().stream())
                .map(AgentCapability::getId)
                .distinct()
                .toList();
    }

    /**
     * 获取已注册 Agent 数量（用于健康检查）
     */
    public int getRegisteredCount() {
        return agentInstances.size();
    }
}
