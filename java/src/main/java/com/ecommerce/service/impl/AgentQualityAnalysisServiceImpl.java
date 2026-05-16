package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.constants.AgentConstants;
import com.ecommerce.common.enums.ErrorCodeEnum;
import com.ecommerce.common.enums.FeedbackRatingEnum;
import com.ecommerce.entity.AgentQualityAnalysis;
import com.ecommerce.entity.ChatFeedback;
import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.SessionQualityMetrics;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.AgentQualityAnalysisMapper;
import com.ecommerce.service.AgentQualityAnalysisService;
import com.ecommerce.service.ChatFeedbackService;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.SessionQualityMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent质量分析服务实现类
 * 负责按Agent维度汇总每日的反馈数据和质量指标，产出分析结果供看板展示
 *
 * <p>核心流程：
 * <ol>
 *   <li>通过 ChatFeedbackService 查询昨日有评分的反馈记录</li>
 *   <li>通过 ConversationSessionService 批量查询关联的 session，并按 message_index 解析 Agent</li>
 *   <li>按 Agent 汇总点赞数、拉踩数、差评原因分布</li>
 *   <li>通过 SessionQualityMetricsService 查询昨日质量事件，按 session 关联 Agent 汇总</li>
 *   <li>统计各 Agent 的会话数和平均轮数</li>
 *   <li>写入 agent_quality_analysis 表（按 agent_name + analysis_date upsert）</li>
 * </ol>
 */
