package com.ecommerce.event;

import com.ecommerce.service.VectorSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 向量同步事件监听器
 * 监听商品变更事件，异步同步到向量库
 */
@Slf4j
@Component
public class VectorSyncListener {

    @Resource
    private VectorSyncService vectorSyncService;

    @Async
    @EventListener
    public void onProductChange(ProductChangeEvent event) {
        log.info("VectorSyncListener.onProductChange 收到商品变更事件: productId={}, type={}", event.getProductId(), event.getChangeType());

        try {
            switch (event.getChangeType()) {
                case CREATE:
                case UPDATE:
                    vectorSyncService.syncProduct(event.getProductId());
                    break;
                case DELETE:
                    vectorSyncService.removeProductFromVector(event.getProductId());
                    break;
                default:
                    log.warn("VectorSyncListener.onProductChange 未知的变更类型: {}", event.getChangeType());
            }
        } catch (Exception e) {
            log.error("VectorSyncListener.onProductChange 同步商品到向量库失败: productId={}", event.getProductId(), e);
        }
    }
}
