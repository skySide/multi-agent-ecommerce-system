package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.ProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存决策Agent — 实时库存查询 + 库存预警 + 限购策略
 * 接入真实数据库库存数据
 */
@Component
public class InventoryAgent extends BaseAgent {

    @Resource
    private ProductService productService;

    private static final int SAFETY_STOCK_THRESHOLD = 50;
    private static final int LOW_STOCK_THRESHOLD = 100;
    private static final int HOT_ITEM_PURCHASE_LIMIT = 2;

    public InventoryAgent() {
        super("inventory", 5.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 支持传入 model.Product 列表 或 productId 列表
        List<String> productIds = extractProductIds(params);

        log.info("InventoryAgent.execute 开始检查库存，共 {} 个商品", productIds.size());

        // 从数据库查询真实库存
        List<Product> products = productService.listByProductIds(productIds);

        List<String> available = new ArrayList<>();
        List<Map<String, Object>> alerts = new ArrayList<>();
        Map<String, Integer> purchaseLimits = new HashMap<>();

        for (Product product : products) {
            int stock = product.getStock() != null ? product.getStock() : 0;
            if (stock <= 0) {
                log.info("InventoryAgent.execute 商品 {} 库存为0，不可售", product.getProductId());
                continue;
            }

            available.add(product.getProductId());

            if (stock <= SAFETY_STOCK_THRESHOLD) {
                alerts.add(Map.of(
                        "product_id", product.getProductId(),
                        "name", product.getProductName(),
                        "current_stock", stock,
                        "level", "critical",
                        "action", "urgent_restock"
                ));
                log.warn("InventoryAgent.execute 商品 {} 库存临界: {}", product.getProductId(), stock);
            } else if (stock <= LOW_STOCK_THRESHOLD) {
                alerts.add(Map.of(
                        "product_id", product.getProductId(),
                        "name", product.getProductName(),
                        "current_stock", stock,
                        "level", "warning",
                        "action", "plan_restock"
                ));
            }

            Integer limit = calcPurchaseLimit(product, stock);
            if (limit != null) {
                purchaseLimits.put(product.getProductId(), limit);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("available_products", available);
        data.put("low_stock_alerts", alerts);
        data.put("purchase_limits", purchaseLimits);
        data.put("total_checked", productIds.size());
        data.put("available_count", available.size());
        data.put("unavailable_count", productIds.size() - available.size());

        log.info("InventoryAgent.execute 库存检查完成: 可售{} 缺货{} 预警{}",
                available.size(), productIds.size() - available.size(), alerts.size());

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.95)
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

        // 方式3: 传入 entity.Product 列表
        List<Product> entityProducts = (List<Product>) params.get("entityProducts");
        if (entityProducts != null && !entityProducts.isEmpty()) {
            return entityProducts.stream()
                    .map(Product::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private Integer calcPurchaseLimit(Product product, int stock) {
        boolean isHot = isHotProduct(product);
        if (stock <= SAFETY_STOCK_THRESHOLD) return 1;
        if (stock <= LOW_STOCK_THRESHOLD && isHot) return HOT_ITEM_PURCHASE_LIMIT;
        if (isHot && stock <= 300) return 3;
        return null;
    }

    private boolean isHotProduct(Product product) {
        // 根据销量或标签判断是否为热销品
        if (product.getSalesCount() != null && product.getSalesCount() > 1000) {
            return true;
        }
        String desc = product.getProductDescription();
        if (desc != null && (desc.contains("新品") || desc.contains("旗舰") || desc.contains("热销"))) {
            return true;
        }
        return false;
    }
}
