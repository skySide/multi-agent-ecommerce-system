package com.ecommerce.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingConfig {

    @Value("${spring.ai.siliconflow.api-key}")
    private String siliconflowApiKey;

    @Value("${spring.ai.siliconflow.base-url}")
    private String siliconflowBaseUrl;

    @Value("${spring.ai.siliconflow.embedding-model}")
    private String siliconflowEmbeddingModel;

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        // 1. 构建硅基流动的OpenAiApi（兼容格式）
        OpenAiApi siliconflowApi = new OpenAiApi(siliconflowBaseUrl, siliconflowApiKey);

        // 2. 创建OpenAiEmbeddingModel（M5版本正确写法）
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(siliconflowApi);

        // 3. 设置默认模型名称（M5版本通过withDefaultOptions方法设置）
        embeddingModel.withDefaultOptions(
                org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                        .model(siliconflowEmbeddingModel)
                        .build()
        );

        return embeddingModel;
    }
}
