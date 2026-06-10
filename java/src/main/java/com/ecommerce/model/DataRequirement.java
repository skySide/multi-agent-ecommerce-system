package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据需求
 * 声明一个意图执行需要什么类型的数据，以及从用户消息中提取的筛选条件
 *
 * 注意：不包含 minCount 等数量约束——LLM 无法预先知道有多少条数据匹配筛选条件，
 * 应该从历史数据中筛选，让筛选结果自然决定数量。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRequirement {

    /** 数据类型（预定义枚举） */
    private DataType type;

    /** 从用户消息中提取的筛选条件描述，供 LLM 在历史数据中匹配 */
    private String filterDescription;
}
