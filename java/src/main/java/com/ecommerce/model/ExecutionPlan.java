package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 执行计划（A2A Protocol）
 *
 * 由 A2AOrchestrator 调用 LLM 生成。LLM 分析历史数据和用户意图后，
 * 输出 A2A Task 列表（指定 skillId + input），AgentRegistry 根据技能动态匹配 Agent。
 *
 * contextAnalysis 是 LLM 对当前上下文与用户需求匹配情况的推理说明，
 * **不参与业务逻辑**，仅用于日志可观测性——出问题时可直接看到 LLM 当时的判断依据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {

    /**
     * LLM 的上下文分析（仅日志，不参与业务逻辑）
     * 例："历史推荐了6台手机（含小米14和vivo X100），品牌匹配，可从历史复用"
     */
    private String contextAnalysis;

    /** LLM 生成的 A2A 任务列表（按 dependsOn 拓扑序排列） */
    private List<A2ATask> tasks;

    /**
     * 格式化输出完整执行计划，用于日志打印
     */
    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== A2A 执行计划 ==========\n");
        sb.append("上下文分析: ").append(contextAnalysis != null ? contextAnalysis : "无").append("\n\n");

        if (CollectionUtils.isEmpty(tasks)) {
            sb.append("（无任务）\n");
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                A2ATask task = tasks.get(i);
                sb.append(String.format("Task %s [capabilityId: %s]", task.getId(), task.getCapabilityId()));
                if (task.getAssignedAgent() != null) {
                    sb.append(String.format(" → agent: %s", task.getAssignedAgent()));
                }
                sb.append("\n");
                if (task.getInput() != null && !task.getInput().isEmpty()) {
                    sb.append("  输入: ").append(task.getInput()).append("\n");
                }
                sb.append("  依赖: ").append(CollectionUtils.isEmpty(task.getDependsOn())
                        ? "无" : String.join(", ", task.getDependsOn())).append("\n");
                sb.append("  推理: ").append(task.getReasoning()).append("\n");
                if (i < tasks.size() - 1) {
                    sb.append("\n");
                }
            }
        }
        sb.append("==================================");
        return sb.toString();
    }
}