@Slf4j
@Service
public class AgentQualityAnalysisServiceImpl extends ServiceImpl<AgentQualityAnalysisMapper, AgentQualityAnalysis>
        implements AgentQualityAnalysisService {

    /** 需要汇总的质量指标类型 */
    private static final List<String> QUALITY_METRIC_TYPES = List.of("abrupt_end", "repeated_question",
            "transfer_to_human");

    @Resource
    private ChatFeedbackService chatFeedbackService;

    @Resource
    private ConversationSessionService conversationSessionService;

    @Resource
    private SessionQualityMetricsService sessionQualityMetricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 分析维度：所有Agent名称列表，通过@PostConstruct从AgentConstants初始化 */
    private List<String> analysisAgentNames;

    @PostConstruct
    public void init() {
        this.analysisAgentNames = AgentConstants.ANALYSIS_AGENT_NAMES;
        log.info("AgentQualityAnalysisService.init - 分析维度初始化完成, agents: {}", analysisAgentNames);
    }

    /**
     * 查询某日所有Agent的质量分析数据
     * 供看板页面按日期查看各Agent指标
     *
     * @param date 分析日期
     * @return 该日期下所有Agent的分析结果
     */
    @Override
    public List<AgentQualityAnalysis> listByDate(LocalDate date) {
        return lambdaQuery()
                .eq(AgentQualityAnalysis::getAnalysisDate, date)
                .list();
    }

    /**
     * 查询某Agent近N天的质量趋势
     * 供看板页面展示单个Agent的满意度、反馈数等指标的时间趋势
     *
     * @param agentName Agent名称（recommend/product_query/knowledge_query/compare/chitchat）
     * @param days      天数
     * @return 该Agent最近N天的分析结果，按日期升序
     */
    @Override
    public List<AgentQualityAnalysis> listByAgent(String agentName, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return lambdaQuery()
                .eq(AgentQualityAnalysis::getAgentName, agentName)
                .ge(AgentQualityAnalysis::getAnalysisDate, startDate)
                .orderByAsc(AgentQualityAnalysis::getAnalysisDate)
                .list();
    }

    /**
     * 执行单日离线分析，分析前一天的数据
     *
     * <p>分析步骤：
     * <ol>
     *   <li>查询昨日所有有评分的 chat_feedback 记录</li>
     *   <li>提取 sessionIdSet，批量查询关联的 session</li>
     *   <li>按 Agent 维度汇总赞/踩数量和差评原因分布</li>
     *   <li>查询昨日 session_quality_metrics 质量事件</li>
     *   <li>为质量事件的 session 补充缓存（可能和反馈的 session 不完全重叠）</li>
     *   <li>按 Agent 汇总突然结束、重复提问、转人工事件数</li>
     *   <li>统计各 Agent 的会话数和平均轮数</li>
     *   <li>将汇总结果写入 agent_quality_analysis 表（按 agent_name + analysis_date upsert）</li>
     * </ol>
     */
    @Override
    public void runDailyAnalysis() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = LocalDate.now().atStartOfDay();

        log.info("AgentQualityAnalysisService.runDailyAnalysis - 开始分析, 日期: {}", yesterday);

        try {
            // 步骤1: 获取反馈数据 + 关联的 Session 缓存
            List<ChatFeedback> feedbacks = chatFeedbackService.listByTimeRangeAndRatings(
                    start, end, List.of(FeedbackRatingEnum.LIKE.getCode(), FeedbackRatingEnum.DISLIKE.getCode()));
            Map<String, ConversationSession> sessionCache = buildSessionCache(feedbacks);

            // 步骤2: 按 Agent 汇总反馈统计（赞/踩/差评原因）
            Map<String, Integer> agentLikeCounts = initAgentCountMap();
            Map<String, Integer> agentDislikeCounts = initAgentCountMap();
            Map<String, Map<String, Integer>> agentReasonCounts = initAgentReasonMap();
            aggregateFeedbackByAgent(feedbacks, sessionCache, agentLikeCounts, agentDislikeCounts, agentReasonCounts);

            // 步骤3: 查询按 Agent 汇总质量事件（突然结束/重复提问/转人工）
            List<SessionQualityMetrics> metrics = sessionQualityMetricsService.listByTimeRangeAndTypes(
                    start, end, QUALITY_METRIC_TYPES);
            supplementSessionCache(metrics, sessionCache);

            Map<String, Integer> agentAbruptEndCounts = initAgentCountMap();
            Map<String, Integer> agentRepeatedQuestionCounts = initAgentCountMap();
            Map<String, Integer> agentTransferToHumanCounts = initAgentCountMap();
            aggregateQualityMetricsByAgent(metrics, sessionCache,
                    agentAbruptEndCounts, agentRepeatedQuestionCounts, agentTransferToHumanCounts);

            // 步骤4: 统计各 Agent 的会话数和平均轮数
            Map<String, Integer> agentSessionCounts;
            Map<String, Double> agentAvgRounds;
            Map<String, Object> sessionStats = computeSessionStats(sessionCache);
            agentSessionCounts = uncheckedMapCast(sessionStats, "sessionCounts");
            agentAvgRounds = uncheckedMapCast(sessionStats, "avgRounds");

            // 步骤5: 持久化分析结果
            persistAnalysisResults(yesterday, agentLikeCounts, agentDislikeCounts, agentReasonCounts,
                    agentAbruptEndCounts, agentRepeatedQuestionCounts, agentTransferToHumanCounts,
                    agentSessionCounts, agentAvgRounds);

            log.info("AgentQualityAnalysisService.runDailyAnalysis - 分析完成, 日期: {}, agents: {}",
                    yesterday, analysisAgentNames.size());

        } catch (Exception e) {
            log.error("AgentQualityAnalysisService.runDailyAnalysis - 执行失败, 日期: {}", yesterday, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR.getCode(), e.getMessage());
        }
    }

    // ==================== 步骤1: 数据获取 ====================

    /**
     * 从反馈列表中提取 sessionId，批量查询并缓存 Session
     */
    private Map<String, ConversationSession> buildSessionCache(List<ChatFeedback> feedbacks) {
        Set<String> sessionIdSet = feedbacks.stream()
                .map(ChatFeedback::getSessionId)
                .collect(Collectors.toSet());
        if (sessionIdSet.isEmpty()) {
            return Collections.emptyMap();
        }
        return conversationSessionService.batchGetBySessionIds(sessionIdSet);
    }

    // ==================== 步骤2: 反馈统计 ====================

    /**
     * 初始化 Agent 整数统计 Map（所有 Agent 初始为 0）
     */
    private Map<String, Integer> initAgentCountMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String agent : analysisAgentNames) {
            map.put(agent, 0);
        }
        return map;
    }

    /**
     * 初始化 Agent 差评原因统计 Map
     */
    private Map<String, Map<String, Integer>> initAgentReasonMap() {
        Map<String, Map<String, Integer>> map = new LinkedHashMap<>();
        for (String agent : analysisAgentNames) {
            map.put(agent, new LinkedHashMap<>());
        }
        return map;
    }

    /**
     * 遍历反馈记录，按 Agent 汇总赞/踩数量和差评原因分布
     */
    private void aggregateFeedbackByAgent(List<ChatFeedback> feedbacks,
                                           Map<String, ConversationSession> sessionCache,
                                           Map<String, Integer> likeCounts,
                                           Map<String, Integer> dislikeCounts,
                                           Map<String, Map<String, Integer>> reasonCounts) {
        for (ChatFeedback fb : feedbacks) {
            ConversationSession session = sessionCache.get(fb.getSessionId());
            String agent = conversationSessionService.resolveAgentByMessageIndex(session, fb.getMessageIndex());

            if (FeedbackRatingEnum.LIKE.getCode().equals(fb.getRating())) {
                likeCounts.merge(agent, 1, Integer::sum);
            } else if (FeedbackRatingEnum.DISLIKE.getCode().equals(fb.getRating())) {
                dislikeCounts.merge(agent, 1, Integer::sum);
                if (!StringUtils.isBlank(fb.getFeedbackReason())) {
                    Map<String, Integer> reasonMap = reasonCounts.getOrDefault(agent, new LinkedHashMap<>());
                    for (String reason : fb.getFeedbackReason().split(",")) {
                        reasonMap.merge(reason.trim(), 1, Integer::sum);
                    }
                }
            }
        }
    }

    // ==================== 步骤3: 质量事件统计 ====================

    /**
     * 为质量事件的 session 补充缓存（可能和反馈的 session 不完全重叠）
     */
    private void supplementSessionCache(List<SessionQualityMetrics> metrics,
                                         Map<String, ConversationSession> sessionCache) {
        Set<String> missingIds = metrics.stream()
                .map(SessionQualityMetrics::getSessionId)
                .filter(sid -> !sessionCache.containsKey(sid))
                .collect(Collectors.toSet());
        if (!missingIds.isEmpty()) {
            Map<String, ConversationSession> extra = conversationSessionService
                    .batchGetBySessionIds(missingIds);
            sessionCache.putAll(extra);
        }
    }

    /**
     * 按 session 分组质量事件，解析归属 Agent 后汇总计数
     */
    private void aggregateQualityMetricsByAgent(List<SessionQualityMetrics> metrics,
                                                 Map<String, ConversationSession> sessionCache,
                                                 Map<String, Integer> abruptEndCounts,
                                                 Map<String, Integer> repeatedQuestionCounts,
                                                 Map<String, Integer> transferToHumanCounts) {
        Map<String, List<SessionQualityMetrics>> metricsBySession = metrics.stream()
                .collect(Collectors.groupingBy(SessionQualityMetrics::getSessionId));

        for (Map.Entry<String, List<SessionQualityMetrics>> entry : metricsBySession.entrySet()) {
            String sid = entry.getKey();
            ConversationSession session = sessionCache.get(sid);
            String agent = conversationSessionService.resolveAgent(session);

            for (SessionQualityMetrics m : entry.getValue()) {
                switch (m.getMetricType()) {
                    case "abrupt_end" -> abruptEndCounts.merge(agent, 1, Integer::sum);
                    case "repeated_question" -> repeatedQuestionCounts.merge(agent, 1, Integer::sum);
                    case "transfer_to_human" -> transferToHumanCounts.merge(agent, 1, Integer::sum);
                }
            }
        }
    }

    // ==================== 步骤4: 会话统计 ====================

    /**
     * 统计各 Agent 的会话数和平均对话轮数
     */
    private Map<String, Object> computeSessionStats(Map<String, ConversationSession> sessionCache) {
        Map<String, Integer> sessionCounts = initAgentCountMap();
        Map<String, Double> avgRounds = new LinkedHashMap<>();
        Set<String> counted = new HashSet<>();

        for (String agent : analysisAgentNames) {
            int sessionCount = 0;
            int totalRounds = 0;
            for (Map.Entry<String, ConversationSession> entry : sessionCache.entrySet()) {
                ConversationSession session = entry.getValue();
                String resolvedAgent = conversationSessionService.resolveAgent(session);
                String countKey = entry.getKey() + "_" + agent;
                if (agent.equals(resolvedAgent) && counted.add(countKey)) {
                    sessionCount++;
                    totalRounds += countRounds(session.getDialogueHistory());
                }
            }
            sessionCounts.put(agent, sessionCount);
            avgRounds.put(agent, sessionCount > 0
                    ? Math.round((double) totalRounds / sessionCount * 10.0) / 10.0 : 0.0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionCounts", sessionCounts);
        result.put("avgRounds", avgRounds);
        return result;
    }

    private int countRounds(String dialogueHistory) {
        if (dialogueHistory == null || dialogueHistory.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = dialogueHistory.indexOf("用户:", idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }

    // ==================== 步骤5: 持久化 ====================

    /**
     * 将汇总结果按 Agent 逐条写入 agent_quality_analysis 表（saveOrUpdate）
     */
    private void persistAnalysisResults(LocalDate yesterday,
                                         Map<String, Integer> likeCounts,
                                         Map<String, Integer> dislikeCounts,
                                         Map<String, Map<String, Integer>> reasonCounts,
                                         Map<String, Integer> abruptEndCounts,
                                         Map<String, Integer> repeatedQuestionCounts,
                                         Map<String, Integer> transferToHumanCounts,
                                         Map<String, Integer> sessionCounts,
                                         Map<String, Double> avgRounds) throws Exception {
        for (String agent : analysisAgentNames) {
            int likeCount = likeCounts.getOrDefault(agent, 0);
            int dislikeCount = dislikeCounts.getOrDefault(agent, 0);
            long totalFb = likeCount + dislikeCount;
            double satisfactionRate = totalFb > 0
                    ? Math.round((double) likeCount / totalFb * 10000.0) / 100.0 : 0.0;

            String topReasonsJson = buildTopDislikeReasonsJson(reasonCounts.getOrDefault(agent, new LinkedHashMap<>()));

            AgentQualityAnalysis record = AgentQualityAnalysis.builder()
                    .agentName(agent)
                    .analysisDate(yesterday)
                    .totalFeedback((int) totalFb)
                    .likeCount(likeCount)
                    .dislikeCount(dislikeCount)
                    .satisfactionRate(satisfactionRate)
                    .topDislikeReasons(topReasonsJson)
                    .abruptEndCount(abruptEndCounts.getOrDefault(agent, 0))
                    .repeatedQuestionCount(repeatedQuestionCounts.getOrDefault(agent, 0))
                    .transferToHumanCount(transferToHumanCounts.getOrDefault(agent, 0))
                    .totalSessions(sessionCounts.getOrDefault(agent, 0))
                    .avgRounds(avgRounds.getOrDefault(agent, 0.0))
                    .build();

            AgentQualityAnalysis existing = lambdaQuery()
                    .eq(AgentQualityAnalysis::getAgentName, agent)
                    .eq(AgentQualityAnalysis::getAnalysisDate, yesterday)
                    .one();
            if (Objects.nonNull(existing)) {
                record.setId(existing.getId());
                updateById(record);
            } else {
                save(record);
            }
        }
    }

    /**
     * 构建 Top-5 差评原因 JSON
     */
    private String buildTopDislikeReasonsJson(Map<String, Integer> reasonMap) throws Exception {
        return objectMapper.writeValueAsString(
                reasonMap.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(5)
                        .map(e -> Map.of("reason", e.getKey(), "count", e.getValue()))
                        .toList()
        );
    }

    // ==================== 工具方法 ====================

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedMapCast(Map<String, Object> map, String key) {
        return (T) map.get(key);
    }
}
