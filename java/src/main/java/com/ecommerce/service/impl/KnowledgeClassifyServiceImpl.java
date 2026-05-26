package com.ecommerce.service.impl;

import com.ecommerce.dto.ClassifyResultDTO;
import com.ecommerce.service.KnowledgeClassifyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 知识分类服务实现
 * 使用 LLM 对用户查询进行知识分类，输出 knowledge_type + sub_type
 */
@Slf4j
@Service
public class KnowledgeClassifyServiceImpl implements KnowledgeClassifyService {

    @Resource
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CLASSIFY_PROMPT = """
            你是一个电商知识分类专家。请分析用户问题涉及哪些知识类别。

            ## 可用知识类别
            - after_sales: 售后服务（退货/换货/退款流程/质量问题）
              - return_policy: 退货政策
              - exchange_policy: 换货政策
              - refund_process: 退款流程
              - quality_issue: 质量问题
            - logistics: 物流配送（配送/运费）
              - delivery_info: 配送说明
              - shipping_fee: 运费标准
            - member: 会员权益（会员等级/积分）
              - member_benefits: 会员权益
              - points_rule: 积分规则
            - coupon: 优惠券（使用/退回/过期）
              - coupon_use: 优惠券使用规则
              - coupon_refund: 退货后优惠券退回
              - coupon_expire: 优惠券过期处理
            - payment: 支付与退款
              - pay_method: 支付方式
              - refund_flow: 退款到账说明
            - product_guide: 商品选购指南
              - phone_buying_guide: 手机选购指南

            ## 常见跨类别场景参考
            - 退货后优惠券是否退回 → after_sales + coupon
            - 退货后运费谁承担 → after_sales + logistics
            - 会员退货是否影响等级 → after_sales + member
            - 退款后优惠券如何处理 → payment + coupon
            - 退货退款什么时间到账 → after_sales + payment

            ## 对话上下文（如有）
            %s

            ## 用户问题
            %s

            ## 输出格式（JSON）
            {
              "categories": [
                {"knowledge_type": "after_sales", "sub_type": "return_policy", "relevance": 0.9}
              ],
              "is_cross_category": false,
              "confidence": 0.9
            }

            请只输出 JSON，不要其他解释。
            """;

    @Override
    public ClassifyResultDTO classify(String query, List<String> history) {
        log.info("KnowledgeClassifyServiceImpl.classify 开始分类 query={}", query);

        try {
            String historyContext = buildHistoryContext(history);
            String prompt = String.format(CLASSIFY_PROMPT, historyContext, query);
            String response = chatClient.prompt().user(prompt).call().content().trim();

            // 清理 markdown 代码块
            String json = response;
            if (json.startsWith("```")) {
                json = json.substring(json.indexOf('\n') + 1);
                json = json.substring(0, json.lastIndexOf("```")).trim();
            }

            Map<String, Object> resultMap = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> categoriesRaw = (List<Map<String, Object>>) resultMap.get("categories");
            boolean isCrossCategory = (boolean) resultMap.getOrDefault("is_cross_category", false);
            double confidence = resultMap.get("confidence") instanceof Number
                    ? ((Number) resultMap.get("confidence")).doubleValue() : 0.8;

            List<ClassifyResultDTO.CategoryHit> categories = categoriesRaw.stream()
                    .map(c -> ClassifyResultDTO.CategoryHit.builder()
                            .knowledgeType((String) c.get("knowledge_type"))
                            .subType((String) c.get("sub_type"))
                            .relevance(c.get("relevance") instanceof Number
                                    ? ((Number) c.get("relevance")).doubleValue() : 0.5)
                            .build())
                    .toList();

            ClassifyResultDTO result = ClassifyResultDTO.builder()
                    .categories(categories)
                    .isCrossCategory(isCrossCategory)
                    .confidence(confidence)
                    .build();

            log.info("KnowledgeClassifyServiceImpl.classify 分类结果: categories={}, isCross={}",
                    categories.stream().map(ClassifyResultDTO.CategoryHit::getKnowledgeType).toList(), isCrossCategory);
            return result;

        } catch (Exception e) {
            log.warn("KnowledgeClassifyServiceImpl.classify 分类失败: {}", e.getMessage());
            // 降级：返回空分类结果，由调用方退化为不过滤检索
            return ClassifyResultDTO.builder()
                    .categories(Collections.emptyList())
                    .isCrossCategory(false)
                    .confidence(0.0)
                    .build();
        }
    }

    private String buildHistoryContext(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "（无上下文）";
        }
        List<String> recent = history.subList(Math.max(0, history.size() - 4), history.size());
        return String.join("\n", recent);
    }
}
