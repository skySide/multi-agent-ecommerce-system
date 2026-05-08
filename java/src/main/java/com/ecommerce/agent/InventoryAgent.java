package com.ecommerce.agent;

import com.ecommerce.common.constants.InventoryConstants;
import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.response.InventoryCheckResult;
import com.ecommerce.service.ProductService;
import com.ecommerce.tool.InventoryTool;
import com.ecommerce.vo.StockAlertVO;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存决策Agent
 * 实时库存查询 + 库存预警 + 限购策略
 * 通过 InventoryTool 查询库存，LLM分析汇总
 */
@Component
public class InventoryAgent extends BaseAgent {

    @Resource
    private ChatClient chatClient;

    @Resource
    private InventoryTool inventoryTool;

    @Resource
    private ProductService productService;

    public InventoryAgent() {
        super("inventory", 5.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取商品ID列表
        List<String> productIds = extractProductIds(params);

        log.info("InventoryAgent.execute - 开始检查库存, 商品数: {}", productIds.size());

        // 步骤2: 处理空列表
        if (CollectionUtils.isEmpty(productIds)) {
            log.warn("InventoryAgent.execute - 商品列表为空");
            return AgentResult.builder().agentName(name).success(true)
                    .data(Map.of("available_products", Collections.emptyList())).confidence(1.0).build();
        }

        // 步骤3: 构建LLM提示词（角色定位 + 输出格式示例）
        String systemPrompt = "你是一个资深的电商库存管理专家。你擅长根据商品的实时库存信息，进行库存状态分析和预警。\n" +
                "请使用 queryProductStock 工具逐个查询每个商品的库存信息，然后根据以下规则进行分析汇总：\n" +
                "1. 库存 > 0 的商品列入 availableProducts\n" +
                "2. 库存 ≤ 50 的商品列入 lowStockAlerts，level=critical，action=urgent_restock\n" +
                "3. 库存 ≤ 100 的商品列入 lowStockAlerts，level=warning，action=plan_restock\n" +
                "4. 库存 ≤ 50 限购1件，库存 ≤ 100 且热销限购2件，热销且库存 ≤ 300 限购3件" +
                "\n\n输出JSON格式示例：" +
                "\n{\"availableProducts\":[\"P001\",\"P002\"],\"lowStockAlerts\":[{\"productId\":\"P003\",\"name\":\"商品名\",\"currentStock\":30,\"level\":\"critical\",\"action\":\"urgent_restock\"}],\"purchaseLimits\":{\"P003\":1}}";

        String userMessage = String.format("请检查以下 %d 个商品的库存状态:\n%s",
                productIds.size(),
                productIds.stream().map(id -> "- " + id).collect(Collectors.joining("\n")));

        // 步骤4: 调用LLM分析库存
        InventoryCheckResult result = chatClient.prompt()
                .system(systemPrompt)
                .tools(inventoryTool)
                .user(userMessage)
                .call()
                .entity(InventoryCheckResult.class);

        // 步骤5: 处理LLM解析失败
        if (Objects.isNull(result)) {
            log.warn("InventoryAgent.execute - LLM解析结果为空，使用兜底逻辑");
            result = fallbackCheck(productIds);
        }

        // 步骤6: 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("available_products", !CollectionUtils.isEmpty(result.getAvailableProducts()) ? result.getAvailableProducts() : Collections.emptyList());
        data.put("low_stock_alerts", !CollectionUtils.isEmpty(result.getLowStockAlerts()) ? result.getLowStockAlerts() : Collections.emptyList());
        data.put("purchase_limits", !CollectionUtils.isEmpty(result.getPurchaseLimits()) ? result.getPurchaseLimits() : Collections.emptyMap());
        data.put("total_checked", productIds.size());
        data.put("available_count", !CollectionUtils.isEmpty(result.getAvailableProducts()) ? result.getAvailableProducts().size() : 0);
        data.put("unavailable_count", productIds.size() - (!CollectionUtils.isEmpty(result.getAvailableProducts()) ? result.getAvailableProducts().size() : 0));

        log.info("InventoryAgent.execute - 库存检查完成, data = {}", data);
        return AgentResult.builder()
                .agentName(name).success(true).data(data).confidence(0.95)
                .build();
    }

    /**
     * 兜底检查逻辑，当LLM解析失败时使用
     */
    private InventoryCheckResult fallbackCheck(List<String> productIds) {
        // 步骤1: 查询商品库存
        InventoryCheckResult result = new InventoryCheckResult();
        List<Product> products = productService.listByProductIds(productIds);
        if (CollectionUtils.isEmpty(products)) {
            log.warn("InventoryAgent.fallbackCheck - 未查到商品信息");
            return result;
        }

        // 步骤2: 循环处理每个商品
        List<String> available = new ArrayList<>();
        List<StockAlertVO> alerts = new ArrayList<>();
        Map<String, Integer> purchaseLimits = new HashMap<>();

        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            int stock = product.getStock() != null ? product.getStock() : 0;

            // 步骤2.1: 跳过无库存商品
            if (stock <= 0) {
                log.info("InventoryAgent.fallbackCheck - 第{}个商品库存为0, productId: {}", i + 1, product.getProductId());
                continue;
            }

            // 步骤2.2: 记录可售商品
            available.add(product.getProductId());

            // 步骤2.3: 检查库存预警
            if (stock <= InventoryConstants.SAFETY_STOCK_THRESHOLD) {
                alerts.add(StockAlertVO.builder()
                        .productId(product.getProductId()).name(product.getProductName())
                        .currentStock(stock).level("critical").action("urgent_restock").build());
                log.warn("InventoryAgent.fallbackCheck - 第{}个商品库存临界, productId: {}, stock: {}", i + 1, product.getProductId(), stock);
            } else if (stock <= InventoryConstants.LOW_STOCK_THRESHOLD) {
                alerts.add(StockAlertVO.builder()
                        .productId(product.getProductId()).name(product.getProductName())
                        .currentStock(stock).level("warning").action("plan_restock").build());
            }

            // 步骤2.4: 计算限购数量
            Integer limit = calcPurchaseLimit(product, stock);
            if (Objects.nonNull(limit)) {
                purchaseLimits.put(product.getProductId(), limit);
            }
        }
        result.setAvailableProducts(available);
        result.setLowStockAlerts(alerts);
        result.setPurchaseLimits(purchaseLimits);

        log.info("InventoryAgent.fallbackCheck - 兜底检查完成, result = {}", result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractProductIds(Map<String, Object> params) {
        // 方式1: 直接传入 productId 列表
        List<String> ids = (List<String>) params.get("productIds");
        if (!CollectionUtils.isEmpty(ids)) {
            return ids;
        }

        // 方式2: 传入 model.Product 列表
        List<com.ecommerce.model.Product> modelProducts =
                (List<com.ecommerce.model.Product>) params.get("products");
        if (!CollectionUtils.isEmpty(modelProducts)) {
            return modelProducts.stream()
                    .map(com.ecommerce.model.Product::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        // 方式3: 传入 entity.Product 列表
        List<Product> entityProducts = (List<Product>) params.get("entityProducts");
        if (!CollectionUtils.isEmpty(entityProducts)) {
            return entityProducts.stream()
                    .map(Product::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private Integer calcPurchaseLimit(Product product, int stock) {
        boolean isHot = inventoryTool.isHotProduct(product);
        if (stock <= InventoryConstants.SAFETY_STOCK_THRESHOLD) {
            return 1;
        }
        if (stock <= InventoryConstants.LOW_STOCK_THRESHOLD && isHot) {
            return InventoryConstants.HOT_ITEM_PURCHASE_LIMIT;
        }
        if (isHot && stock <= 300) {
            return 3;
        }
        return null;
    }
}
