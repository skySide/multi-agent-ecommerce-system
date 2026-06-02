package com.ecommerce.model;

import com.ecommerce.model.response.IntentItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 执行组
 * 同一组内的 Agent 可并行执行，组之间按拓扑序串行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionGroup {

    /** 组内待执行的意图列表 */
    private List<IntentItem> intentItems;

    /** 是否并行执行（true=并行，false=串行 [当前仅用于单元素组]） */
    private boolean parallel;

    /** 上游组传递下来的结果数据（key=上游intent名称, value=上游AgentResult.data） */
    private Map<String, Map<String, Object>> upstreamData;
}
