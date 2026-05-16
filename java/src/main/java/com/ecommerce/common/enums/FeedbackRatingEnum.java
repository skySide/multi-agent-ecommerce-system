package com.ecommerce.common.enums;

/**
 * 反馈评分枚举
 * 定义用户对AI回复的评分
 */
public enum FeedbackRatingEnum {

    /** 点赞 */
    LIKE(1, "点赞"),

    /** 拉踩 */
    DISLIKE(-1, "拉踩");

    private final Integer code;
    private final String desc;

    FeedbackRatingEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据code获取枚举值
     *
     * @param code 评分code
     * @return 枚举值，未匹配时返回null
     */
    public static FeedbackRatingEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (FeedbackRatingEnum rating : values()) {
            if (rating.code.equals(code)) {
                return rating;
            }
        }
        return null;
    }
}
