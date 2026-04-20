package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对话画像更新记录实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversation_profile_update")
public class ConversationProfileUpdate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String sessionId;

    private String updateType;

    private String oldValue;

    private String newValue;

    private BigDecimal confidence;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
