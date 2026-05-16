package com.ecommerce.task;

import com.ecommerce.service.AgentQualityAnalysisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agent质量分析定时任务
 * 每天凌晨2:00执行，分析前一天的会话质量数据，按Agent维度汇总
 */
@Slf4j
@Component
public class AgentQualityAnalysisTask {

    @Resource
    private AgentQualityAnalysisService agentQualityAnalysisService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void analyze() {
        log.info("AgentQualityAnalysisTask.analyze 开始执行");
        agentQualityAnalysisService.runDailyAnalysis();
        log.info("AgentQualityAnalysisTask.analyze 执行完成");
    }
}
