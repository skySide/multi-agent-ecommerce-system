package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.ecommerce.dto.RewriteResultDTO;
import com.ecommerce.service.QueryRewriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.lang.Strings;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Query 改写服务实现
 * <p>基于 LLM 实现智能 query 改写，支持：</p>
 * <ul>
 *     <li>同义词扩展：电脑 → 笔记本、台式机</li>
 *     <li>意图理解：推荐 vs 查询 vs 问答</li>
 *     <li>实体抽取：类目、品牌、价格区间</li>
 *     <li>Query 扩展：生成多个检索变体提升召回</li>
 * </ul>
 */
@Slf4j
@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    @Resource
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Query 改写 Prompt 模板 */
    private static final String REWRITE_PROMPT = """
            你是一个电商搜索专家。请分析用户的查询意图，并进行改写和扩展。
            
            ## 任务
            1. 理解用户意图（recommend=推荐商品, product_query=查询特定商品, knowledge_query=问答, compare=对比）
            2. 抽取关键实体（category类目, brand品牌, price_min最低价, price_max最高价, product_name商品名）
            3. 将用户输入改写为更适合检索的 query（如同义词替换、拼写纠错）
            4. 生成 2-3 个同义查询变体，用于多路召回
            
            ## 类目同义词映射（参考）
            - 电脑/手提电脑/便携电脑 → 笔记本
            - 手机/移动电话 → 手机
            - 耳机/头戴式耳机/入耳式耳机 → 耳机
            
            ## 输出格式（JSON）
            ```json
            {
              "intent": "recommend",
              "entities": {"category": "笔记本", "price_max": 6000},
              "rewritten_query": "笔记本电脑 推荐 性价比",
              "expanded_queries": ["笔记本 5000-6000元", "笔记本电脑 学生", "笔记本 游戏本"],
              "confidence": 0.9
            }
            ```
            
            ## 用户输入
            %s
            
            ## 对话上下文（如有）
            %s
            
            请只输出 JSON，不要其他解释。
            """;

    @Override
    public RewriteResultDTO rewrite(String userQuery, Map<String, Object> context) {
        log.info("QueryRewriteServiceImpl.rewrite 开始改写 query={}", userQuery);
        if (StringUtils.isBlank(userQuery)) {
            return buildFallbackResult("", context);
        }

        try {
            // 构建上下文信息,获取 对话历史 + 用户画像
            String contextStr = buildContextString(context);
            // 调用 LLM 进行改写
            String prompt = String.format(REWRITE_PROMPT, userQuery, contextStr);
            RewriteResultDTO result = chatClient.prompt().user(prompt).call().entity(RewriteResultDTO.class);
            log.info("QueryRewriteServiceImpl.rewrite 改写完成 originalUserQuery = {}, rewriteResult = {}",
                    userQuery, result);
            return result;
        } catch (Exception e) {
            log.error("QueryRewriteServiceImpl.rewrite 改写失败 query={}", userQuery, e);
            return buildFallbackResult(userQuery, context);
        }
    }

    @Override
    public String toVectorQuery(String userQuery, Map<String, Object> profile) {
        log.info("QueryRewriteServiceImpl.toVectorQuery 转换向量检索 query={}", userQuery);

        if (userQuery == null || userQuery.trim().isEmpty()) {
            return "热门商品推荐";
        }

        try {
            // 构建 Prompt，让 LLM 生成适合向量检索的 query
            String prompt = String.format("""
                    你是一个电商搜索专家。请将用户输入转换为适合向量检索的 query。
                    
                    要求：
                    1. 保留核心意图关键词
                    2. 使用标准类目名称（如：笔记本、手机、耳机、平板）
                    3. 移除无关词汇（如：帮我、我想、有没有）
                    4. 如果用户提到具体商品，保留商品特征
                    
                    用户画像偏好：%s
                    
                    用户输入：%s
                    
                    只输出改写后的 query，不要解释。
                    """,
                    profile != null ? profile.toString() : "无",
                    userQuery
            );

            String rewritten = chatClient.prompt().user(prompt).call().content().trim();
            log.info("QueryRewriteServiceImpl.toVectorQuery 改写结果: {} → {}", userQuery, rewritten);
            return rewritten;

        } catch (Exception e) {
            log.error("QueryRewriteServiceImpl.toVectorQuery 改写失败", e);
            // 降级：直接返回原始 query
            return userQuery;
        }
    }

    @Override
    public List<String> expandVariants(String userQuery, int numVariants) {
        log.info("QueryRewriteServiceImpl.expandVariants 扩展查询变体 query={} num={}", userQuery, numVariants);

        if (userQuery == null || userQuery.trim().isEmpty()) {
            return List.of("热门商品");
        }

        try {
            String prompt = String.format("""
                    你是一个电商搜索专家。请为用户查询生成 %d 个同义变体，用于多路召回。
                    
                    要求：
                    1. 保持核心意图不变
                    2. 使用同义词替换（如：电脑→笔记本）
                    3. 添加相关修饰词（如：性价比、学生、办公）
                    4. 每个变体一行
                    
                    用户查询：%s
                    
                    只输出变体列表，每行一个，不要编号和解释。
                    """,
                    numVariants,
                    userQuery
            );

            String response = chatClient.prompt().user(prompt).call().content();
            List<String> variants = Arrays.asList(response.trim().split("\n"));

            // 清理和限制数量
            List<String> cleaned = variants.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(numVariants)
                    .toList();

            log.info("QueryRewriteServiceImpl.expandVariants 扩展结果: {}", cleaned);
            return cleaned.isEmpty() ? List.of(userQuery) : cleaned;

        } catch (Exception e) {
            log.error("QueryRewriteServiceImpl.expandVariants 扩展失败", e);
            return List.of(userQuery);
        }
    }

    /**
     * 构建上下文字符串
     */
    private String buildContextString(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Strings.EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        if (Objects.nonNull(context.get("history"))) {
            sb.append("对话历史: ").append(context.get("history")).append("\n");
        }
        if (Objects.nonNull(context.get("profile"))) {
            sb.append("用户画像: ").append(context.get("profile")).append("\n");
        }
        return sb.length() > 0 ? sb.toString() : Strings.EMPTY;
    }

    /**
     * 解析 LLM 返回的改写结果
     */
    @SuppressWarnings("unchecked")
    private RewriteResultDTO parseRewriteResult(String originalQuery, String response) {
        try {
            // 清理 markdown 代码块
            String cleaned = response.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }

            // 解析 JSON
            Map<String, Object> result = objectMapper.readValue(cleaned, Map.class);

            String intent = (String) result.getOrDefault("intent", "recommend");
            Map<String, Object> entities = (Map<String, Object>) result.getOrDefault("entities", new HashMap<>());
            String rewritten = (String) result.getOrDefault("rewritten_query", originalQuery);
            List<String> expanded = (List<String>) result.getOrDefault("expanded_queries", List.of());
            Double confidence = result.get("confidence") instanceof Number
                    ? ((Number) result.get("confidence")).doubleValue()
                    : 0.8;

            return RewriteResultDTO.builder()
                    .originalQuery(originalQuery)
                    .rewrittenQuery(rewritten)
                    .expandedQueries(expanded)
                    .intent(intent)
                    .entities(entities)
                    .confidence(confidence)
                    .build();

        } catch (Exception e) {
            log.warn("QueryRewriteServiceImpl.parseRewriteResult 解析失败: {}", e.getMessage());
            return buildFallbackResult(originalQuery, null);
        }
    }

    /**
     * 构建降级结果
     */
    private RewriteResultDTO buildFallbackResult(String originalQuery, Map<String, Object> context) {
        return RewriteResultDTO.builder()
                .originalQuery(originalQuery)
                .rewrittenQuery(originalQuery)
                .expandedQueries(List.of(originalQuery))
                .intent("recommend")
                .entities(new HashMap<>())
                .confidence(0.5)
                .build();
    }
}
