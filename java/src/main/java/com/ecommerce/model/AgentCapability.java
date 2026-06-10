package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 能力声明（原 AgentSkill）
 *
 * 每个 Agent 通过 AgentCard 声明自己拥有的能力（capability），
 * LLM 在生成执行计划时根据 capabilityId 匹配合适的 Agent。
 * AgentRegistry 负责将 capabilityId 映射到具体的 Agent 实例。
 *
 * 与旧术语 "skill" 的区别：capability 更准确表达"Agent 具备什么能力"，
 * 而非"Agent 拥有什么技能"。一个 Agent 可以有多个 capability。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCapability {

    /** 能力唯一标识，如 "search_by_keyword"、"compare_products" */
    private String id;

    /** 能力名称，如 "关键词搜索" */
    private String name;

    /** 能力描述，供 LLM 理解该能力的用途 */
    private String description;

    /** 标签列表，如 ["product", "search"]，用于模糊匹配 */
    private List<String> tags;
}
