package com.ecommerce.service;

import com.ecommerce.common.constants.QualityConstants;
import com.ecommerce.entity.ConversationSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 重复提问检测器
 * 基于 intent + entities + 文本相似度 三层判定，异步执行
 */
@Slf4j
@Component
public class RepeatedQuestionDetector {

    @Resource
    private SessionQualityMetricsService sessionQualityMetricsService;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private MemoryService memoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 检测当前消息是否为重复提问
     *
     * @param session          当前会话
     * @param currentMessage   当前用户消息文本
     * @param currentIntent    当前识别的意图
     * @param currentEntities  当前识别的实体
     * @param currentRound     当前轮次
     */
    public void detect(ConversationSession session, String currentMessage,
                       String currentIntent, Map<String, Object> currentEntities, int currentRound) {
        // 步骤1: 解析 round_intents
        String roundIntentsJson = session.getRoundIntents();
        if (StringUtils.isBlank(roundIntentsJson) || roundIntentsJson.equals("[]")) {
            return;
        }

        List<Map<String, Object>> roundIntents;
        try {
            roundIntents = objectMapper.readValue(roundIntentsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("RepeatedQuestionDetector.detect - 解析 round_intents 失败, sessionId: {}",
                    session.getSessionId(), e);
            return;
        }

        // 步骤2: 遍历最近 N 轮（排除当前轮自身）
        int startIdx = Math.max(0, roundIntents.size() - QualityConstants.REPEATED_DETECTION_WINDOW);
        Map<String, Object> bestMatch = null;
        double bestSimilarity = 0;

        for (int i = startIdx; i < roundIntents.size(); i++) {
            Map<String, Object> roundData = roundIntents.get(i);
            int round = (int) roundData.get("round");
            // 跳过当前轮自身
            if (round == currentRound) {
                continue;
            }

            String histIntent = (String) roundData.get("intent");
            @SuppressWarnings("unchecked")
            Map<String, Object> histEntities = (Map<String, Object>) roundData.get("entities");

            // 步骤3: 比对 intent 是否相同
            if (!currentIntent.equals(histIntent)) {
                continue;
            }

            // 步骤4: 比对 entities 关键字段是否有交集
            if (!entitiesOverlap(currentEntities, histEntities)) {
                continue;
            }

            // 步骤5: 计算文本相似度
            double similarity = computeTextSimilarity(currentMessage, session);
            log.info("RepeatedQuestionDetector.detect - intent相同、实体重叠, round: {}, similarity: {}",
                    round, similarity);

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = roundData;
            }
        }

        // 步骤6: 超过阈值则记录重复提问事件
        if (bestMatch != null && bestSimilarity > QualityConstants.REPEATED_SIMILARITY_THRESHOLD) {
            String metricValue = buildMetricValue(currentRound, (int) bestMatch.get("round"),
                    currentIntent, currentEntities, bestMatch, bestSimilarity);
            int messageIndex = currentRound * 2 + 1;
            sessionQualityMetricsService.recordRepeatedQuestion(
                    session.getSessionId(), session.getUserId(), metricValue, messageIndex);
            log.info("RepeatedQuestionDetector.detect - 检测到重复提问, sessionId: {}, currentRound: {}, similarRound: {}, similarity: {}",
                    session.getSessionId(), currentRound, bestMatch.get("round"), bestSimilarity);
        }
    }

    /**
     * 判断两个实体 Map 的关键字段是否有交集
     */
    private boolean entitiesOverlap(Map<String, Object> entities1, Map<String, Object> entities2) {
        if (CollectionUtils.isEmpty(entities1) && CollectionUtils.isEmpty(entities2)) {
            return true;
        } else if (CollectionUtils.isEmpty(entities1) || CollectionUtils.isEmpty(entities2)) {
            return false;
        }

        // 检查可比较的字段：category, brand, product_name, product_names
        String[] comparableKeys = {"category", "brand", "product_name"};
        for (String key : comparableKeys) {
            Object v1 = entities1.get(key);
            Object v2 = entities2.get(key);
            if (v1 != null && v2 != null && v1.equals(v2)) {
                return true;
            }
        }

        // 检查 product_names 数组是否有交集
        Object pn1 = entities1.get("product_names");
        Object pn2 = entities2.get("product_names");
        if (pn1 instanceof List && pn2 instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list1 = (List<String>) pn1;
            @SuppressWarnings("unchecked")
            List<String> list2 = (List<String>) pn2;
            for (String item : list1) {
                if (list2.contains(item)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 计算当前消息与历史消息的文本相似度
     * 使用 EmbeddingService + 余弦相似度
     */
    private double computeTextSimilarity(String currentMessage, ConversationSession session) {
        try {
            // 步骤1: 从 dialog_history 提取最近几条用户消息
            List<String> history = memoryService.deserializeHistory(session.getDialogueHistory());
            List<String> userMessages = new ArrayList<>();
            for (String entry : history) {
                if (entry.startsWith("用户: ")) {
                    userMessages.add(entry.substring(4)); // 去掉 "用户: " 前缀
                }
            }

            // 步骤2: 取最近 N 条用户消息（排除当前消息本身）
            int startIdx = Math.max(0, userMessages.size() - QualityConstants.REPEATED_DETECTION_WINDOW - 1);
            List<String> recentUserMessages = userMessages.subList(startIdx,
                    Math.max(startIdx, userMessages.size() - 1)); // 排除最后一条（当前消息）

            if (recentUserMessages.isEmpty()) {
                return 0.0;
            }

            // 步骤3: 批量计算 embedding
            List<String> allTexts = new ArrayList<>();
            allTexts.add(currentMessage);
            allTexts.addAll(recentUserMessages);
            List<List<Float>> embeddings = embeddingService.generateEmbeddings(allTexts);

            if (CollectionUtils.isEmpty(embeddings)) {
                log.warn("RepeatedQuestionDetector.computeTextSimilarity - embedding生成失败，返回0");
                return 0.0;
            }

            List<Float> currentEmb = embeddings.get(0);
            if (currentEmb == null || currentEmb.isEmpty()) {
                log.warn("RepeatedQuestionDetector.computeTextSimilarity - 当前消息embedding为空，返回0");
                return 0.0;
            }

            double maxSimilarity = 0.0;

            // 步骤4: 逐个计算余弦相似度
            for (int i = 1; i < embeddings.size(); i++) {
                List<Float> histEmb = embeddings.get(i);
                if (histEmb == null || histEmb.isEmpty()) {
                    continue;
                }
                double sim = cosineSimilarity(currentEmb, histEmb);
                if (sim > maxSimilarity) {
                    maxSimilarity = sim;
                }
            }
            return maxSimilarity;
        } catch (Exception e) {
            log.error("RepeatedQuestionDetector.computeTextSimilarity - 计算相似度失败, sessionId: {}",
                    session.getSessionId(), e);
            return 0.0;
        }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private double cosineSimilarity(List<Float> v1, List<Float> v2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 构建重复提问事件的 metric_value JSON
     */
    private String buildMetricValue(int currentRound, int similarRound, String intent,
                                     Map<String, Object> currentEntities,
                                     Map<String, Object> matchedRound, double similarity) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("current_round", currentRound);
        value.put("similar_round", similarRound);
        value.put("intent", intent);
        value.put("current_entities", currentEntities);
        value.put("similar_entities", matchedRound.get("entities"));
        value.put("similarity", Math.round(similarity * 10000.0) / 10000.0);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("RepeatedQuestionDetector.buildMetricValue - 序列化失败", e);
            return "{}";
        }
    }
}
