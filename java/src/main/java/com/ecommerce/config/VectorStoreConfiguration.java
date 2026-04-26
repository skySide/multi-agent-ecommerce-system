package com.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置
 * 使用 SimpleVectorStore（内存向量库）
 */
@Slf4j
@Configuration
public class VectorStoreConfiguration {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("VectorStoreConfiguration.vectorStore 使用 SimpleVectorStore（内存向量库）");
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
