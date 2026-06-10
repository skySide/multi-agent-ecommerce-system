package com.ecommerce.model;

/**
 * 数据类型枚举
 * 定义意图所需数据的类型，供 LLM 在生成执行计划时选择正确的数据来源
 */
public enum DataType {

    /** 商品列表 — 推荐/对比/查询场景，来源：历史推荐结果筛选 或 product_query 搜索 */
    PRODUCT_LIST,

    /** 政策信息 — 退换货/物流/保修/优惠活动，来源：知识库 RAG 检索 */
    POLICY_INFO,

    /** 订单信息 — 订单状态/物流追踪，来源：订单查询系统 */
    ORDER_INFO,

    /** 用户画像 — 偏好/历史行为，来源：用户画像服务 */
    USER_PROFILE,

    /** 通用知识 — 品牌介绍/使用教程等，来源：知识库 RAG 检索 */
    GENERAL_KNOWLEDGE
}
