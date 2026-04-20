package com.ecommerce.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.autoconfigure.vectorstore.milvus.MilvusVectorStoreAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 * 使用原生 MilvusServiceClient，禁用 Spring AI 自动配置避免冲突
 */
@Slf4j
@Configuration
@EnableAutoConfiguration(exclude = {
        MilvusVectorStoreAutoConfiguration.class
})
public class MilvusConfig {

    @Value("${spring.ai.vectorstore.milvus.host:localhost}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.port:19530}")
    private int port;

    /**
     * 原生 Milvus 客户端（用于自定义操作）
     * 如果连接失败，返回 null，让 MilvusServiceImpl 优雅降级
     */
    @Bean
    public MilvusServiceClient milvusClient() {
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .build();
            MilvusServiceClient client = new MilvusServiceClient(connectParam);
            // 测试连接
            client.checkHealth();
            log.info("Milvus 连接成功: {}:{}", host, port);
            return client;
        } catch (Exception e) {
            log.warn("Milvus 连接失败: {}:{}，向量功能将不可用", host, port);
            return null;
        }
    }
}
