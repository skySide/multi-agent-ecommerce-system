package com.ecommerce.service.impl;

import com.ecommerce.dto.CategoryHit;
import com.ecommerce.dto.ClassifyResultDTO;
import com.ecommerce.service.KnowledgeClassifyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 知识分类服务实现
 * 使用 LLM 对用户查询进行知识分类，输出 knowledge_type + sub_type
 */
@Slf4j
@Service
public class KnowledgeClassifyServiceImpl implements KnowledgeClassifyService {

    @Resource
    private ChatClient chatClient;

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
            ```json
            {
              "categories": [
                {"knowledge_type": "after_sales", "sub_type": "return_policy", "relevance": 0.9}
              ],
              "is_cross_category": false,
              "confidence": 0.9
            }
            ```

            请只输出 JSON，不要其他解释。
            """;

    @Override
    public ClassifyResultDTO classify(String query, List<String> history) {
        log.info("KnowledgeClassifyServiceImpl.classify 开始分类 query={}", query);

        try {
            // 步骤1: 构建上下文和提示词
            String historyContext = buildHistoryContext(history);
            String prompt = String.format(CLASSIFY_PROMPT, historyContext, query);

            // 步骤2: 调用 LLM 获取 JSON 响应，使用 BeanOutputConverter 自动反序列化为 DTO
            var converter = new BeanOutputConverter<>(ClassifyResultDTO.class);
            String response = chatClient.prompt().user(prompt).call().content();
            ClassifyResultDTO result = converter.convert(response);

            // 步骤3: 记录分类结果
            log.info("KnowledgeClassifyServiceImpl.classify 分类结果: categories={}, isCross={}",
                    result.getCategories() != null
                            ? result.getCategories().stream().map(CategoryHit::getKnowledgeType).toList()
                            : Collections.emptyList(),
                    result.isCrossCategory());
            return result;

        } catch (Exception e) {
            log.error("KnowledgeClassifyServiceImpl.classify 分类失败: {}", e.getMessage());
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
