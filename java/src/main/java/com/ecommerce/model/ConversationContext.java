package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 对话上下文
 * AgentOrchestrator 在调用 LLM 生成执行计划前，从 ConversationSession 构建的结构化上下文。
 * 包含历史累积数据、历史轮次、对话摘要和最近对话文本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    /** 从 extracted_info 解析的跨轮累积实体数据（如 recommended_product_ids、category 等） */
    private Map<String, Object> availableData;

    /** 从 round_intents 解析的历史轮次快照列表 */
    private List<Map<String, Object>> previousRounds;

    /** 对话摘要（来自 summary 字段） */
    private String dialogueSummary;

    /** 最近几轮对话文本（来自 dialogue_history） */
    private List<String> recentDialogue;
}
