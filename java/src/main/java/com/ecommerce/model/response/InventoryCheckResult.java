package com.ecommerce.model.response;

import com.ecommerce.vo.StockAlertVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 库存检查结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCheckResult {

    /** 可售商品ID列表 */
    private List<String> availableProducts;

    /** 低库存预警列表 */
    private List<StockAlertVO> lowStockAlerts;

    /** 限购策略映射：商品ID → 限购数量 */
    private Map<String, Integer> purchaseLimits;
}
