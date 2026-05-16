package com.ecommerce.common.constants;

/**
 * 会话质量相关常量
 * 定义重复检测、质量指标类型、反馈原因等常量
 */
public class QualityConstants {

    /** 重复检测滑动窗口大小 */
    public static final int REPEATED_DETECTION_WINDOW = 5;

    /** 重复提问文本相似度阈值 */
    public static final double REPEATED_SIMILARITY_THRESHOLD = 0.85;

    /** round_intents 保留轮数 */
    public static final int ROUND_INTENTS_MAX_SIZE = 10;

    /** 会话超时时间（分钟），用于兜底检测 abrupt_end */
    public static final long SESSION_TIMEOUT_MINUTES = 30;

    // ===== 质量指标类型 =====

    /** 重复提问 */
    public static final String METRIC_REPEATED_QUESTION = "repeated_question";

    /** 会话突然结束 */
    public static final String METRIC_ABRUPT_END = "abrupt_end";

    /** 转人工 */
    public static final String METRIC_TRANSFER_TO_HUMAN = "transfer_to_human";

    /** 低参与度 */
    public static final String METRIC_LOW_ENGAGEMENT = "low_engagement";

    /** 会话恢复（用户离开超过30分钟后回来继续对话） */
    public static final String METRIC_SESSION_RESUMED = "session_resumed";

    // ===== 拉踩反馈原因 =====

    /** 回答不准确 */
    public static final String REASON_INACCURATE = "inaccurate";

    /** 答非所问 */
    public static final String REASON_IRRELEVANT = "irrelevant";

    /** 信息不完整 */
    public static final String REASON_INCOMPLETE = "incomplete";

    /** 回答太笼统 */
    public static final String REASON_TOO_GENERIC = "too_generic";

    /** 信息过时 */
    public static final String REASON_OUTDATED = "outdated";

    // ===== 点赞反馈原因 =====

    /** 有帮助 */
    public static final String REASON_HELPFUL = "helpful";

    /** 节省了时间 */
    public static final String REASON_SAVED_TIME = "saved_time";

    /** 其他 */
    public static final String REASON_OTHER = "other";

    private QualityConstants() {
        // 工具类，禁止实例化
    }
}
