package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 执行步骤
 * LLM 生成的执行计划中的一个步骤，包含数据需求、执行动作、依赖关系、推理说明
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStep {

    /** 步骤唯一标识，如 "step_1" */
    private String id;

    /** 执行动作类型：filter_from_history | execute_intent | fetch_data */
    private String action;

    /** 意图类型（仅 execute_intent 时有值）：compare | knowledge_query | recommend | product_query | chitchat */
    private String intent;

    /** 自然语言描述，说明本步骤做什么 */
    private String description;

    /** 数据来源：from_history | from_search | from_rag | from_step */
    private String dataSource;

    /** 本步骤需要的数据（含筛选条件） */
    private List<DataRequirement> requiredData;

    /** 依赖的步骤 ID 列表 */
    private List<String> dependsOn;

    /** 产出数据的标识 key，供下游步骤引用 */
    private String outputKey;

    /** LLM 的推理说明（必填，用于日志追溯） */
    private String reasoning;
}
