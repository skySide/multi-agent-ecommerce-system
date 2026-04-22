package com.ecommerce.service;

import com.ecommerce.entity.Product;
import com.ecommerce.model.UserProfile;

import java.util.List;
import java.util.Map;

/**
 * 推荐引擎核心服务
 * 多路召回 + 精排 + 多样性控制
 */
public interface RecommendEngineService {

    /**
     * 多路召回候选商品
     *
     * @param userId   用户ID
     * @param profile  用户画像
     * @param numItems 需要的数量
     * @param context  上下文信息
     * @return 召回结果，key为召回通道名称
     */
    Map<String, List<Product>> multiChannelRecall(String userId, UserProfile profile, int numItems, Map<String, Object> context);

    /**
     * 向量搜索召回
     */
    List<Product> vectorRecall(String userId, UserProfile profile, int numItems);

    /**
     * 热门商品召回
     */
    List<Product> hotRecall(int numItems);

    /**
     * 新品召回
     */
    List<Product> newArrivalRecall(int numItems);

    /**
     * 类目偏好召回
     */
    List<Product> categoryRecall(UserProfile profile, int numItems);

    /**
     * 精排（规则 + LLM）
     *
     * @param candidates 候选商品
     * @param profile    用户画像
     * @param numItems   最终返回数量
     * @return 精排后的商品列表
     */
    List<Product> rerank(List<Product> candidates, UserProfile profile, int numItems);

    /**
     * 多样性控制（MMR算法）
     *
     * @param candidates 候选商品
     * @param numItems   最终返回数量
     * @param lambda     相关性vs多样性权衡参数
     * @return 多样性调整后的商品列表
     */
    List<Product> applyDiversity(List<Product> candidates, int numItems, double lambda);

    /**
     * 获取推荐商品（完整流程：召回+精排+多样性）
     */
    List<Product> recommend(String userId, UserProfile profile, int numItems, Map<String, Object> context);
}
