package com.ecommerce.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 多意图识别结果
 * LLM 从单条用户消息中识别多个意图及其依赖关系
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiIntentRecognitionResult {

    /** 意图列表，每个意图包含类型、实体、依赖关系 */
    private List<IntentItem> intents;
}
