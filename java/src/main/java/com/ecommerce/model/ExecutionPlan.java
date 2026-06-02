package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 执行计划
 * AgentOrchestrator 根据多意图依赖关系生成的 DAG 执行计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {

    /** 串行执行组列表（按拓扑序排列，组内并行、组间串行） */
    private List<ExecutionGroup> groups;
}
