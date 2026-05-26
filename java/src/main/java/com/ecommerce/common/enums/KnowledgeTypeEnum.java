package com.ecommerce.common.enums;

import lombok.Getter;

/**
 * 知识分类枚举
 * 定义知识大类(knowledge_type)和子类(sub_type)
 */
@Getter
public enum KnowledgeTypeEnum {

    // ==================== 知识大类 ====================
    AFTER_SALES("after_sales", "售后服务"),
    LOGISTICS("logistics", "物流配送"),
    MEMBER("member", "会员权益"),
    COUPON("coupon", "优惠券/消费券"),
    PAYMENT("payment", "支付与退款"),
    PRODUCT_GUIDE("product_guide", "商品选购指南"),
    ACCOUNT("account", "账户相关"),

    // ==================== 知识子类 ====================
    // -- after_sales --
    RETURN_POLICY("return_policy", "退货政策", AFTER_SALES),
    EXCHANGE_POLICY("exchange_policy", "换货政策", AFTER_SALES),
    REFUND_PROCESS("refund_process", "退款流程", AFTER_SALES),
    QUALITY_ISSUE("quality_issue", "质量问题", AFTER_SALES),

    // -- logistics --
    DELIVERY_INFO("delivery_info", "配送说明", LOGISTICS),
    SHIPPING_FEE("shipping_fee", "运费标准", LOGISTICS),

    // -- member --
    MEMBER_BENEFITS("member_benefits", "会员权益", MEMBER),
    POINTS_RULE("points_rule", "积分规则", MEMBER),

    // -- coupon --
    COUPON_USE("coupon_use", "优惠券使用规则", COUPON),
    COUPON_REFUND("coupon_refund", "退货后优惠券退回", COUPON),
    COUPON_EXPIRE("coupon_expire", "优惠券过期处理", COUPON),

    // -- payment --
    PAY_METHOD("pay_method", "支付方式", PAYMENT),
    REFUND_FLOW("refund_flow", "退款到账说明", PAYMENT),

    // -- product_guide --
    PHONE_BUYING_GUIDE("phone_buying_guide", "手机选购指南", PRODUCT_GUIDE);

    private final String code;
    private final String desc;
    private final KnowledgeTypeEnum parent;

    /** 大类构造 */
    KnowledgeTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
        this.parent = null;
    }

    /** 子类构造 */
    KnowledgeTypeEnum(String code, String desc, KnowledgeTypeEnum parent) {
        this.code = code;
        this.desc = desc;
        this.parent = parent;
    }

    /** 是否为大类（无父节点） */
    public boolean isCategory() {
        return parent == null;
    }

    /** 是否为子类 */
    public boolean isSubType() {
        return parent != null;
    }

    public static KnowledgeTypeEnum getByCode(String code) {
        if (code == null) return null;
        for (KnowledgeTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
