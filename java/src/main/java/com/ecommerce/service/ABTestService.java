package com.ecommerce.service;

import java.util.Map;

/**
 * A/B测试服务接口
 */
public interface ABTestService {

    /**
     * 分配实验组（默认实验）
     */
    Map<String, Object> assign(String userId);

    /**
     * 分配实验组（指定实验ID）
     */
    Map<String, Object> assign(String userId, String experimentId);
}
