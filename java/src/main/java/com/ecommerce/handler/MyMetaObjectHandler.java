package com.ecommerce.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 当 @TableField(fill = ...) 注解的字段为空时，自动填充默认值
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("MyMetaObjectHandler.insertFill 自动填充, 实体: {}", metaObject.getOriginalObject().getClass().getSimpleName());

        // 填充创建时间（INSERT 时）
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());

        // 填充更新时间（INSERT 时也填充）
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        // 填充逻辑删除字段默认值（INSERT 时）
        this.strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("MyMetaObjectHandler.updateFill 自动填充, 实体: {}", metaObject.getOriginalObject().getClass().getSimpleName());

        // 填充更新时间（UPDATE 时）
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
