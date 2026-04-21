package com.ecommerce;

import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MultiAgentApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MultiAgentApplication.class, args);
        // 强制打印Milvus配置，确认是否生效
        MilvusVectorStore vectorStore = context.getBean(MilvusVectorStore.class);
        System.out.println("✅ Milvus URI: " + vectorStore);
    }
}
