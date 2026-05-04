package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.service.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 营销文案Agent — Prompt模板引擎 + 个性化生成 + 广告法合规校验
 * 接入真实商品数据
 */
@Component
public class MarketingCopyAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;
    @Resource
    private ProductService productService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> TEMPLATES = Map.of(
            "new_user", "为新用户撰写欢迎文案,风格热情友好,突出新人优惠。",
            "high_value", "为VIP用户撰写推荐文案,风格品质尊享,突出品牌价值。",
            "price_sensitive", "为价格敏感用户撰写文案,突出性价比和促销优惠。",
            "active", "为活跃用户撰写文案,突出商品亮点和使用场景。",
            "churn_risk", "为流失风险用户撰写召回文案,突出专属折扣。"
    );

    private static final List<String> FORBIDDEN_WORDS = List.of(
            "最好", "第一", "国家级", "全球首", "绝对", "100%", "永久", "万能"
    );

    public MarketingCopyAgent() {
        super("marketing_copy", 10.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        UserProfile profile = (UserProfile) params.get("userProfile");
        List<String> productIds = extractProductIds(params);

        log.info("MarketingCopyAgent.execute 开始生成营销文案，商品数={} 用户分群={}",
                productIds.size(), profile != null ? profile.getSegments() : "unknown");

        if (productIds.isEmpty()) {
            log.info("MarketingCopyAgent.execute 商品列表为空，跳过文案生成");
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("copies", List.of())).confidence(1.0).build();
        }

        // 从数据库查询真实商品信息
        List<Product> products = productService.listByProductIds(productIds);
        if (products.isEmpty()) {
            log.warn("MarketingCopyAgent.execute 未找到商品信息");
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("copies", List.of())).confidence(1.0).build();
        }

        String templateKey = selectTemplate(profile);
        String systemPrompt = TEMPLATES.getOrDefault(templateKey, TEMPLATES.get("active"))
                + "\n每个商品生成一条文案(30-50字)。输出JSON数组: [{\"product_id\":\"xxx\",\"copy\":\"文案\"}]";

        String productInfo = products.stream()
                .map(p -> String.format("ID:%s %s %s ¥%.0f 品牌:%s",
                        p.getProductId(), p.getProductName(), p.getCategoryName(),
                        p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                        p.getBrand()))
                .collect(Collectors.joining("\n"));

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("商品列表:\n" + productInfo)
                .call()
                .content();

        List<Map<String, String>> copies = parseCopies(response);
        copies = copies.stream().map(this::complianceCheck).collect(Collectors.toList());

        // 确保每个商品都有文案（兜底）
        copies = ensureAllProductsHaveCopy(products, copies);

        Map<String, Object> data = new HashMap<>();
        data.put("copies", copies);
        data.put("template_used", templateKey);
        data.put("product_count", products.size());

        log.info("MarketingCopyAgent.execute 文案生成完成，共 {} 条", copies.size());

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.9)
                .build();
    }

    /**
     * 从参数中提取商品ID列表
     */
    @SuppressWarnings("unchecked")
    private List<String> extractProductIds(Map<String, Object> params) {
        // 方式1: 直接传入 productId 列表
        List<String> ids = (List<String>) params.get("productIds");
        if (ids != null && !ids.isEmpty()) {
            return ids;
        }

        // 方式2: 传入 model.Product 列表
        List<com.ecommerce.model.Product> modelProducts =
                (List<com.ecommerce.model.Product>) params.get("products");
        if (modelProducts != null && !modelProducts.isEmpty()) {
            return modelProducts.stream()
                    .map(com.ecommerce.model.Product::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    /**
     * 确保每个商品都有文案，没有则生成默认文案
     */
    private List<Map<String, String>> ensureAllProductsHaveCopy(List<Product> products, List<Map<String, String>> copies) {
        Map<String, String> copyMap = copies.stream()
                .collect(Collectors.toMap(
                        c -> c.getOrDefault("product_id", ""),
                        c -> c.getOrDefault("copy", ""),
                        (a, b) -> a
                ));

        List<Map<String, String>> result = new ArrayList<>();
        for (Product product : products) {
            String pid = product.getProductId();
            String copy = copyMap.get(pid);
            if (copy == null || copy.isEmpty()) {
                copy = String.format("%s %s，%s出品，仅售¥%.0f，品质保证！",
                        product.getCategoryName(), product.getProductName(),
                        product.getBrand(),
                        product.getPrice() != null ? product.getPrice().doubleValue() : 0);
            }
            Map<String, String> item = new HashMap<>();
            item.put("product_id", pid);
            item.put("copy", copy);
            result.add(item);
        }
        return result;
    }

    private String selectTemplate(UserProfile profile) {
        if (profile == null || profile.getSegments() == null) return "active";
        List<String> priority = List.of("new_user", "high_value", "churn_risk", "price_sensitive", "active");
        for (String seg : priority) {
            if (profile.getSegments().contains(seg)) return seg;
        }
        return "active";
    }

    private List<Map<String, String>> parseCopies(String raw) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("MarketingCopyAgent.parseCopies 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> complianceCheck(Map<String, String> copyItem) {
        String text = copyItem.getOrDefault("copy", "");
        for (String word : FORBIDDEN_WORDS) {
            text = text.replace(word, "***");
        }
        Map<String, String> result = new HashMap<>(copyItem);
        result.put("copy", text);
        return result;
    }
}
