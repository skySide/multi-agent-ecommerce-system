package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A2A 任务
 *
 * A2AOrchestrator 调用 LLM 生成的任务列表中的单个任务。
 * LLM 只指定 capabilityId 和 input，不感知具体 Agent。
 * AgentRegistry 根据 capabilityId 动态匹配 Agent。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2ATask {

    /** 任务唯一标识，如 "task_1" */
    private String id;

    /** 任务状态：pending / running / completed / failed */
    private String status;

    /** LLM 指定的能力 ID，如 "search_by_keyword" */
    private String capabilityId;

    /** LLM 从用户消息中提取的输入参数 */
    private Map<String, Object> input;

    /** 任务执行后的输出数据 */
    private Map<String, Object> output;

    /** 由 AgentRegistry 匹配后分配的 Agent 名称 */
    private String assignedAgent;

    /** 依赖的任务 ID 列表 */
    private List<String> dependsOn;

    /** LLM 的推理说明（用于日志追溯） */
    private String reasoning;
}
