package com.ecommerce.service;

import com.ecommerce.dto.RewriteResultDTO;

import java.util.List;
import java.util.Map;

/**
 * Query 改写服务接口
 * <p>基于 LLM 实现用户意图理解和查询扩展，支持商品推荐和智能客服场景。</p>
 */
public interface QueryRewriteService {

    /**
     * 对用户输入进行改写和扩展
     *
     * @param userQuery     用户原始输入
     * @param context       上下文信息（用户画像、对话历史等）
     * @return              改写结果
     */
    RewriteResultDTO rewrite(String userQuery, Map<String, Object> context);

    /**
     * 将用户意图转换为向量检索 query
     *
     * @param userQuery     用户原始输入
     * @param profile       用户画像
     * @return              向量检索用的 query 文本
     */
    String toVectorQuery(String userQuery, Map<String, Object> profile);

    /**
     * 批量改写（用于多路召回时生成多个查询变体）
     *
     * @param userQuery     用户原始输入
     * @param numVariants   变体数量
     * @return              改写后的查询列表
     */
    List<String> expandVariants(String userQuery, int numVariants);

    /**
     * 结合对话历史和摘要进行 Query 改写
     * 用于智能客服场景，降低幻觉率
     *
     * @param query   用户原始查询
     * @param history 对话历史列表
     * @param summary 对话摘要
     * @return 改写后的查询，失败时返回原始查询
     */
    String rewriteWithContext(String query, List<String> history, String summary);
}
