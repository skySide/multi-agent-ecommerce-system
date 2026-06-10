package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 能力卡片（Google A2A Protocol 风格）
 *
 * 每个 Agent 通过 getAgentCard() 返回自身的能力声明。
 * A2AOrchestrator 通过 AgentRegistry 根据 capabilityId 匹配 Agent。
 *
 * 新增 Agent 只需注册 AgentCard，LLM 自动发现新能力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCard {

    /** Agent 名称（与 BaseAgent.getName() 一致） */
    private String name;

    /** 能力描述，供 LLM 理解该 Agent 的用途 */
    private String description;

    /** 能力列表 */
    private List<AgentCapability> capabilities;

    /** 输入参数模式（声明需要什么参数） */
    private Map<String, Object> inputSchema;

    /** 输出数据模式（声明产出什么） */
    private Map<String, Object> outputSchema;
}
