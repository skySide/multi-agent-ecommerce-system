package com.ecommerce.tool;

import com.ecommerce.common.constants.InventoryConstants;
import com.ecommerce.entity.Product;
import com.ecommerce.service.ProductService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 库存查询工具
 * 用于 AI Agent 查询商品库存信息
 */
@Component
public class InventoryTool {

    private static final Logger log = LoggerFactory.getLogger(InventoryTool.class);

    @Resource
    private ProductService productService;

    /**
     * 查询单个商品的实时库存信息
     *
     * @param productId 商品ID
     * @return 商品库存信息字符串
     */
    @Tool(name = "queryProductStock", description = "查询单个商品的实时库存信息，返回商品ID、名称、库存数量、销量和价格")
    public String queryProductStock(
            @ToolParam(description = "商品ID，需要查询库存的商品标识") String productId) {
        // 步骤1: 参数校验
        if (Objects.isNull(productId)) {
            log.error("InventoryTool.queryProductStock - 参数为空, productId: null");
            return "商品ID不能为空";
        }

        log.info("InventoryTool.queryProductStock - 查询商品库存, productId: {}", productId);

        // 步骤2: 查询商品信息
        Product product = productService.getByProductId(productId);
        if (Objects.isNull(product)) {
            log.warn("InventoryTool.queryProductStock - 商品不存在, productId: {}", productId);
            return String.format("商品[%s] 不存在", productId);
        }

        // 步骤3: 判断是否热销
        boolean isHot = isHotProduct(product);

        // 步骤4: 返回结果
        log.info("InventoryTool.queryProductStock - 查询完成, productId: {}, stock: {}, isHot: {}",
                productId, product.getStock(), isHot);
        return String.format("商品[%s] %s, 库存:%d件, 销量:%d, 价格:%.2f, 是否热销:%s",
                product.getProductId(), product.getProductName(),
                product.getStock() != null ? product.getStock() : 0,
                product.getSalesCount() != null ? product.getSalesCount() : 0,
                product.getPrice() != null ? product.getPrice().doubleValue() : 0.0,
                isHot ? "是" : "否");
    }

    /**
     * 判断商品是否为热销品
     */
    public boolean isHotProduct(Product product) {
        if (product.getSalesCount() != null && product.getSalesCount() > InventoryConstants.HOT_SALES_THRESHOLD) {
            return true;
        }
        String desc = product.getProductDescription();
        if (desc != null && (desc.contains(InventoryConstants.HOT_KEYWORD_NEW)
                || desc.contains(InventoryConstants.HOT_KEYWORD_FLAGSHIP)
                || desc.contains(InventoryConstants.HOT_KEYWORD_HOT))) {
            return true;
        }
        return false;
    }
}
