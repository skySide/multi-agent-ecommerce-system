package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.constants.AgentConstants;
import com.ecommerce.common.enums.FeedbackRatingEnum;
import com.ecommerce.entity.AgentQualityAnalysis;
import com.ecommerce.entity.ChatFeedback;
import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.SessionQualityMetrics;
import com.ecommerce.mapper.AgentQualityAnalysisMapper;
import com.ecommerce.service.AgentQualityAnalysisService;
import com.ecommerce.service.ChatFeedbackService;
import com.ecommerce.service.ConversationSessionService;
import com.ecommerce.service.SessionQualityMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
        // 步骤1: 计算分析日期范围（昨日0点到今日0点）
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = LocalDate.now().atStartOfDay();

        log.info("AgentQualityAnalysisService.runDailyAnalysis - 开始分析, 日期: {}", yesterday);

        try {
            // 步骤2: 查询昨日所有有评分的反馈记录
            List<ChatFeedback> feedbacks = chatFeedbackService.listByTimeRangeAndRatings(
                    start, end, List.of(FeedbackRatingEnum.LIKE.getCode(), FeedbackRatingEnum.DISLIKE.getCode()));

            // 步骤3: 从反馈中提取 sessionIdSet，批量查询 session
            Set<String> feedbackSessionIdSet = feedbacks.stream()
                    .map(ChatFeedback::getSessionId).collect(Collectors.toSet());
            Map<String, ConversationSession> sessionCache = conversationSessionService
                    .batchGetBySessionIds(feedbackSessionIdSet);

            // 步骤4: 初始化各Agent的统计Map
            Map<String, Integer> agentLikeCounts = new LinkedHashMap<>();
            Map<String, Integer> agentDislikeCounts = new LinkedHashMap<>();
            Map<String, Map<String, Integer>> agentReasonCounts = new LinkedHashMap<>();
            for (String agent : analysisAgentNames) {
                agentLikeCounts.put(agent, 0);
                agentDislikeCounts.put(agent, 0);
                agentReasonCounts.put(agent, new LinkedHashMap<>());
            }

            // 步骤5: 遍历反馈记录，按Agent汇总点赞数和拉踩数
            for (int i = 0; i < feedbacks.size(); i++) {
                ChatFeedback fb = feedbacks.get(i);
                ConversationSession session = sessionCache.get(fb.getSessionId());
                String agent = conversationSessionService.resolveAgentByMessageIndex(session, fb.getMessageIndex());

                if (FeedbackRatingEnum.LIKE.getCode().equals(fb.getRating())) {
                    agentLikeCounts.merge(agent, 1, Integer::sum);
                } else if (FeedbackRatingEnum.DISLIKE.getCode().equals(fb.getRating())) {
                    agentDislikeCounts.merge(agent, 1, Integer::sum);
                    // 解析差评原因，按原因标签统计
                    if (fb.getFeedbackReason() != null && !fb.getFeedbackReason().isEmpty()) {
                        Map<String, Integer> reasonMap = agentReasonCounts.get(agent);
                        for (String reason : fb.getFeedbackReason().split(",")) {
                            reasonMap.merge(reason.trim(), 1, Integer::sum);
                        }
                    }
                }
            }

            // 步骤6: 查询昨日所有质量指标事件
            List<SessionQualityMetrics> metrics = sessionQualityMetricsService.listByTimeRangeAndTypes(
                    start, end, QUALITY_METRIC_TYPES);

            // 步骤7: 为质量事件的 session 补充缓存（可能和反馈的 session 不完全重叠）
            Set<String> metricsSessionIdSet = metrics.stream()
                    .map(SessionQualityMetrics::getSessionId)
                    .filter(sid -> !sessionCache.containsKey(sid))
                    .collect(Collectors.toSet());
            if (!metricsSessionIdSet.isEmpty()) {
                Map<String, ConversationSession> metricsSessionCache = conversationSessionService
                        .batchGetBySessionIds(metricsSessionIdSet);
                sessionCache.putAll(metricsSessionCache);
            }

            // 步骤8: 初始化质量指标统计Map
            Map<String, Integer> agentAbruptEndCounts = new LinkedHashMap<>();
            Map<String, Integer> agentRepeatedQuestionCounts = new LinkedHashMap<>();
            Map<String, Integer> agentTransferToHumanCounts = new LinkedHashMap<>();
            for (String agent : analysisAgentNames) {
                agentAbruptEndCounts.put(agent, 0);
                agentRepeatedQuestionCounts.put(agent, 0);
                agentTransferToHumanCounts.put(agent, 0);
            }

            // 步骤9: 按 session_id 分组，遍历质量指标事件按Agent汇总
            Map<String, List<SessionQualityMetrics>> metricsBySession = metrics.stream()
                    .collect(Collectors.groupingBy(SessionQualityMetrics::getSessionId));

            for (Map.Entry<String, List<SessionQualityMetrics>> entry : metricsBySession.entrySet()) {
                String sid = entry.getKey();
                ConversationSession session = sessionCache.get(sid);
                String agent = conversationSessionService.resolveAgent(session);

                for (SessionQualityMetrics m : entry.getValue()) {
                    switch (m.getMetricType()) {
                        case "abrupt_end" -> agentAbruptEndCounts.merge(agent, 1, Integer::sum);
                        case "repeated_question" -> agentRepeatedQuestionCounts.merge(agent, 1, Integer::sum);
                        case "transfer_to_human" -> agentTransferToHumanCounts.merge(agent, 1, Integer::sum);
                        default -> {
                            // 未知指标类型忽略
                        }
                    }
                }
            }

            // 步骤10: 统计各Agent的会话数和平均对话轮数
            Map<String, Integer> agentSessionCounts = new LinkedHashMap<>();
            Map<String, Double> agentAvgRounds = new LinkedHashMap<>();
            Set<String> countedSessions = new HashSet<>();

            for (String agent : analysisAgentNames) {
                int sessionCount = 0;
                int totalRounds = 0;
                for (Map.Entry<String, ConversationSession> entry : sessionCache.entrySet()) {
                    ConversationSession session = entry.getValue();
                    String resolvedAgent = conversationSessionService.resolveAgent(session);
                    String countKey = entry.getKey() + "_" + agent;
                    if (agent.equals(resolvedAgent) && countedSessions.add(countKey)) {
                        sessionCount++;
                        totalRounds += countRounds(session.getDialogueHistory());
                    }
                }
                agentSessionCounts.put(agent, sessionCount);
                agentAvgRounds.put(agent, sessionCount > 0
                        ? Math.round((double) totalRounds / sessionCount * 10.0) / 10.0 : 0.0);
            }

            // 步骤11: 将汇总结果写入 agent_quality_analysis 表（按 agent_name + analysis_date upsert）
            for (String agent : analysisAgentNames) {
                int likeCount = agentLikeCounts.getOrDefault(agent, 0);
                int dislikeCount = agentDislikeCounts.getOrDefault(agent, 0);
                long totalFb = likeCount + dislikeCount;
                double satisfactionRate = totalFb > 0
                        ? Math.round((double) likeCount / totalFb * 10000.0) / 100.0 : 0.0;

                // 构建Top5差评原因JSON
                Map<String, Integer> reasonMap = agentReasonCounts.getOrDefault(agent, new LinkedHashMap<>());
                String topReasonsJson = objectMapper.writeValueAsString(
                        reasonMap.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(5)
                                .map(e -> Map.of("reason", e.getKey(), "count", e.getValue()))
                                .toList()
                );

                // 删除旧记录后插入新记录（upsert）
                lambdaUpdate()
                        .eq(AgentQualityAnalysis::getAgentName, agent)
                        .eq(AgentQualityAnalysis::getAnalysisDate, yesterday)
                        .remove();

                AgentQualityAnalysis record = AgentQualityAnalysis.builder()
                        .agentName(agent)
                        .analysisDate(yesterday)
                        .totalFeedback((int) totalFb)
                        .likeCount(likeCount)
                        .dislikeCount(dislikeCount)
                        .satisfactionRate(satisfactionRate)
                        .topDislikeReasons(topReasonsJson)
                        .abruptEndCount(agentAbruptEndCounts.getOrDefault(agent, 0))
                        .repeatedQuestionCount(agentRepeatedQuestionCounts.getOrDefault(agent, 0))
                        .transferToHumanCount(agentTransferToHumanCounts.getOrDefault(agent, 0))
                        .totalSessions(agentSessionCounts.getOrDefault(agent, 0))
                        .avgRounds(agentAvgRounds.getOrDefault(agent, 0.0))
                        .build();
                save(record);
            }

            log.info("AgentQualityAnalysisService.runDailyAnalysis - 分析完成, 日期: {}, agents: {}",
                    yesterday, analysisAgentNames.size());

        } catch (Exception e) {
            log.error("AgentQualityAnalysisService.runDailyAnalysis - 执行失败, 日期: {}", yesterday, e);
        }
    }

    /**
     * 计算对话历史中的轮数（用户消息数）
     *
     * @param dialogueHistory 对话历史JSON字符串
     * @return 用户消息的条数，即对话轮数
     */
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
}
