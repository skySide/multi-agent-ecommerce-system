package com.ecommerce.common.constants;

/**
 * 库存相关常量
 * 定义库存预警阈值、热销判断标准等常量
 */
public class InventoryConstants {

    /** 安全库存阈值，低于此值触发紧急补货 */
    public static final int SAFETY_STOCK_THRESHOLD = 50;

    /** 低库存预警阈值，低于此值触发计划补货 */
    public static final int LOW_STOCK_THRESHOLD = 100;

    /** 热销商品限购数量 */
    public static final int HOT_ITEM_PURCHASE_LIMIT = 2;

    /** 热销判定阈值，销量超过此值视为热销 */
    public static final int HOT_SALES_THRESHOLD = 1000;

    /** 热销关键词：新品 */
    public static final String HOT_KEYWORD_NEW = "新品";

    /** 热销关键词：旗舰 */
    public static final String HOT_KEYWORD_FLAGSHIP = "旗舰";

    /** 热销关键词：热销 */
    public static final String HOT_KEYWORD_HOT = "热销";

    private InventoryConstants() {
    }
}
