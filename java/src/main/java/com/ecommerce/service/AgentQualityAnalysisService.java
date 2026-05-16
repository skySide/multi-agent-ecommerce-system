package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.AgentQualityAnalysis;

import java.time.LocalDate;
import java.util.List;

public interface AgentQualityAnalysisService extends IService<AgentQualityAnalysis> {

    /**
     * 查询某日所有Agent的质量分析数据
     */
    List<AgentQualityAnalysis> listByDate(LocalDate date);

    /**
     * 查询某Agent近N天的质量趋势
     */
    List<AgentQualityAnalysis> listByAgent(String agentName, int days);

    /**
     * 执行单日分析（按Agent维度汇总昨日数据）
     */
    void runDailyAnalysis();
}
