package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.entity.AgentQualityAnalysis;
import com.ecommerce.service.AgentQualityAnalysisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    @Resource
    private AgentQualityAnalysisService agentQualityAnalysisService;

    /**
     * 获取某日各Agent质量分析数据
     */
    @GetMapping("/agent-stats")
    public Result<List<AgentQualityAnalysis>> getAgentStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now().minusDays(1);
        log.info("QualityController.getAgentStats date={}", queryDate);
        List<AgentQualityAnalysis> list = agentQualityAnalysisService.listByDate(queryDate);
        return Result.success(list);
    }

    /**
     * 获取某Agent近N天的质量趋势
     */
    @GetMapping("/agent-stats/{agentName}")
    public Result<List<AgentQualityAnalysis>> getAgentTrend(
            @PathVariable String agentName,
            @RequestParam(defaultValue = "30") int days) {
        log.info("QualityController.getAgentTrend agentName={} days={}", agentName, days);
        List<AgentQualityAnalysis> list = agentQualityAnalysisService.listByAgent(agentName, days);
        return Result.success(list);
    }

    /**
     * 获取全局质量概览（近N天汇总）
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam(defaultValue = "7") int days) {
        log.info("QualityController.getOverview days={}", days);
        List<AgentQualityAnalysis> all = agentQualityAnalysisService.listByAgent("recommend", days);
        // 汇总所有Agent数据
        List<AgentQualityAnalysis> allAgents = new java.util.ArrayList<>();
        for (String agent : List.of("recommend", "product_query", "knowledge_query", "compare", "chitchat")) {
            allAgents.addAll(agentQualityAnalysisService.listByAgent(agent, days));
        }

        long totalFeedback = allAgents.stream().mapToLong(a -> a.getTotalFeedback() != null ? a.getTotalFeedback() : 0).sum();
        long totalLike = allAgents.stream().mapToLong(a -> a.getLikeCount() != null ? a.getLikeCount() : 0).sum();
        long totalDislike = allAgents.stream().mapToLong(a -> a.getDislikeCount() != null ? a.getDislikeCount() : 0).sum();
        double overallRate = totalFeedback > 0 ? Math.round((double) totalLike / totalFeedback * 10000.0) / 100.0 : 0.0;
        long abruptEnd = allAgents.stream().mapToLong(a -> a.getAbruptEndCount() != null ? a.getAbruptEndCount() : 0).sum();
        long transferToHuman = allAgents.stream().mapToLong(a -> a.getTransferToHumanCount() != null ? a.getTransferToHumanCount() : 0).sum();
        long totalSessions = allAgents.stream().mapToLong(a -> a.getTotalSessions() != null ? a.getTotalSessions() : 0).sum();
        double transferRate = totalSessions > 0 ? Math.round((double) transferToHuman / totalSessions * 10000.0) / 100.0 : 0.0;

        Map<String, Object> overview = new java.util.LinkedHashMap<>();
        overview.put("totalFeedback", totalFeedback);
        overview.put("totalLike", totalLike);
        overview.put("totalDislike", totalDislike);
        overview.put("satisfactionRate", overallRate);
        overview.put("abruptEndCount", abruptEnd);
        overview.put("transferToHumanCount", transferToHuman);
        overview.put("transferRate", transferRate);
        overview.put("totalSessions", totalSessions);
        overview.put("days", days);

        return Result.success(overview);
    }
}
