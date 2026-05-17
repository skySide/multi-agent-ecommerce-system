package com.ecommerce.common.constants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent相关常量
 * 定义意图与Agent的映射关系，供各处统一使用
 */
public class AgentConstants {

    /** 意图 → Agent名称映射 */
    public static final Map<String, String> INTENT_AGENT_MAPPING = Map.of(
            "recommend", "recommend_intent",
            "product_query", "product_query_intent",
            "knowledge_query", "knowledge_intent",
            "compare", "compare_intent",
            "chitchat", "chitchat_intent",
            "transfer_to_human", "chitchat_intent"
    );

    /** 所有意图集合（用于分析维度） */
    public static final Set<String> ALL_INTENTS = INTENT_AGENT_MAPPING.keySet().stream()
            .filter(intent -> !"transfer_to_human".equals(intent))
            .collect(Collectors.toSet());

    /** 分析用的Agent名称列表（去重后，transfer_to_human归入chitchat） */
    public static final List<String> ANALYSIS_AGENT_NAMES = INTENT_AGENT_MAPPING.values().stream()
            .map(item -> {
                int index = item.indexOf("_intent");
                if (index != -1) {
                    return item.substring(0, index);
                }
                return item;
            })
            .collect(Collectors.toList());
}
