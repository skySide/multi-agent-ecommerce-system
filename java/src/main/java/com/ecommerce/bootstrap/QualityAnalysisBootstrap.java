package com.ecommerce.bootstrap;

import com.ecommerce.service.AgentQualityAnalysisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 质量分析数据启动同步器
 * 项目启动后自动执行一次离线质量分析，确保 agent_quality_analysis 表中有初始数据
 *
 * <p>使用 ApplicationRunner（比 CommandLineRunner 晚执行），确保 SystemBootstrap
 * 的数据库初始化、模拟数据生成、向量同步等前置步骤已完成。</p>
 */
@Slf4j
@Component
@Order(1)
public class QualityAnalysisBootstrap implements ApplicationRunner {

    @Resource
    private AgentQualityAnalysisService agentQualityAnalysisService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("QualityAnalysisBootstrap.run 启动时同步质量分析数据开始");
        try {
            agentQualityAnalysisService.runDailyAnalysis();
            log.info("QualityAnalysisBootstrap.run 启动时质量分析同步完成");
        } catch (Exception e) {
            log.error("QualityAnalysisBootstrap.run 启动时质量分析同步失败: {}", e.getMessage(), e);
        }
    }
}
