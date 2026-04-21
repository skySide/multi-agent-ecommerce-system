package com.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class VectorStoreConfiguration {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // Spring AI 1.0.0-M5 的 SimpleVectorStore 构建方式
        return new SimpleVectorStore(embeddingModel);

    }
}
