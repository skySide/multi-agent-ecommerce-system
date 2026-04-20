package com.ecommerce.service.impl;

import com.ecommerce.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Embedding 服务实现类
 * 调用 Python 服务生成向量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${embedding.service.url:http://localhost:8000}")
    private String embeddingServiceUrl;

    @Override
    public List<Float> generateEmbedding(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = objectMapper.writeValueAsString(
                    Collections.singletonMap("text", text)
            );

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    embeddingServiceUrl + "/api/v1/embedding",
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return parseEmbedding(jsonNode.get("embedding"));

        } catch (Exception e) {
            log.error("生成 Embedding 失败: {}", text, e);
            return null;
        }
    }

    @Override
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = objectMapper.writeValueAsString(
                    Collections.singletonMap("texts", texts)
            );

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    embeddingServiceUrl + "/api/v1/embedding/batch",
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return parseEmbeddings(jsonNode.get("embeddings"));

        } catch (Exception e) {
            log.error("批量生成 Embedding 失败", e);
            return null;
        }
    }

    @Override
    public void syncAllProductsToVector() {
        try {
            restTemplate.postForEntity(
                    embeddingServiceUrl + "/api/v1/sync/products",
                    null,
                    String.class
            );
            log.info("触发商品向量同步任务");
        } catch (Exception e) {
            log.error("同步商品向量失败", e);
        }
    }

    private List<Float> parseEmbedding(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<Float> embedding = new java.util.ArrayList<>();
        for (JsonNode n : node) {
            embedding.add(n.floatValue());
        }
        return embedding;
    }

    private List<List<Float>> parseEmbeddings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<List<Float>> embeddings = new java.util.ArrayList<>();
        for (JsonNode n : node) {
            embeddings.add(parseEmbedding(n));
        }
        return embeddings;
    }
}
