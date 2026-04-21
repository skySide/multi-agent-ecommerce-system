package com.ecommerce.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 商品变更事件
 * 商品增删改时触发，用于同步向量库
 */
@Getter
public class ProductChangeEvent extends ApplicationEvent {

    private final String productId;
    private final ChangeType changeType;

    public ProductChangeEvent(Object source, String productId, ChangeType changeType) {
        super(source);
        this.productId = productId;
        this.changeType = changeType;
    }

    public enum ChangeType {
        CREATE,     // 新增
        UPDATE,     // 更新
        DELETE      // 删除
    }
}
