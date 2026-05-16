package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_quality_analysis")
public class AgentQualityAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentName;

    private LocalDate analysisDate;

    private Integer totalFeedback;

    private Integer likeCount;

    private Integer dislikeCount;

    private Double satisfactionRate;

    private String topDislikeReasons;

    private Integer abruptEndCount;

    private Integer repeatedQuestionCount;

    private Integer transferToHumanCount;

    private Integer totalSessions;

    private Double avgRounds;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
