package com.ecommerce.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 意图识别结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentRecognitionResult {

    /** 意图类型：recommend, product_query, knowledge_query, compare, chitchat */
    private String intent;

    /** 从用户消息中提取的实体信息 */
    private Map<String, Object> entities;
}
