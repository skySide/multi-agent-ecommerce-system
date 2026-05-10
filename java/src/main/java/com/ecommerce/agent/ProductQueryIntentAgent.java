package com.ecommerce.agent;

import com.ecommerce.entity.Product;
import com.ecommerce.model.AgentResult;
import com.ecommerce.service.MemoryService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.QueryRewriteService;
import com.ecommerce.service.RecommendEngineService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 商品查询意图Agent
 * 结合向量召回 + 数据库搜索，查询用户询问的具体商品信息
 */
@Component
public class ProductQueryIntentAgent extends BaseAgent {

    @Resource
    private ProductService productService;

    @Resource
    private RecommendEngineService recommendEngineService;

    @Resource
    private QueryRewriteService queryRewriteService;

    @Resource
    private MemoryService memoryService;

    public ProductQueryIntentAgent() {
        super("product_query_intent", 8.0, 2);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 提取参数
        String message = (String) params.get("message");
        List<String> history = (List<String>) params.get("history");
        String summary = (String) params.get("summary");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");

        // 步骤2: 从实体中提取商品名称，没有则使用原始消息
        String productName = entities.get("product_name") instanceof String
                ? (String) entities.get("product_name") : message;

        log.info("ProductQueryIntentAgent.execute - 商品查询, productName: {}", productName);

        // 步骤3: Query改写 + 向量召回
        String rewrittenQuery = rewriteQuery(productName, history, summary);
        List<Product> vectorProducts = recommendEngineService.vectorRecall(rewrittenQuery, null, 3);

        // 步骤4: 数据库关键词搜索
        List<Product> dbProducts = productService.searchByKeyword(productName, 3);

        // 步骤5: 合并去重
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

        // 步骤6: 生成回复
        String historyContext = memoryService.buildHistoryContext(history, summary);
        String reply = generateProductQueryReply(mergedProducts, historyContext, message);

        // 步骤7: 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("reply", reply);
        data.put("products", mergedProducts);

        log.info("ProductQueryIntentAgent.execute - 查询完成, 找到{}条商品", mergedProducts.size());
        return AgentResult.builder().agentName(name).success(true).data(data).confidence(0.9).build();
    }

    private String rewriteQuery(String query, List<String> history, String summary) {
        try {
            String rewritten = queryRewriteService.rewriteWithContext(query, history, summary);
            if (org.apache.commons.lang3.StringUtils.isNotBlank(rewritten)) {
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("ProductQueryIntentAgent.rewriteQuery 改写失败", e);
        }
        return query;
    }

    private String generateProductQueryReply(List<Product> products, String historyContext, String userQuery) {
        // 步骤1: 处理空列表
        if (CollectionUtils.isEmpty(products)) {
            return "抱歉，没有找到相关商品。";
        }

        // 步骤2: 取第一个商品展示详情
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
}
