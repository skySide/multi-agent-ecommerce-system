package com.ecommerce.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 单个意图项
 * 包含意图类型、关联实体、以及 LLM 根据语义分析出的依赖关系
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentItem {

    /** 意图类型：recommend, product_query, knowledge_query, compare, chitchat */
    private String intent;

    /** 从用户消息中提取的实体信息 */
    private Map<String, Object> entities;

    /** 依赖的其他意图列表（LLM 根据语义分析得出，为空表示无依赖） */
    private List<String> dependsOn;
}
