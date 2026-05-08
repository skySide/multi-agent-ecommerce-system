package com.ecommerce.service.impl;

import com.ecommerce.dto.RewriteResultDTO;
import com.ecommerce.entity.ConversationSession;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.model.ConversationRequest;
import com.ecommerce.model.ConversationResponse;
import com.ecommerce.model.response.IntentRecognitionResult;
import com.ecommerce.model.response.ProductNamesResult;
import com.ecommerce.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话服务实现
 * 多轮对话 + 意图识别 + RAG问答 + 对话式推荐
 * 
 * 改进点：
 * 1. compare 场景真正使用 LLM 进行对比分析
 * 2. knowledge_query 使用 Query 改写降低幻觉
 * 3. product_query 结合向量召回
 * 4. 返回 entity.Product 包含完整字段
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
    @Resource
    private QueryRewriteService queryRewriteService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最大历史轮数（滑动窗口） */
    private static final int MAX_HISTORY_ROUNDS = 10;
    
    /** 触发摘要的轮数阈值 */
    private static final int SUMMARY_THRESHOLD = 5;

    @Override
    public ConversationResponse chat(ConversationRequest request) {
        String userId = request.getUserId();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        log.info("ConversationServiceImpl.chat 用户={} 会话={} 消息={}", userId, sessionId, message);

        // 1. 获取或创建会话
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = createSession(userId);
        }
        ConversationSession session = conversationSessionService.getBySessionId(sessionId);
        if (session == null) {
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        } else if (!userId.equals(session.getUserId())) {
            log.warn("ConversationServiceImpl.chat 会话归属校验失败: sessionId={} 属于 userId={}, 请求 userId={}",
                    sessionId, session.getUserId(), userId);
            sessionId = createSession(userId);
            session = conversationSessionService.getBySessionId(sessionId);
        }

        // 2. 解析对话历史 + 摘要（短期记忆）
        List<String> history = parseHistory(session.getDialogueHistory());
        String summary = session.getSummary();

        // 3. 意图识别 + 实体抽取
        IntentRecognitionResult intentResult = recognizeIntent(message, history, summary);
        String intent = intentResult.getIntent();
        Map<String, Object> entities = intentResult.getEntities();

        log.info("ConversationService.chat 意图识别结果: intent={} entities={}", intent, entities);

        // 4. 根据意图处理
        ConversationResponse response;
        switch (intent) {
            case "recommend":
                response = handleRecommend(userId, sessionId, message, history, summary, entities);
                break;
            case "product_query":
                response = handleProductQuery(userId, sessionId, message, history, summary, entities);
                break;
            case "knowledge_query":
                response = handleKnowledgeQuery(userId, sessionId, message, history, summary);
                break;
            case "compare":
                response = handleCompare(userId, sessionId, message, history, summary, entities);
                break;
            case "chitchat":
            default:
                response = handleChitchat(userId, sessionId, message, history, summary);
                break;
        }

        // 5. 保存对话历史
        history.add("用户: " + message);
        history.add("助手: " + response.getMessage());
        
        // 检查是否需要触发摘要（超过阈值时由定时任务处理，这里只保存历史）
        if (history.size() > MAX_HISTORY_ROUNDS * 2) {
            history = history.subList(history.size() - MAX_HISTORY_ROUNDS * 2, history.size());
        }
        saveHistory(session, history, entities);

        response.setSessionId(sessionId);
        response.setDialogueHistory(history);
        response.setIntent(intent);
        response.setExtractedInfo(entities);
        response.setSummary(summary);

        // 6. 记录用户行为
        try {
            userBehaviorService.recordBehavior(userId, null, "chat", message, "conversation");
        } catch (Exception e) {
            log.error("ConversationService.chat 记录行为失败", e);
        }

        log.info("ConversationService.chat 响应完成 session={} intent={}", sessionId, intent);
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
                                                  List<String> history, String summary, Map<String, Object> entities) {
        // 读取会话记忆，合并跨轮次累积的实体
        Map<String, Object> mergedEntities = mergeWithSessionMemory(sessionId, entities);

        // 构建上下文（包含用户查询、历史、摘要）
        Map<String, Object> context = new HashMap<>(mergedEntities);
        context.put("userQuery", message);
        context.put("history", history);
        context.put("summary", summary);

        // 构建用户画像
        UserProfile profile = buildProfileFromEntities(userId, mergedEntities);

        // 注入长期记忆
        String longTermContext = buildLongTermContext(userId);

        // 调用推荐引擎
        int numItems = mergedEntities.get("num_items") instanceof Number
                ? ((Number) mergedEntities.get("num_items")).intValue() : 6;
        List<Product> recProducts = recommendEngineService.recommend(
                userId, profile, numItems, context);

        // 直接返回 entity.Product（不再转换）
        return ConversationResponse.builder()
                .message(generateRecommendReply(recProducts))
                .recommendedProducts(recProducts)
                .build();
    }

    /**
     * 商品查询 - 结合向量召回 + 数据库查询
     */
    private ConversationResponse handleProductQuery(String userId, String sessionId, String message,
                                                     List<String> history, String summary, Map<String, Object> entities) {
        String productName = entities.get("product_name") instanceof String
                ? (String) entities.get("product_name") : message;

        // 1. 先进行 Query 改写
        String rewrittenQuery = rewriteQuery(productName, history, summary);
        log.info("ConversationServiceImpl.handleProductQuery 原始Query={} 改写后={}", productName, rewrittenQuery);

        // 2. 向量召回（通过 RecommendEngineService）
        List<Product> vectorProducts = recommendEngineService.vectorRecall(rewrittenQuery, null, 3);

        // 3. 数据库关键词搜索
        List<Product> dbProducts = productService.searchByKeyword(productName, 3);

        // 4. 合并去重
        Set<String> seenIds = new HashSet<>();
        List<Product> mergedProducts = new ArrayList<>();

        for (Product p : vectorProducts) {
            if (p != null && p.getProductId() != null && !seenIds.contains(p.getProductId())) {
                seenIds.add(p.getProductId());
                mergedProducts.add(p);
            }
        }
        for (Product p : dbProducts) {
            if (p != null && p.getProductId() != null && !seenIds.contains(p.getProductId())) {
                seenIds.add(p.getProductId());
                mergedProducts.add(p);
            }
        }

        if (mergedProducts.isEmpty()) {
            return ConversationResponse.builder()
                    .message("抱歉，我没有找到相关商品。您可以换个关键词试试，或者告诉我您的需求，我帮您推荐。")
                    .build();
        }

        // 生成回复
        String historyContext = buildHistoryContext(history, summary);
        String reply = generateProductQueryReply(mergedProducts, historyContext, message);

        return ConversationResponse.builder()
                .message(reply)
                .recommendedProducts(mergedProducts)
                .build();
    }

    /**
     * 知识问答 - 使用 Query 改写降低幻觉
     */
    private ConversationResponse handleKnowledgeQuery(String userId, String sessionId, String message,
                                                       List<String> history, String summary) {
        // 1. Query 改写（结合历史和摘要）
        String rewrittenQuery = rewriteQuery(message, history, summary);
        log.info("ConversationServiceImpl.handleKnowledgeQuery 原始Query={} 改写后={}", message, rewrittenQuery);

        // 2. RAG 向量搜索知识库
        List<Document> docs = documentVectorService.searchKnowledgeBase(rewrittenQuery, 3);

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 3. 构建提示词（包含历史、摘要、知识库内容）
        String historyContext = buildHistoryContext(history, summary);
        
        String prompt = String.format(
                "你是电商客服助手。请根据以下信息回答用户问题。\n\n" +
                        "%s知识库内容：\n%s\n\n用户问题：%s\n\n" +
                        "要求：\n" +
                        "1. 如果知识库中有相关信息，请基于知识库回答\n" +
                        "2. 如果知识库中没有相关信息，请明确告知用户，不要编造\n" +
                        "3. 回答要简洁友好，使用中文",
                historyContext,
                context.isEmpty() ? "（知识库中暂无相关信息）" : context,
                message
        );

        String reply = chatClient.prompt().user(prompt).call().content();

        return ConversationResponse.builder()
                .message(reply.trim())
                .build();
    }

    /**
     * 商品对比 - 使用 LLM 真正进行对比分析
     */
    private ConversationResponse handleCompare(String userId, String sessionId, String message,
                                                List<String> history, String summary, Map<String, Object> entities) {
        @SuppressWarnings("unchecked")
        List<String> productNames = entities.get("product_names") instanceof List
                ? (List<String>) entities.get("product_names") : List.of();

        if (productNames.size() < 2) {
            // 尝试从消息中提取商品名
            productNames = extractProductNamesFromMessage(message);
        }

        if (productNames.size() < 2) {
            return ConversationResponse.builder()
                    .message("请告诉我您想对比哪几款商品，比如\"iPhone 16 和 华为 Mate 70 哪个好？\"")
                    .build();
        }

        // 搜索商品详情
        List<Product> products = new ArrayList<>();
        for (String name : productNames.subList(0, Math.min(3, productNames.size()))) {
            List<Product> found = productService.searchByKeyword(name, 1);
            if (!found.isEmpty()) {
                products.add(found.get(0));
            }
        }

        if (products.size() < 2) {
            return ConversationResponse.builder()
                    .message("抱歉，我只找到了部分商品信息，请确认商品名称是否正确。")
                    .build();
        }

        // 使用 LLM 进行真正的对比分析
        String comparison = generateLLMComparison(message, products, history, summary);

        return ConversationResponse.builder()
                .message(comparison)
                .recommendedProducts(products)
                .build();
    }

    /**
     * 使用 LLM 生成真正的商品对比分析
     */
    private String generateLLMComparison(String userQuery, List<Product> products, 
                                          List<String> history, String summary) {
        StringBuilder productInfo = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            productInfo.append(String.format(
                    "\n【商品%d】%s\n" +
                            "- 品牌：%s\n" +
                            "- 价格：¥%.0f\n" +
                            "- 评分：%.1f/5\n" +
                            "- 销量：%d\n" +
                            "- 描述：%s\n",
                    i + 1,
                    p.getProductName(),
                    p.getBrand(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                    p.getRating() != null ? p.getRating().doubleValue() : 0,
                    p.getSalesCount() != null ? p.getSalesCount() : 0,
                    p.getProductDescription() != null ? p.getProductDescription() : "暂无"
            ));
        }

        String historyContext = buildHistoryContext(history, summary);

        String prompt = String.format(
                "你是专业的电商导购助手。用户想对比商品，请根据商品信息给出专业的对比分析。\n\n" +
                        "%s商品信息：%s\n\n用户问题：%s\n\n" +
                        "请从以下维度进行对比：\n" +
                        "1. 价格性价比\n" +
                        "2. 品牌/口碑\n" +
                        "3. 适用人群\n" +
                        "4. 综合推荐\n\n" +
                        "要求：\n" +
                        "- 客观分析，不要偏袒任何一方\n" +
                        "- 给出明确的购买建议\n" +
                        "- 回答简洁友好，300字以内",
                historyContext, productInfo.toString(), userQuery
        );

        return chatClient.prompt().user(prompt).call().content().trim();
    }

    private ConversationResponse handleChitchat(String userId, String sessionId, String message,
                                                 List<String> history, String summary) {
        String historyContext = buildHistoryContext(history, summary);

        String prompt = String.format(
                "你是淘宝智能购物助手，友好、专业、幽默。和用户进行自然对话。\n\n" +
                        "%s用户：%s\n\n助手：",
                historyContext, message
        );

        String reply = chatClient.prompt().user(prompt).call().content();

        return ConversationResponse.builder()
                .message(reply.trim())
                .build();
    }

    // ========== 意图识别 ==========

    private IntentRecognitionResult recognizeIntent(String message, List<String> history, String summary) {
        String historyContext = buildHistoryContext(history, summary);

        String prompt = String.format(
                "分析用户意图。\n" +
                        "意图说明：\n" +
                        "- recommend: 用户想要推荐商品（如\"推荐手机\"\"适合学生的笔记本\"）\n" +
                        "- product_query: 用户询问某款商品（如\"iPhone 16 多少钱\"\"华为 Mate 70 怎么样\"）\n" +
                        "- knowledge_query: 用户问售后/物流/优惠活动等知识性问题\n" +
                        "- compare: 用户对比商品（如\"iPhone 和 华为哪个好\"\"对比这两款笔记本\"）\n" +
                        "- chitchat: 闲聊\n" +
                        "实体可包含：category(类目), brand(品牌), price_min, price_max, product_name(商品名), product_names(商品名数组), num_items\n" +
                        "%s用户消息：%s",
                historyContext, message
        );

        try {
            IntentRecognitionResult result = chatClient.prompt().user(prompt).call().entity(IntentRecognitionResult.class);
            if (result != null && result.getIntent() != null) {
                return result;
            }
            return new IntentRecognitionResult("chitchat", new HashMap<>());
        } catch (Exception e) {
            log.error("ConversationService.recognizeIntent 意图识别失败", e);
            return new IntentRecognitionResult("chitchat", new HashMap<>());
        }
    }

    // ========== Query 改写 ==========

    /**
     * Query 改写 - 结合历史和摘要
     */
    private String rewriteQuery(String query, List<String> history, String summary) {
        try {
            String rewritten = queryRewriteService.rewriteWithContext(query, history, summary);
            if (StringUtils.isNotBlank(rewritten)) {
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.rewriteQuery 改写失败，使用原始query: {}", e.getMessage());
        }
        return query;
    }

    // ========== 回复生成 ==========

    private String generateRecommendReply(List<Product> products) {
        if (products.isEmpty()) {
            return "抱歉，暂时没有合适的商品推荐给您。您可以告诉我更具体的需求，比如预算、品牌偏好等。";
        }

        StringBuilder sb = new StringBuilder("根据您的需求，我为您精选了以下商品：\n\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append(String.format("%d. %s（%s）¥%.0f - %s\n",
                    i + 1, p.getProductName(), p.getBrand(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                    p.getCategoryName()));
        }
        sb.append("\n如果您想了解某款商品的详细信息，直接问我就可以啦！");
        return sb.toString();
    }

    private String generateProductQueryReply(List<Product> products, String historyContext, String userQuery) {
        if (products.isEmpty()) {
            return "抱歉，没有找到相关商品。";
        }

        Product p = products.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("为您找到 %s：\n", p.getProductName()));
        sb.append(String.format("- 价格：¥%.0f\n", p.getPrice() != null ? p.getPrice().doubleValue() : 0));
        sb.append(String.format("- 品牌：%s\n", p.getBrand()));
        sb.append(String.format("- 类目：%s\n", p.getCategoryName()));
        sb.append(String.format("- 评分：%.1f/5\n", p.getRating() != null ? p.getRating().doubleValue() : 0));
        if (p.getProductDescription() != null) {
            sb.append(String.format("- 描述：%s\n", p.getProductDescription()));
        }
        return sb.toString();
    }

    // ========== 工具方法 ==========

    private UserProfile buildProfileFromEntities(String userId, Map<String, Object> entities) {
        UserProfile profile = UserProfile.builder().userId(userId).build();

        if (entities.get("category") instanceof String) {
            profile.setPreferredCategories((String) entities.get("category"));
        } else if (entities.get("category") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> cats = (List<String>) entities.get("category");
            profile.setPreferredCategories(String.join(",", cats));
        }

        if (entities.get("brand") instanceof String) {
            profile.setPreferredBrands((String) entities.get("brand"));
        }

        BigDecimal priceMin = BigDecimal.ZERO;
        BigDecimal priceMax = BigDecimal.valueOf(100000);
        if (entities.get("price_min") instanceof Number) {
            priceMin = BigDecimal.valueOf(((Number) entities.get("price_min")).doubleValue());
        }
        if (entities.get("price_max") instanceof Number) {
            priceMax = BigDecimal.valueOf(((Number) entities.get("price_max")).doubleValue());
        }
        profile.setPriceRangeMin(priceMin);
        profile.setPriceRangeMax(priceMax);

        return profile;
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
            Map<String, Object> merged = mergeExtractedInfo(session.getExtractedInfo(), entities);
            session.setExtractedInfo(objectMapper.writeValueAsString(merged));
            session.setUpdateTime(LocalDateTime.now());
            conversationSessionService.updateById(session);
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.saveHistory 保存历史失败: {}", e.getMessage());
        }
    }

    /**
     * 构建历史上下文（包含摘要）
     */
    private String buildHistoryContext(List<String> history, String summary) {
        StringBuilder sb = new StringBuilder();
        
        if (summary != null && !summary.isEmpty()) {
            sb.append("对话摘要：").append(summary).append("\n\n");
        }
        
        if (!history.isEmpty()) {
            List<String> recent = history.subList(Math.max(0, history.size() - 6), history.size());
            sb.append("最近对话：\n").append(String.join("\n", recent)).append("\n\n");
        }
        
        return sb.toString();
    }

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
        existing.putAll(newEntities);
        return existing;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeWithSessionMemory(String sessionId, Map<String, Object> currentEntities) {
        try {
            ConversationSession session = conversationSessionService.getBySessionId(sessionId);
            if (session == null || session.getExtractedInfo() == null) {
                return currentEntities;
            }
            Map<String, Object> sessionMemory = objectMapper.readValue(session.getExtractedInfo(), Map.class);
            Map<String, Object> merged = new HashMap<>(sessionMemory);
            merged.putAll(currentEntities);
            return merged;
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.mergeWithSessionMemory 失败: {}", e.getMessage());
            return currentEntities;
        }
    }

    private String buildLongTermContext(String userId) {
        try {
            UserProfile profile = userProfileService.getByUserId(userId);
            if (profile == null) {
                return "";
            }
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
            log.warn("ConversationServiceImpl.buildLongTermContext 失败 userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * 从消息中提取商品名称
     */
    private List<String> extractProductNamesFromMessage(String message) {
        List<String> names = new ArrayList<>();
        try {
            String prompt = String.format(
                    "从用户消息中提取商品名称。\n用户消息：%s",
                    message
            );
            ProductNamesResult result = chatClient.prompt().user(prompt).call().entity(ProductNamesResult.class);
            if (result != null && result.getProducts() != null) {
                names = result.getProducts();
            }
        } catch (Exception e) {
            log.warn("ConversationServiceImpl.extractProductNamesFromMessage 失败: {}", e.getMessage());
        }
        return names;
    }
}
