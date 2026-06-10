package com.ecommerce.model.response;

import com.ecommerce.model.DataRequirement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 单个意图项
 * 包含意图类型、关联实体、以及意图执行所需的数据需求
 *
 * 核心改变：意图不依赖其他意图（dependsOn），而是声明需要什么数据（requiredData）。
 * 数据来源由 AgentOrchestrator 的 LLM 执行计划决定（从历史筛选 or 重新获取）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentItem {

    /** 意图类型：recommend, product_query, knowledge_query, compare, chitchat, transfer_to_human */
    private String intent;

    /** 从用户消息中提取的实体信息 */
    private Map<String, Object> entities;

    /** 执行此意图需要的数据列表（LLM 根据用户消息分析得出，含类型和筛选条件） */
    private List<DataRequirement> requiredData;

    /** 意图的输出数据标识 key，供上下游数据传递 */
    private String outputKey;
}
