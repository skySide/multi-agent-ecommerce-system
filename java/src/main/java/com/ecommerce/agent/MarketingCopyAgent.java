package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.MarketingCopyResult;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.tool.ProductSearchTool;
import com.ecommerce.tool.SensitiveWordTool;
import com.ecommerce.vo.MarketingCopyVO;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 营销文案Agent
 * Prompt模板引擎 + 个性化生成 + 广告法合规校验
 * 
 * 工具调用链：
 *   1. getProductInfo -> 获取商品信息
 *   2. 生成营销文案
 *   3. filterSensitiveWords -> 过滤敏感词
 */
@Component
public class MarketingCopyAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private ProductSearchTool productSearchTool;

    @Resource
    private SensitiveWordTool sensitiveWordTool;

    /** 文案模板映射，按用户分群选择不同风格 */
    private static final Map<String, String> TEMPLATES = Map.of(
            "new_user", "为新用户撰写欢迎文案,风格热情友好,突出新人优惠。",
            "high_value", "为VIP用户撰写推荐文案,风格品质尊享,突出品牌价值。",
            "price_sensitive", "为价格敏感用户撰写文案,突出性价比和促销优惠。",
            "active", "为活跃用户撰写文案,突出商品亮点和使用场景。",
            "churn_risk", "为流失风险用户撰写召回文案,突出专属折扣。"
    );

    /** 默认文案模板 */
    private static final String DEFAULT_TEMPLATE_KEY = "active";

    public MarketingCopyAgent() {
        super("marketing_copy", 10.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        UserProfile profile = (UserProfile) params.get("userProfile");
        List<String> productIds = extractProductIds(params);

        log.info("MarketingCopyAgent.execute - 开始生成营销文案, 商品数: {}, 用户分群: {}",
                productIds.size(), profile != null ? profile.getSegments() : "unknown");

        // 步骤2: 处理空列表
        if (CollectionUtils.isEmpty(productIds)) {
            log.warn("MarketingCopyAgent.execute - 商品列表为空");
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("copies", Collections.emptyList())).confidence(1.0).build();
        }

        // 步骤3: 选择文案模板
        String templateKey = selectTemplate(profile);
        String templatePrompt = TEMPLATES.getOrDefault(templateKey, TEMPLATES.get(DEFAULT_TEMPLATE_KEY));

        // 步骤4: 构建提示词（多工具调用链 + 输出格式示例）
        String systemPrompt = "你是一个资深的电商营销文案撰写专家。请按以下步骤完成任务：\n" +
                "\n步骤1 - 查询商品信息：使用 getProductInfo 工具逐个查询每个商品的信息。" +
                "\n步骤2 - 生成营销文案：根据商品信息和" + templatePrompt + "，为每个商品生成一条营销文案。" +
                "\n  要求：必须为提供的每个商品ID生成一条文案，数量必须一致。每条文案30-50字。" +
                "\n  如果某个商品无法生成个性化文案，请根据商品名称、类目、价格等信息写一个简单描述作为默认文案。" +
                "\n步骤3 - 过滤敏感词：将生成的文案列表传给 filterSensitiveWords 工具进行广告法合规过滤。" +
                "\n步骤4 - 将过滤后的结果按以下JSON格式返回。" +
                "\n\n输出JSON格式示例：" +
                "\n{\"copies\":[{\"productId\":\"P001\",\"copy\":\"这款手机性能强劲，拍照出色，仅售2999，性价比超高！\"},{\"productId\":\"P002\",\"copy\":\"轻薄笔记本，续航12小时，办公学习首选！\"}]}";

        String userMessage = String.format("请为以下 %d 个商品生成营销文案:\n%s",
                productIds.size(),
                productIds.stream().map(id -> "- " + id).collect(Collectors.joining("\n")));

        // 步骤5: 调用LLM执行多工具调用链
        MarketingCopyResult result = chatClient.prompt()
                .system(systemPrompt)
                .tools(productSearchTool, sensitiveWordTool)
                .user(userMessage)
                .call()
                .entity(MarketingCopyResult.class);

        // 步骤6: 返回结果
        List<MarketingCopyVO> copies = Objects.nonNull(result) && !CollectionUtils.isEmpty(result.getCopies())
                ? result.getCopies() : Collections.emptyList();

        Map<String, Object> data = new HashMap<>();
        data.put("copies", copies);
        data.put("template_used", templateKey);
        data.put("product_count", productIds.size());

        log.info("MarketingCopyAgent.execute - 文案生成完成, data = {}", data);

        return AgentResult.builder()
                .agentName(name).success(true).data(data).confidence(0.9)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractProductIds(Map<String, Object> params) {
        List<String> ids = (List<String>) params.get("productIds");
        if (!CollectionUtils.isEmpty(ids)) {
            return ids;
        }

        List<com.ecommerce.model.Product> modelProducts =
                (List<com.ecommerce.model.Product>) params.get("products");
        if (!CollectionUtils.isEmpty(modelProducts)) {
            return modelProducts.stream()
                    .map(com.ecommerce.model.Product::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private String selectTemplate(UserProfile profile) {
        if (Objects.isNull(profile) || Objects.isNull(profile.getSegments())) {
            return DEFAULT_TEMPLATE_KEY;
        }
        List<String> priority = List.of("new_user", "high_value", "churn_risk", "price_sensitive", DEFAULT_TEMPLATE_KEY);
        for (String seg : priority) {
            if (profile.getSegments().contains(seg)) {
                return seg;
            }
        }
        return DEFAULT_TEMPLATE_KEY;
    }
}
