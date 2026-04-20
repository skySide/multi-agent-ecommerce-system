package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 推荐结果缓存实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("recommend_cache")
public class RecommendCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String cacheKey;

    private String userId;

    private String scene;

    private String products;

    private LocalDateTime expireTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
