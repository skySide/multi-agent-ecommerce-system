package com.ecommerce.service.impl;

import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;
import com.ecommerce.model.Product;
import com.ecommerce.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 * 多轮对话 + 意图识别 + RAG问答 + 对话式推荐
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ChatClient chatClient;
    @Resource
    private ConversationSessionService conversationSessionService;
    @Resource
    private RecommendEngineService recommendEngineService;
    @Resource
    private DocumentVectorService documentVectorService;
    @Resource
    private ProductService productService;
    @Resource
    private UserBehaviorService userBehaviorService;
    @Resource
    @Lazy
    private UserProfileService userProfileService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 最大历史轮数
    private static final int MAX_HISTORY_ROUNDS = 10;

    @Override
    public ConversationResponse chat(ConversationRequest request) {
        String userId = request.getUserId();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        log.info("ConversationServiceImpl.chat 用户={} 会话={} 消息={}", userId, sessionId, message);

        // 1. 获取或创建会话（校验 sessionId 归属，防止跨用户访问）
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = createSession(userId);
        }
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null) {
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        } else if (!userId.equals(session.getUserId())) {
            // sessionId 不属于当前用户，拒绝访问，创建新会话
            log.warn("ConversationServiceImpl.chat 会话归属校验失败: sessionId={} 属于 userId={}, 请求 userId={}",
                    sessionId, session.getUserId(), userId);
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        }

        // 2. 解析对话历史
        List<String> history = parseHistory(session.getDialogueHistory());

        // 3. 意图识别 + 实体抽取
        IntentResult intentResult = recognizeIntent(message, history);
        String intent = intentResult.intent;
        Map<String, Object> entities = intentResult.entities;

        log.info("ConversationServiceImpl.chat 意图识别结果: intent={} entities={}", intent, entities);

        // 4. 根据意图处理
        ConversationResponse response;
        switch (intent) {
            case "recommend":
                response = handleRecommend(userId, sessionId, message, history, entities);
                break;
            case "product_query":
                response = handleProductQuery(userId, sessionId, message, history, entities);
                break;
            case "knowledge_query":
                response = handleKnowledgeQuery(userId, sessionId, message, history);
                break;
            case "compare":
                response = handleCompare(userId, sessionId, message, history, entities);
                break;
            case "chitchat":
            default:
                response = handleChitchat(userId, sessionId, message, history);
                break;
        }

        // 5. 保存对话历史
        history.add("用户: " + message);
        history.add("助手: " + response.getMessage());
        if (history.size() > MAX_HISTORY_ROUNDS * 2) {
            history = history.subList(history.size() - MAX_HISTORY_ROUNDS * 2, history.size());
        }
        saveHistory(session, history, entities);

        response.setSessionId(sessionId);
        response.setDialogueHistory(history);
        response.setIntent(intent);
        response.setExtractedInfo(entities);

        // 6. 记录用户行为
        try {
            userBehaviorService.recordBehavior(userId, null, "chat", message, "conversation");
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.chat 记录行为失败: {}", e.getMessage());
        }

        log.info("ConversationServiceImpl.chat 响应完成 session={} intent={}", sessionId, intent);
        return response;
    }

    @Override
    public String createSession(String userId) {
        ConversationSession session = conversationSessionService.createSession(userId);
        log.info("ConversationServiceImpl.createSession 创建会话 userId={} sessionId={}", userId, session.getSessionId());
        return session.getSessionId();
    }

    @Override
    public List<String> getSessionHistory(String sessionId) {
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null || session.getDialogueHistory() == null) {
            return List.of();
        }
        return parseHistory(session.getDialogueHistory());
    }

    @Override
    public boolean endSession(String sessionId) {
        log.info("ConversationServiceImpl.endSession 结束会话 sessionId={}", sessionId);
        return conversationSessionService.endSession(sessionId);
    }

    // ========== 意图处理 ==========

    private ConversationResponse handleRecommend(String userId, String sessionId, String message,
                                                  List<String> history, Map<String, Object> entities) {
        // 读取会话记忆（extracted_info），合并跨轮次累积的实体
        Map<String, Object> mergedEntities = mergeWithSessionMemory(sessionId, entities);

        // 构建用户画像（从合并后实体中提取偏好）
        UserProfile profile = buildProfileFromEntities(userId, mergedEntities);

        // 注入长期记忆：读取 user_profile 历史偏好
        String longTermContext = buildLongTermContext(userId);

        // 调用推荐引擎
        int numItems = mergedEntities.get("num_items") instanceof Number
                ? ((Number) mergedEntities.get("num_items")).intValue() : 6;
        List<com.ecommerce.entity.Product> recProducts = recommendEngineService.recommend(
                userId, profile, numItems, mergedEntities);

        // 转换为 model.Product
        List<Product> modelProducts = recProducts.stream()
                .map(this::convertToModel)
                .collect(Collectors.toList());

        // 生成回复（注入对话历史 + 长期记忆背景）
        String reply = generateRecommendReply(message, modelProducts, profile, history, longTermContext);

        return ConversationResponse.builder()
                .message(reply)
                .recommendedProducts(modelProducts)
                .build();
    }

    private ConversationResponse handleProductQuery(String userId, String sessionId, String message,
                                                     List<String> history, Map<String, Object> entities) {
        String productName = entities.get("product_name") instanceof String
                ? (String) entities.get("product_name") : message;

        // 搜索商品
        List<com.ecommerce.entity.Product> products = productService.searchByKeyword(productName, 5);

        if (products.isEmpty()) {
            return ConversationResponse.builder()
                    .message("抱歉，我没有找到相关商品。您可以换个关键词试试，或者告诉我您的需求，我帮您推荐。")
                    .build();
        }

        List<Product> modelProducts = products.stream()
                .map(this::convertToModel)
                .collect(Collectors.toList());

        // 注入对话历史，让回复更连贯
        String historyText = "";
        if (!history.isEmpty()) {
            List<String> recent = history.subList(Math.max(0, history.size() - 4), history.size());
            historyText = "对话历史：\n" + String.join("\n", recent) + "\n\n";
        }

        String reply = generateProductQueryReply(modelProducts, historyText);

        return ConversationResponse.builder()
                .message(reply)
                .recommendedProducts(modelProducts)
                .build();
    }

    private ConversationResponse handleKnowledgeQuery(String userId, String sessionId, String message,
                                                       List<String> history) {
        // RAG 搜索知识库
        List<Document> docs = documentVectorService.searchKnowledgeBase(message, 3);

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 注入最近3轮对话历史，支持指代消解
        String historyText = "";
        if (!history.isEmpty()) {
            List<String> recent = history.subList(Math.max(0, history.size() - 6), history.size());
            historyText = "对话历史：\n" + String.join("\n", recent) + "\n\n";
        }

        String prompt = String.format(
                "你是电商客服助手。根据以下知识库内容回答用户问题。如果知识库中没有相关信息，请基于常识回答。\n" +
                        "知识库内容：\n%s\n\n%s用户问题：%s\n\n请用中文回答，简洁友好。",
                context.isEmpty() ? "（暂无相关知识）" : context,
                historyText,
                message
        );

        String reply = chatClient.prompt().user(prompt).call().content();

        return ConversationResponse.builder()
                .message(reply.trim())
                .build();
    }

    private ConversationResponse handleCompare(String userId, String sessionId, String message,
                                                List<String> history, Map<String, Object> entities) {
        @SuppressWarnings("unchecked")
        List<String> productNames = entities.get("product_names") instanceof List
                ? (List<String>) entities.get("product_names") : List.of();

        if (productNames.size() < 2) {
            return ConversationResponse.builder()
                    .message("请告诉我您想对比哪几款商品，比如\"iPhone 16 和 华为 Mate 70 哪个好？\"")
                    .build();
        }

        List<com.ecommerce.entity.Product> products = new ArrayList<>();
        for (String name : productNames.subList(0, Math.min(3, productNames.size()))) {
            List<com.ecommerce.entity.Product> found = productService.searchByKeyword(name, 1);
            if (!found.isEmpty()) {
                products.add(found.get(0));
            }
        }

        if (products.size() < 2) {
            return ConversationResponse.builder()
                    .message("抱歉，我只找到了部分商品信息，请确认商品名称是否正确。")
                    .build();
        }

        String comparison = generateComparisonReply(products);

        return ConversationResponse.builder()
                .message(comparison)
                .recommendedProducts(products.stream().map(this::convertToModel).collect(Collectors.toList()))
                .build();
    }

    private ConversationResponse handleChitchat(String userId, String sessionId, String message,
                                                 List<String> history) {
        String historyText = String.join("\n", history.subList(
                Math.max(0, history.size() - 6), history.size()));

        String prompt = String.format(
                "你是淘宝智能购物助手，友好、专业、幽默。和用户进行自然对话。\n" +
                        "对话历史：\n%s\n\n用户：%s\n\n助手：",
                historyText, message
        );

        String reply = chatClient.prompt().user(prompt).call().content();

        return ConversationResponse.builder()
                .message(reply.trim())
                .build();
    }

    // ========== 意图识别 ==========

    private IntentResult recognizeIntent(String message, List<String> history) {
        // 取最近3轮历史辅助意图识别，支持指代消解（如"那换货呢"）
        String recentHistory = "";
        if (!history.isEmpty()) {
            List<String> recent = history.subList(Math.max(0, history.size() - 6), history.size());
            recentHistory = "\n对话历史（最近3轮）：\n" + String.join("\n", recent) + "\n";
        }

        String prompt = String.format(
                "分析用户意图，输出JSON：{\"intent\":\"recommend|product_query|knowledge_query|compare|chitchat\",\"entities\":{...}}\n" +
                        "意图说明：\n" +
                        "- recommend: 用户想要推荐商品（如\"推荐手机\"\"适合学生的笔记本\"）\n" +
                        "- product_query: 用户询问某款商品（如\"iPhone 16 多少钱\"\"华为 Mate 70 怎么样\"）\n" +
                        "- knowledge_query: 用户问售后/物流/优惠活动等知识性问题\n" +
                        "- compare: 用户对比商品（如\"iPhone 和 华为哪个好\"）\n" +
                        "- chitchat: 闲聊\n" +
                        "实体可包含：category(类目), brand(品牌), price_min, price_max, product_name(商品名), product_names(商品名数组), num_items\n" +
                        "%s用户消息：%s\n只输出JSON。",
                recentHistory, message
        );

        try {
            String response = chatClient.prompt().user(prompt).call().content();
            String cleaned = response.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(cleaned, Map.class);
            String intent = (String) result.getOrDefault("intent", "chitchat");
            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) result.getOrDefault("entities", Map.of());

            return new IntentResult(intent, entities);
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.recognizeIntent 意图识别失败: {}", e.getMessage());
            return new IntentResult("chitchat", Map.of());
        }
    }

    // ========== 回复生成 ==========

    private String generateRecommendReply(String message, List<Product> products, UserProfile profile,
                                           List<String> history, String longTermContext) {
        if (products.isEmpty()) {
            return "抱歉，暂时没有合适的商品推荐给您。您可以告诉我更具体的需求，比如预算、品牌偏好等。";
        }

        StringBuilder sb = new StringBuilder("根据您的需求，我为您精选了以下商品：\n\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append(String.format("%d. %s（%s）¥%.0f - %s\n",
                    i + 1, p.getName(), p.getBrand(), p.getPrice(), p.getCategory()));
        }
        sb.append("\n如果您想了解某款商品的详细信息，直接问我就可以啦！");
        return sb.toString();
    }

    private String generateProductQueryReply(List<Product> products, String historyText) {
        if (products.isEmpty()) {
            return "抱歉，没有找到相关商品。";
        }
        Product p = products.get(0);
        return String.format("%s为您找到 %s：\n价格：¥%.0f\n品牌：%s\n类目：%s\n%s",
                historyText.isEmpty() ? "" : "",  // history 已在 prompt 层处理
                p.getName(), p.getPrice(), p.getBrand(), p.getCategory(),
                p.getDescription() != null ? "描述：" + p.getDescription() + "\n" : "");
    }

    private String generateComparisonReply(List<com.ecommerce.entity.Product> products) {
        StringBuilder sb = new StringBuilder("为您对比以下商品：\n\n");
        for (com.ecommerce.entity.Product p : products) {
            sb.append(String.format("【%s】\n价格：¥%.0f | 品牌：%s | 评分：%.1f | 销量：%d\n%s\n\n",
                    p.getProductName(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                    p.getBrand(),
                    p.getRating() != null ? p.getRating().doubleValue() : 0,
                    p.getSalesCount() != null ? p.getSalesCount() : 0,
                    p.getProductDescription() != null ? p.getProductDescription() : ""));
        }
        sb.append("如果您需要更详细的对比分析，请告诉我您最关注哪些方面（如性能、续航、拍照等）。");
        return sb.toString();
    }

    // ========== 工具方法 ==========

    private UserProfile buildProfileFromEntities(String userId, Map<String, Object> entities) {
        UserProfile profile = UserProfile.builder().userId(userId).build();

        // 类目偏好 → entity 存逗号分隔字符串
        if (entities.get("category") instanceof String) {
            profile.setPreferredCategories((String) entities.get("category"));
        } else if (entities.get("category") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> cats = (List<String>) entities.get("category");
            profile.setPreferredCategories(String.join(",", cats));
        }

        // 品牌偏好 → entity 存逗号分隔字符串
        if (entities.get("brand") instanceof String) {
            profile.setPreferredBrands((String) entities.get("brand"));
        }

        // 价格范围
        double priceMin = 0;
        double priceMax = 100000;
        if (entities.get("price_min") instanceof Number) {
            priceMin = ((Number) entities.get("price_min")).doubleValue();
        }
        if (entities.get("price_max") instanceof Number) {
            priceMax = ((Number) entities.get("price_max")).doubleValue();
        }
        profile.setPriceRangeMin(java.math.BigDecimal.valueOf(priceMin));
        profile.setPriceRangeMax(java.math.BigDecimal.valueOf(priceMax));

        return profile;
    }

    private Product convertToModel(com.ecommerce.entity.Product entity) {
        if (entity == null) return null;
        return Product.builder()
                .productId(entity.getProductId())
                .name(entity.getProductName())
                .category(entity.getCategoryName())
                .price(entity.getPrice() != null ? entity.getPrice().doubleValue() : 0)
                .description(entity.getProductDescription())
                .brand(entity.getBrand())
                .stock(entity.getStock() != null ? entity.getStock() : 0)
                .score(entity.getRating() != null ? entity.getRating().doubleValue() : 0)
                .build();
    }

    private List<String> parseHistory(String historyJson) {
        if (historyJson == null || historyJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(historyJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveHistory(ConversationSession session, List<String> history, Map<String, Object> entities) {
        try {
            session.setDialogueHistory(objectMapper.writeValueAsString(history));
            // 合并式更新 extracted_info：新值覆盖旧值，未覆盖字段保留
            Map<String, Object> merged = mergeExtractedInfo(session.getExtractedInfo(), entities);
            session.setExtractedInfo(objectMapper.writeValueAsString(merged));
            session.setUpdateTime(LocalDateTime.now());
            conversationSessionService.updateById(session);
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.saveHistory 保存历史失败: {}", e.getMessage());
        }
    }

    /**
     * 合并会话记忆：新值覆盖旧值，未覆盖字段保留
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeExtractedInfo(String existingJson, Map<String, Object> newEntities) {
        Map<String, Object> existing = new HashMap<>();
        if (existingJson != null && !existingJson.isEmpty() && !existingJson.equals("{}")) {
            try {
                existing = objectMapper.readValue(existingJson, Map.class);
            } catch (Exception e) {
                log.warn("ConversationServiceImpl.mergeExtractedInfo 解析失败: {}", e.getMessage());
            }
        }
        existing.putAll(newEntities); // 新值覆盖旧值，旧字段保留
        return existing;
    }

    /**
     * 读取会话记忆（extracted_info），合并到当前实体
     * 通过 sessionId 隔离，不同用户的会话记忆互不干扰
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeWithSessionMemory(String sessionId, Map<String, Object> currentEntities) {
        try {
            ConversationSession session = conversationSessionService.getBySessionId(sessionId);
            if (session == null || session.getExtractedInfo() == null) return currentEntities;
            Map<String, Object> sessionMemory = objectMapper.readValue(session.getExtractedInfo(), Map.class);
            // 会话记忆作为基础，当前轮次实体覆盖（当前轮次优先级更高）
            Map<String, Object> merged = new HashMap<>(sessionMemory);
            merged.putAll(currentEntities);
            return merged;
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.mergeWithSessionMemory 读取会话记忆失败: {}", e.getMessage());
            return currentEntities;
        }
    }

    /**
     * 读取长期记忆（user_profile），构建背景上下文字符串
     */
    private String buildLongTermContext(String userId) {
        try {
            UserProfile profile = userProfileService.getByUserId(userId);
            if (profile == null) return "";
            StringBuilder sb = new StringBuilder("用户历史偏好：");
            if (profile.getPreferredCategories() != null && !profile.getPreferredCategories().isEmpty()) {
                sb.append("偏好类目=").append(profile.getPreferredCategories()).append("；");
            }
            if (profile.getPreferredBrands() != null && !profile.getPreferredBrands().isEmpty()) {
                sb.append("偏好品牌=").append(profile.getPreferredBrands()).append("；");
            }
            if (profile.getPriceRangeMin() != null && profile.getPriceRangeMax() != null) {
                sb.append("价格区间=¥").append(profile.getPriceRangeMin())
                  .append("-¥").append(profile.getPriceRangeMax()).append("；");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.buildLongTermContext 读取长期记忆失败 userId={}: {}", userId, e.getMessage());
            return ""; // 降级：不使用长期记忆
        }
    }

    // 内部类：意图识别结果
    private record IntentResult(String intent, Map<String, Object> entities) {
    }
}
