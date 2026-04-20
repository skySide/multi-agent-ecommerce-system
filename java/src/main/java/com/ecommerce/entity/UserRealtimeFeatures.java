package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实时特征实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_realtime_features")
public class UserRealtimeFeatures {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private Integer viewCount1h;

    private Integer viewCount24h;

    private Integer clickCount24h;

    private Integer cartCount24h;

    private String lastViewProductId;

    private LocalDateTime lastViewTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
