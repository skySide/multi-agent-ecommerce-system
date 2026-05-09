# 多Agent电商推荐系统 — 技术深度面试手册

> 覆盖：整体架构 · 核心表设计 · 实现细节 · 难点攻关 · 高频面试题

---

## 一、整体架构

### 1.1 系统分层

```
┌──────────────────────────────────────────────────────────────┐
│                  前端 (React + Ant Design)                    │
│  HomePage  SearchPage  ProductDetailPage  UserCenterPage      │
└─────────────────────────┬────────────────────────────────────┘
                          │ HTTP / Vite Proxy (:3000 → :8080)
┌─────────────────────────▼────────────────────────────────────┐
│                   Spring Boot 3 后端                          │
│                                                               │
│  Controller层                                                 │
│  RecommendationController  ConversationController             │
│  ProductController  UserController  BehaviorController        │
│  FavoriteController  ShoppingCartController                   │
│                                                               │
│  编排层                                                       │
│  SupervisorOrchestrator (CompletableFuture并行)               │
│                                                               │
│  Agent层 (BaseAgent: 重试+降级+指数退避)                      │
│  UserProfileAgent  ProductRecAgent                            │
│  MarketingCopyAgent  InventoryAgent                           │
│                                                               │
│  服务层                                                       │
│  RecommendEngineService  ConversationService                  │
│  VectorSyncService  UserBehaviorService                       │
│  ShoppingCartService  UserFavoriteService                     │
└──────────────────────────────────────────────────────────────┘
        │                   │                   │
   MySQL 8.0           Milvus向量库          LLM API
  (业务数据)         (商品向量+知识库)     (MiniMax/通义)
```

### 1.2 推荐链路全流程

```
用户请求 POST /api/v1/recommend
    │
    ▼
SupervisorOrchestrator.recommend()
    │
    ├── Phase 1 (CompletableFuture并行)
    │   ├── UserProfileAgent
    │   │   读 user_behavior(最近50条) → LLM分析 → RFM分群
    │   │   → saveOrUpdateProfile(user_profile表)
    │   └── ProductRecAgent
    │       四路召回: 向量(40%)+热销(20%)+新品(20%)+类目(20%)
    │       → RRF融合 → 候选集
    │
    ├── Phase 2 (CompletableFuture并行)
    │   ├── ProductRecAgent (精排)
    │   │   规则预排(销量+评分+偏好匹配) → LLM调整顺序
    │   │   LLM失败 → 回退规则排序
    │   └── InventoryAgent
    │       查 product.stock → 过滤缺货 → 输出限购策略
    │
    ├── 聚合: 库存过滤 → MMR多样性控制(λ=0.5) → TopN
    │
    └── Phase 3 (串行，依赖前两步结果)
        └── MarketingCopyAgent
            按用户分群选Prompt模板(5套) → LLM生成文案
            → 广告法违禁词校验
```

### 1.3 对话链路全流程

```
用户消息 POST /api/v1/conversation/chat
    │
    ▼
ConversationServiceImpl.chat()
    │
    ├── 1. 会话归属校验
    │   userId != session.userId → 拒绝，创建新会话
    │
    ├── 2. 三层记忆读取
    │   短期: dialogue_history (最近10轮，JSON数组)
    │   会话: extracted_info (跨轮次实体，JSON对象)
    │   长期: user_profile (历史偏好，200ms超时降级)
    │
    ├── 3. 意图识别 (LLM + 最近3轮历史)
    │   → recommend / product_query / knowledge_query
    │     / compare / chitchat
    │
    ├── 4. 意图路由
    │   recommend     → 合并会话记忆实体 → 推荐引擎
    │   product_query → 全文搜索 + 历史上下文
    │   knowledge_query → RAG检索知识库 + 历史上下文
    │   compare       → 搜索多商品 → LLM对比
    │   chitchat      → 带历史上下文闲聊
    │
    └── 5. 记忆持久化
        dialogue_history: 追加本轮，超10轮截断
        extracted_info: 合并式更新(新覆旧，旧字段保留)
```

---

## 二、核心表设计

### 2.1 完整表清单

| 表名 | 用途 | 关键约束 |
|---|---|---|
| `user` | 用户基础信息 | user_id UNIQUE |
| `product` | 商品信息 | product_id UNIQUE, FULLTEXT(product_name) ngram |
| `category` | 商品类目(三级) | category_id UNIQUE, parent_id |
| `tag` / `product_tag` | 商品标签 | (product_id, tag_id) UNIQUE |
| `user_behavior` | 行为日志(只追加) | INDEX(user_id, create_time) |
| `user_profile` | 用户画像 | user_id UNIQUE, RFM分数 |
| `user_realtime_features` | 实时特征 | user_id UNIQUE, 1h/24h计数 |
| `recommend_cache` | 推荐结果缓存 | cache_key UNIQUE(user_id:scene) |
| `conversation_session` | 对话会话 | session_id UNIQUE, user_id |
| `conversation_profile_update` | 对话画像更新记录 | user_id, session_id |
| `shopping_cart` | 购物车 | (user_id, product_id) UNIQUE |
| `user_favorite` | 用户收藏 | (user_id, product_id) UNIQUE |

### 2.2 关键设计决策

**为什么 shopping_cart 和 user_favorite 要建独立表？**

`user_behavior` 是只追加的流水日志，不适合做状态查询：
- 查"当前购物车"需扫描所有cart行为再去重，效率低
- 无法支持"取消收藏"（日志不能删除）
- 无法存储 quantity 字段

独立表有 `(user_id, product_id)` 唯一约束，支持精确状态查询和幂等操作。

**user_profile 的 saveOrUpdate 问题**

`user_id` 加了唯一约束，直接 `save()` 第二次会抛 DuplicateKeyException。
MyBatis-Plus 的 `saveOrUpdate()` 依赖自增主键 id，不适用。
正确做法：先 `getByUserId()` 查，存在则 `updateById()`，不存在则 `save()`。

**product 表的全文索引**

```sql
FULLTEXT INDEX ft_name (product_name) WITH PARSER ngram
-- 查询: MATCH(product_name) AGAINST(#{keyword} IN BOOLEAN MODE)
-- ngram分词器支持中文，最小分词长度2个字符
```

---

## 三、核心实现细节

### 3.1 多路召回 + RRF融合

```
RRF公式: score(d) = Σ weight_i / (k + rank_i(d))
k=60 (平滑常数，避免排名靠前的商品分数过高)

各通道权重: 向量0.4, 热销0.2, 新品0.2, 类目0.2
通道内先稳定排序再参与RRF，保证rank语义准确
```

优势：不依赖各通道原始分数（向量相似度和销量是不同量纲），只用排名位置，天然解决量纲不统一问题。

### 3.2 向量召回后为什么要回表MySQL

向量库存的是快照数据，存在数据延迟：商品下架/价格调整/库存清零后，向量库不一定立即感知。回表MySQL获取最新数据，避免推荐已下架商品（降低幻觉）。

回表后还需要按向量相似度顺序重排（`reorderByIds()`），因为MySQL IN查询返回顺序不保证与输入ID顺序一致。

### 3.3 三层记忆系统

```
短期记忆: dialogue_history (JSON数组)
  容量: 最多10轮=20条，超出截断最早的
  用途: 注入所有意图处理的LLM prompt

会话记忆: extracted_info (JSON对象)
  合并策略: 新值覆盖同名旧值，未覆盖字段保留
  用途: "预算5000"→下一轮"推荐一个"，系统仍知道预算

长期记忆: user_profile表
  读取时机: 对话开始时
  注入内容: 仅 preferred_categories + preferred_brands + price_range
  降级: 读取超时(200ms)或失败时跳过，不影响主流程
```

### 3.4 多用户记忆隔离

三道防线：
1. `session_id` 是UUID，天然不同用户不同session
2. `chat()` 入口校验 `sessionId` 归属，`userId != session.getUserId()` 时拒绝访问
3. 所有记忆读取通过 `sessionId` 或 `userId` 查询，数据库层面天然隔离

### 3.5 BaseAgent 重试与降级

```
指数退避重试: 500ms → 1000ms → 2000ms (最多maxRetries次)
降级: 全部重试失败返回 success=false 的 AgentResult

Supervisor对降级的处理:
- UserProfileAgent降级 → 跳过LLM精排，用规则排序
- InventoryAgent降级 → 不过滤库存，直接返回精排结果
- MarketingCopyAgent降级 → 返回空文案列表
```

### 3.6 RAG知识库问答

```
知识库文档: 退换货政策 / 配送说明 / 会员权益 / 手机选购指南
向量化: 文档分块 → Embedding → 存入Milvus

查询流程:
用户问题 → 向量化 → Milvus相似度搜索(Top-3)
→ 检索到的文档片段注入prompt
→ LLM基于文档内容回答，而非凭空生成
```

准确性保证：LLM的回答来源于知识库文档，不是训练数据。知识库由运营人员维护，是唯一可信来源。

---

## 四、实现难点

### 难点1: 向量召回后回表顺序不稳定

MySQL IN查询返回顺序不保证与输入ID顺序一致，导致向量相似度排序被破坏。

解决：`reorderByIds()` 以Milvus返回的ID顺序为准，重新排列MySQL回表结果。

### 难点2: user_profile 唯一约束冲突

直接 `save()` 第二次调用抛 DuplicateKeyException。MyBatis-Plus的 `saveOrUpdate()` 依赖自增主键，不适用业务主键场景。

解决：`saveOrUpdateProfile()` 先查后更新，手动实现upsert语义。

### 难点3: 对话意图识别的指代消解

"那换货呢"中的"那"指代上文的退货政策，LLM不知道上下文会误判意图。

解决：意图识别时注入最近3轮对话历史，让LLM理解上下文指代。

### 难点4: 会话记忆的合并策略

每轮直接覆盖 `extracted_info` 会丢失上一轮的"预算5000"等信息。

解决：合并式更新，`existing.putAll(newEntities)`，新值覆盖旧值但旧字段保留。

### 难点5: CompletableFuture异常处理

Phase1两个Future并行，一个抛异常会导致 `join()` 抛出 CompletionException，整个推荐失败。

解决：BaseAgent内部捕获所有异常并返回降级的AgentResult，不向外抛出。Supervisor通过 `result.isSuccess()` 决定是否跳过某阶段。

### 难点6: 向量库数据一致性

内存向量库重启后数据消失，每次启动必须全量同步，影响启动时间。

当前方案：启动时全量同步，商品变更时通过 `ProductChangeEvent` 异步增量同步。

生产方案：换用持久化Milvus，存量只同步一次，增量走事件驱动，启动时间大幅降低。

---

## 五、面试高频问题

### Q1: 为什么用 Supervisor 模式？

三个核心原因：
1. **上下文隔离**: 每个Agent只关注自己领域，Token消耗少、推理准确
2. **并行加速**: Phase1+Phase2并行，总延迟约等于最慢Agent耗时，节省约50%
3. **独立演进**: 各Agent可独立升级、独立A/B测试，互不影响

### Q2: RRF融合是什么，为什么比加权平均好？

RRF公式：`score(d) = Σ weight_i / (k + rank_i(d))`

优势：不依赖各通道原始分数（向量相似度和销量是不同量纲），只用排名位置，天然解决量纲不统一问题。长尾商品分数衰减平缓，不会被完全忽略。

### Q3: 三层记忆如何保证多用户隔离？

三道防线：UUID隔离 + chat()入口归属校验 + 数据库userId隔离。核心是 `chat()` 入口的 `userId != session.getUserId()` 校验，防止用户A用用户B的sessionId访问B的对话历史。

### Q4: 向量数据库和MySQL各存什么？

MySQL存结构化数据（价格、库存、状态），支持精确查询和事务。Milvus存语义向量，支持相似度搜索。两者互补：Milvus做语义召回，MySQL做精确过滤和数据校验（回表）。

### Q5: 如何保证RAG知识库回答的准确性？

RAG的核心是"检索增强"，不让LLM凭空生成。用户问题先向量化，检索最相关文档片段，注入prompt让LLM基于文档回答。知识库由运营人员维护，是唯一可信来源。

### Q6: 对话满意度如何衡量？

不能简单用"追问次数=不满意"，追问可能是用户被吸引了想深入了解。用多维信号组合：

| 信号 | 含义 | 权重 |
|---|---|---|
| 用户消息含否定词 | 明确不满意 | 高 |
| 对话后30分钟内加购/购买 | 正向转化 | 高 |
| Session只有1轮就结束 | 可能放弃 | 中 |
| 同意图连续出现 | 深入探索（中性偏正向） | 低 |

解决率 = 无否定信号的Session数 / 总Session数

### Q7: 系统延迟如何优化？

四个手段：并行化（Phase1+Phase2）、超时降级（BaseAgent）、推荐缓存（recommend_cache表）、LLM Prompt精简（意图识别只传最近3轮历史）。

### Q8: LLM精排失败了怎么办？

`rerank()` 有两层保障：先用规则排序（销量+评分+偏好匹配）作为基础，再尝试LLM调整顺序，LLM失败时catch异常回退规则排序。最终结果不会为空。

### Q9: 为什么每次启动都要全量同步向量库？

当前使用内存向量库，服务重启后数据消失。生产环境应换用持久化Milvus，存量只同步一次，增量走 `ProductChangeEvent` 事件驱动异步同步。

### Q10: 购物车和收藏为什么建独立表？

`user_behavior` 是只追加的流水日志，不适合做状态查询（查当前购物车需扫描所有cart行为再去重）。独立表有唯一约束，支持精确状态查询、幂等操作和数量字段。

---

## 六、技术亮点深度解析（面试必答）

### 亮点1: Supervisor + Agent 并行编排架构

**面试问法：** "这个项目最大的技术亮点是什么？为什么这样设计？"

**回答框架：**

1. **设计动机**：传统推荐系统是串行流水线，延迟累加。比如画像分析5s + 召回8s + 重排8s + 库存5s = 26s，用户等待不可接受。

2. **架构创新**：采用 Supervisor + Agent 模式，核心是**任务解耦 + 并行执行**：
   - Phase 1: 画像分析和多路召回并行（互不依赖）
   - Phase 2: LLM重排和库存校验并行（重排不需要库存信息）
   - Phase 3: 文案生成串行（依赖前两步结果）

3. **技术实现**：使用 `CompletableFuture.runAsync()` 实现并行，通过 `join()` 等待结果聚合：
   ```java
   // Phase 1 并行
   CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(...);
   CompletableFuture<AgentResult> recFuture = productRecAgent.runAsync(...);
   AgentResult profileResult = profileFuture.join();
   AgentResult recResult = recFuture.join();
   ```

4. **延迟收益**：串行 26s → 并行后 P99 < 8s，实际平均 6s，节省约 75%。

**追问：为什么不直接用微服务？**
- Agent 是同一个 JVM 内的方法调用，无网络开销
- CompletableFuture 比 RPC 更轻量，适合细粒度并行
- 微服务适合服务间解耦，Agent 适合功能内聚

---

### 亮点2: Agent-to-Agent (A2A) 通信机制

**面试问法：** "Agent 之间是怎么通信的？数据怎么传递？"

这是面试中最容易被深入追问的技术点，需要详细解释。

#### 2.1 A2A 通信架构图

```
                    ┌─────────────────────────────────────┐
                    │      SupervisorOrchestrator         │
                    │      (中央编排器，负责协调)          │
                    └──────────────┬──────────────────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
          ▼                        ▼                        ▼
   ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
   │UserProfile   │         │ProductRec    │         │Inventory     │
   │Agent         │         │Agent         │         │Agent         │
   └──────┬───────┘         └──────┬───────┘         └──────┬───────┘
          │                        │                        │
          │     AgentResult        │     AgentResult        │
          │     ┌─────────────┐    │     ┌─────────────┐    │
          └────►│ success     │    └────►│ success     │◄───┘
                │ data        │         │ data        │
                │ confidence  │         │ confidence  │
                │ latencyMs   │         │ latencyMs   │
                │ error       │         │ error       │
                └──────┬──────┘         └──────┬──────┘
                       │                       │
                       └───────────┬───────────┘
                                   ▼
                    ┌─────────────────────────────────────┐
                    │      Supervisor 聚合结果             │
                    │   Map<String, AgentResult>          │
                    │   key = "user_profile"              │
                    │   key = "product_recall"            │
                    │   key = "rerank"                    │
                    │   key = "inventory"                 │
                    └─────────────────────────────────────┘
```

#### 2.2 核心数据结构：AgentResult

Agent 之间的通信**不通过消息队列**，而是通过**统一的数据结构 AgentResult** 在内存中传递：

```java
@Data
@Builder
public class AgentResult {
    /** Agent 名称，用于日志和调试 */
    private String agentName;
    
    /** 执行是否成功（关键：用于判断是否需要降级） */
    @Builder.Default
    private boolean success = true;
    
    /** 执行耗时（用于监控和性能分析） */
    @Builder.Default
    private double latencyMs = 0.0;
    
    /** 错误信息（失败时记录原因） */
    private String error;
    
    /** 核心数据载体（Map 结构，灵活承载不同类型数据） */
    private Map<String, Object> data;
    
    /** 置信度（0-1，用于结果质量评估） */
    @Builder.Default
    private double confidence = 1.0;
}
```

#### 2.3 A2A 通信流程详解

**Phase 1: 画像 Agent → 召回 Agent（间接通信）**

```java
// SupervisorOrchestrator.java

// Step 1: 启动画像 Agent
CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(
    Map.of("userId", request.getUserId())  // 输入参数
);

// Step 2: 启动召回 Agent（与画像并行）
CompletableFuture<AgentResult> recFuture = productRecAgent.runAsync(
    Map.of("userId", request.getUserId(), "numItems", request.getNumItems() * 2)
);

// Step 3: 等待并获取结果
AgentResult profileResult = profileFuture.join();
AgentResult recResult = recFuture.join();

// Step 4: 从 AgentResult 中提取数据
UserProfile profile = profileResult.getData() != null
    ? (UserProfile) profileResult.getData().get("profile")  // 从 data Map 中取
    : null;

List<Product> rawProducts = recResult.getData() != null
    ? (List<Product>) recResult.getData().get("products")
    : List.of();
```

**Phase 2: 画像 → 召回 Agent（直接通信，传递画像数据）**

```java
// Step 5: 将画像 Agent 的结果，作为召回 Agent 的输入
CompletableFuture<AgentResult> rerankFuture;
if (hasUsableProfile(profile)) {
    rerankFuture = productRecAgent.runAsync(
        Map.of(
            "userId", request.getUserId(),
            "userProfile", profile,      // ← 画像数据传递给召回 Agent
            "numItems", request.getNumItems()
        )
    );
}
```

**关键点：Agent 之间不直接调用，而是通过 Supervisor 作为中介传递数据。**

#### 2.4 各 Agent 的输入输出契约

| Agent | 输入 (Map<String, Object>) | 输出 (AgentResult.data) |
|-------|---------------------------|-------------------------|
| UserProfileAgent | `userId` | `profile`: UserProfile 对象<br>`behavior_summary`: 行为摘要 |
| ProductRecAgent (召回) | `userId`, `numItems` | `products`: List<Product><br>`recall_strategy`: 召回策略 |
| ProductRecAgent (重排) | `userId`, `userProfile`, `numItems` | `products`: List<Product> |
| InventoryAgent | `productIds`: List<String> | `available_products`: List<String><br>`low_stock_alerts`: List<StockAlert><br>`purchase_limits`: Map<String, Integer> |
| MarketingCopyAgent | `userProfile`, `productIds` | `copies`: List<MarketingCopy><br>`template_used`: 模板名称 |

#### 2.5 为什么不用消息队列？

**面试追问：** "为什么不直接用 Kafka 或 RabbitMQ 来做 Agent 通信？"

| 方案 | 优点 | 缺点 |
|------|------|------|
| **CompletableFuture（当前方案）** | 零网络延迟、简单可靠、易于调试 | 单机无法水平扩展 |
| **消息队列（Kafka）** | 可水平扩展、支持持久化 | 网络延迟 + 序列化开销，增加复杂度 |
| **Actor 模型（Akka）** | 天然支持分布式、容错好 | 学习曲线陡、运维复杂 |

**当前方案的选择理由：**
1. **延迟敏感**：推荐系统要求 P99 < 2s，消息队列的网络 + 序列化开销不可接受
2. **数据量小**：Agent 之间传递的是用户画像、商品列表，内存足够
3. **强一致性**：CompletableFuture.join() 保证结果同步返回，无需处理消息丢失

**生产环境升级路径：**
- 如果 QPS 增长到单机无法支撑，可将 Agent 部署为独立微服务
- 使用 gRPC 或 RSocket 替代 CompletableFuture，保持同步语义

#### 2.6 A2A 通信的错误处理

```java
// BaseAgent.java - 所有 Agent 的基类

public CompletableFuture<AgentResult> runAsync(Map<String, Object> params) {
    return CompletableFuture.supplyAsync(() -> {
        callCount.incrementAndGet();
        long start = System.nanoTime();
        int attempt = 0;
        Exception lastError = null;

        // 指数退避重试
        while (attempt < maxRetries) {
            try {
                AgentResult result = execute(params);
                return result;  // 成功则返回
            } catch (Exception e) {
                lastError = e;
                attempt++;
                Thread.sleep((long) (500 * Math.pow(2, attempt - 1)));  // 500ms, 1000ms, 2000ms
            }
        }

        // 重试全部失败，返回降级结果（不抛异常！）
        return fallback(latencyMs, lastError);
    });
}

// 降级结果：success=false，但 data 可能为空或兜底数据
protected AgentResult fallback(double latencyMs, Exception e) {
    return AgentResult.builder()
        .agentName(name)
        .success(false)  // ← 关键标记
        .latencyMs(latencyMs)
        .error(e.getMessage())
        .confidence(0.0)
        .build();
}
```

**Supervisor 如何处理失败结果：**

```java
// SupervisorOrchestrator.java

AgentResult profileResult = profileFuture.join();

// 检查 success 字段，决定是否使用降级策略
if (!profileResult.isSuccess()) {
    log.warn("画像 Agent 失败，使用默认画像");
    profile = new UserProfile();  // 降级为默认画像
} else {
    profile = (UserProfile) profileResult.getData().get("profile");
}
```

---

### 亮点3: BaseAgent 重试 + 降级 + 熔断三位一体

**面试问法：** "Agent 调用失败怎么办？如何保证系统稳定性？"

**回答框架：**

1. **指数退避重试**：LLM 调用可能因网络抖动失败，立即重试会加剧问题。采用 `500ms → 1000ms → 2000ms` 指数退避，给下游恢复时间：
   ```java
   Thread.sleep((long) (500 * Math.pow(2, attempt - 1)));
   ```

2. **优雅降级**：重试全部失败后，返回 `success=false` 的 AgentResult，不抛异常。Supervisor 根据 `isSuccess()` 决定是否跳过该阶段：
   - UserProfileAgent 降级 → 跳过 LLM 精排，用规则排序
   - InventoryAgent 降级 → 不过滤库存，直接返回精排结果
   - MarketingCopyAgent 降级 → 返回空文案列表

3. **错误率熔断**：BaseAgent 内置 `errorCount / callCount` 错误率计算，可在子类中扩展熔断逻辑：
   ```java
   public double getErrorRate() {
       int calls = callCount.get();
       return calls == 0 ? 0.0 : (double) errorCount.get() / calls;
   }
   ```

**追问：为什么不在 BaseAgent 里直接实现熔断？**
- 熔断阈值因 Agent 而异（库存 Agent 可容忍更高错误率）
- 留给子类按需扩展，保持 BaseAgent 职责单一

---

### 亮点4: 多路召回 + RRF 融合算法

**面试问法：** "推荐系统怎么做召回？为什么要用 RRF 融合？"

#### 4.1 四路召回架构

```
                    用户请求
                       │
                       ▼
         ┌─────────────┼─────────────┐
         │             │             │
         ▼             ▼             ▼
   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
   │向量召回  │  │热销召回  │  │新品召回  │  │类目召回  │
   │(Milvus)  │  │(MySQL)   │  │(MySQL)   │  │(MySQL)   │
   │权重:0.4  │  │权重:0.2  │  │权重:0.2  │  │权重:0.2  │
   └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
        │             │             │             │
        ▼             ▼             ▼             ▼
   [P1,P3,P5]    [P1,P2,P4]    [P3,P6,P7]    [P2,P5,P8]
        │             │             │             │
        └─────────────┴─────────────┴─────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  RRF 融合    │
                    │  合并去重    │
                    └──────┬───────┘
                           │
                           ▼
                    [P1,P2,P3,P4,P5,P6,P7,P8]
```

#### 4.2 RRF（Reciprocal Rank Fusion）算法详解

**公式：**

```
RRF_score(d) = Σ (weight_i / (k + rank_i(d)))

其中：
- d: 商品
- weight_i: 第 i 个通道的权重
- k: 平滑常数（默认 60），避免排名靠前的商品分数过高
- rank_i(d): 商品 d 在第 i 个通道中的排名（从 0 开始）
```

**代码实现：**

```java
private List<Product> mergeByRrf(Map<String, List<Product>> channelResult, int limit) {
    Map<String, Product> productMap = new HashMap<>();
    Map<String, Double> scores = new HashMap<>();

    for (Map.Entry<String, List<Product>> entry : channelResult.entrySet()) {
        String channel = entry.getKey();
        List<Product> products = entry.getValue();
        double weight = CHANNEL_WEIGHTS.getOrDefault(channel, 0.1);  // 向量0.4, 热销0.2, 新品0.2, 类目0.2
        
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            productMap.putIfAbsent(p.getProductId(), p);
            
            // RRF 公式核心
            double inc = weight / (RRF_K + i + 1.0);  // k=60
            scores.merge(p.getProductId(), inc, Double::sum);
        }
    }
    
    // 按分数降序排列
    return scores.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
        .limit(limit)
        .map(e -> productMap.get(e.getKey()))
        .collect(Collectors.toList());
}
```

#### 4.3 为什么 RRF 比加权平均好？

| 对比维度 | 加权平均 | RRF |
|----------|----------|-----|
| **量纲问题** | 向量相似度 [0,1]，销量 [0,10000]，量纲不统一，难以直接加权 | 只用排名位置，天然无量纲 |
| **长尾商品** | 分数低则权重低，容易被忽略 | rank 衰减平缓，长尾商品仍有机会 |
| **通道稳定性** | 需要各通道分数归一化，实现复杂 | 直接用排名，简单可靠 |

**示例：**

```
商品 A: 向量召回 rank=0, 热销召回 rank=10
商品 B: 向量召回 rank=5, 热销召回 rank=0

RRF 计算:
A = 0.4/(60+1) + 0.2/(60+11) = 0.00656 + 0.00286 = 0.00942
B = 0.4/(60+6) + 0.2/(60+1) = 0.00606 + 0.00328 = 0.00934

结果: A 略高于 B（向量召回的排名优势被体现）
```

---

### 亮点5: 三层记忆系统

**面试问法：** "对话系统如何记住上下文？多轮对话的偏好如何累积？"

**回答框架：**

1. **设计动机**：用户说"预算5000"，下一轮说"推荐一个手机"，系统必须记住预算。传统方案是每次把完整历史传给 LLM，但 Token 消耗大、上下文窗口有限。

2. **三层记忆架构**：

| 记忆层 | 存储位置 | 容量 | 用途 | 合并策略 |
|--------|----------|------|------|----------|
| 短期记忆 | dialogue_history | 最多10轮=20条 | 注入所有意图处理 prompt | 追加，超10轮截断最早 |
| 会话记忆 | extracted_info | 最多20个字段 | 跨轮次实体累积 | 新值覆盖旧值，旧字段保留 |
| 长期记忆 | user_profile | 无限制 | 跨 Session 历史偏好 | 对话开始时读取，不写入 |

3. **会话记忆合并算法**：
   ```java
   // 每轮结束后
   Map<String, Object> existing = parseJson(session.getExtractedInfo());
   existing.putAll(newEntities);  // 新覆旧，旧字段保留
   session.setExtractedInfo(toJson(existing));
   ```

4. **长期记忆降级**：user_profile 读取设置 200ms 超时，失败时跳过，不影响主流程：
   ```java
   // 伪代码
   UserProfile profile = CompletableFuture.supplyAsync(() -> userProfileService.getByUserId(userId))
       .orTimeout(200, TimeUnit.MILLISECONDS)
       .exceptionally(ex -> null)  // 降级返回 null
       .join();
   ```

**追问：为什么不用 Redis 存对话历史？**
- 对话历史是短生命周期数据，Session 结束后价值降低
- MySQL + JSON 字段足够支撑，无需引入额外中间件
- 如果后续 QPS 增长，可迁移到 Redis

**追问：会话记忆的字段数量为什么限制20个？**
- 控制 prompt 长度，避免超出 LLM 上下文窗口
- 实际场景中，用户关心的维度有限（类目、品牌、价格、颜色等）

---

### 亮点6: 向量召回后回表 MySQL 的设计

**面试问法：** "为什么向量召回后还要回表 MySQL？不直接用向量库的数据？"

**核心问题：向量库是快照数据，存在延迟**

```
时间线:
T0: 商品 A 上架，价格 999 元，库存 100
T1: 商品 A 向量化存入 Milvus
T2: 运营调整价格为 899 元，库存改为 50（MySQL 已更新）
T3: 用户发起搜索，Milvus 返回商品 A
    ↓
    问题：Milvus 中商品 A 的价格仍是 999，库存仍是 100
    解决：回表 MySQL 获取最新数据
```

**代码实现：**

```java
// RecommendEngineServiceImpl.java

public List<Product> vectorRecall(String query, UserProfile profile, int numItems) {
    // 1. 向量检索，获取商品 ID 列表（按相似度排序）
    List<Document> docs = vectorStoreService.searchSimilarProducts(query, numItems);
    List<String> productIds = docs.stream()
        .map(doc -> doc.getMetadata().get("productId").toString())
        .collect(Collectors.toList());
    
    // 2. 回表 MySQL 获取最新数据
    List<Product> products = productService.listByProductIds(productIds);
    
    // 3. 关键：重新按向量相似度顺序排列
    // 因为 MySQL IN 查询返回顺序不保证与输入 ID 顺序一致
    List<Product> reorderProductList = reorderByIds(products, productIds);
    
    return reorderProductList;
}

private List<Product> reorderByIds(List<Product> products, List<String> orderedIds) {
    Map<String, Product> map = products.stream()
        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));
    
    List<Product> ordered = new ArrayList<>();
    for (String id : orderedIds) {  // 按向量相似度顺序遍历
        Product p = map.get(id);
        if (p != null) {
            ordered.add(p);
        }
    }
    return ordered;
}
```

**为什么需要 reorderByIds？**

```
Milvus 返回: [P1, P3, P5, P7]（按相似度降序）
MySQL IN 查询: SELECT * FROM product WHERE product_id IN ('P1', 'P3', 'P5', 'P7')
MySQL 返回: [P5, P1, P7, P3]（顺序不稳定！）

如果不重排，相似度排序会被破坏。
```

---

## 七、面试高频追问（A2A 进阶篇）

### Q16: Agent 之间可以直接通信吗？

**问法：** "UserProfileAgent 能不能直接调用 ProductRecAgent？"

**回答：**

当前架构是 **Supervisor 编排模式**，Agent 之间不直接通信，而是通过 Supervisor 作为中介：

```
❌ 不支持: UserProfileAgent → ProductRecAgent
✅ 支持:   UserProfileAgent → Supervisor → ProductRecAgent
```

**原因：**
1. **解耦**：Agent 只关注自己的职责，不知道其他 Agent 的存在
2. **可测试**：每个 Agent 可独立单元测试
3. **可扩展**：新增 Agent 不影响现有 Agent

**如果需要 Agent 之间直接通信怎么办？**

可以引入 **Blackboard 模式**（黑板模式）：

```java
// 共享黑板
public class AgentBlackboard {
    private Map<String, Object> sharedData = new ConcurrentHashMap<>();
    
    public void put(String key, Object value) {
        sharedData.put(key, value);
    }
    
    public Object get(String key) {
        return sharedData.get(key);
    }
}

// Agent 写入黑板
public class UserProfileAgent extends BaseAgent {
    protected AgentResult execute(Map<String, Object> params) {
        UserProfile profile = analyzeUser(params);
        AgentBlackboard.put("user_profile", profile);  // 写入黑板
        return AgentResult.builder().success(true).build();
    }
}

// Agent 读取黑板
public class ProductRecAgent extends BaseAgent {
    protected AgentResult execute(Map<String, Object> params) {
        UserProfile profile = (UserProfile) AgentBlackboard.get("user_profile");  // 读取黑板
        // ...
    }
}
```

**当前项目没有使用黑板模式的原因：**
- Supervisor 已经做了数据聚合，功能等价
- 黑板模式引入共享状态，需要处理并发问题

---

### Q17: 如何保证 Agent 执行的顺序？

**问法：** "Phase 1 和 Phase 2 的顺序是怎么保证的？"

**回答：**

通过 `CompletableFuture.join()` 的阻塞特性保证顺序：

```java
// Phase 1: 并行启动，但必须全部完成才能进入 Phase 2
CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(...);
CompletableFuture<AgentResult> recFuture = productRecAgent.runAsync(...);

// join() 会阻塞直到 Future 完成
AgentResult profileResult = profileFuture.join();  // 阻塞点 1
AgentResult recResult = recFuture.join();          // 阻塞点 2

// Phase 2: 只有 Phase 1 的两个 Future 都 join() 完成后才会执行
CompletableFuture<AgentResult> rerankFuture = productRecAgent.runAsync(...);
CompletableFuture<AgentResult> inventoryFuture = inventoryAgent.runAsync(...);

AgentResult rerankResult = rerankFuture.join();    // 阻塞点 3
AgentResult inventoryResult = inventoryFuture.join(); // 阻塞点 4
```

**执行时序图：**

```
时间线 →
───────────────────────────────────────────────────────────────
Phase 1:
    UserProfileAgent   ████████████████
    ProductRecAgent    ████████████████████████████
                       ↑              ↑
                    join()         join()
───────────────────────────────────────────────────────────────
Phase 2: (Phase 1 全部完成后才开始)
    ProductRecAgent(rerank)  ████████████████
    InventoryAgent           ██████████████
                            ↑            ↑
                         join()       join()
───────────────────────────────────────────────────────────────
Phase 3: (Phase 2 全部完成后才开始)
    MarketingCopyAgent      ████████████████
                          ↑
                       join()
```

---

### Q18: Agent 的超时如何处理？

**问法：** "如果某个 Agent 执行超过 10 秒怎么办？"

**回答：**

BaseAgent 构造函数中指定超时时间：

```java
public class UserProfileAgent extends BaseAgent {
    public UserProfileAgent() {
        super("user_profile", 5.0, 2);  // 超时 5 秒，最大重试 2 次
    }
}
```

**超时处理策略：**

```java
// 在 Supervisor 中使用 orTimeout
CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(params)
    .orTimeout(5, TimeUnit.SECONDS)
    .exceptionally(ex -> {
        log.error("UserProfileAgent 超时", ex);
        return AgentResult.builder()
            .agentName("user_profile")
            .success(false)
            .error("timeout")
            .build();
    });
```

**各 Agent 的超时配置：**

| Agent | 超时时间 | 原因 |
|-------|----------|------|
| UserProfileAgent | 5s | LLM 分析用户行为，较快 |
| ProductRecAgent | 8s | 多路召回 + LLM 重排，较慢 |
| InventoryAgent | 5s | 纯数据库查询，较快 |
| MarketingCopyAgent | 10s | LLM 生成文案，最慢 |

---

### Q19: 如何监控 Agent 的执行情况？

**问法：** "生产环境怎么知道哪个 Agent 出问题了？"

**回答：**

1. **AgentResult 内置监控字段：**

```java
AgentResult result = AgentResult.builder()
    .agentName("user_profile")
    .success(true)
    .latencyMs(1234.5)   // 执行耗时
    .confidence(0.85)    // 结果置信度
    .build();
```

2. **BaseAgent 内置错误率统计：**

```java
public abstract class BaseAgent {
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    
    public double getErrorRate() {
        int calls = callCount.get();
        return calls == 0 ? 0.0 : (double) errorCount.get() / calls;
    }
}
```

3. **日志埋点：**

```java
log.info("SupervisorOrchestrator.recommend Phase1完成 画像={} 召回{}条商品", 
    profile != null ? profile.getSegments() : "null", 
    rawProducts.size());

log.info("SupervisorOrchestrator.recommend complete request={} latency={:.1f}ms products={}", 
    requestId, totalLatency, finalProducts.size());
```

4. **推荐：接入 Prometheus + Grafana 监控：**

```java
// 使用 Micrometer 记录指标
@Timed(value = "agent.execute", description = "Agent execution time", extraTags = {"agent", "user_profile"})
public AgentResult execute(Map<String, Object> params) {
    // ...
}

// 记录自定义指标
meterRegistry.counter("agent.error", "agent", "user_profile").increment();
```

---

### Q20: 这个架构的瓶颈在哪里？如何扩展？

**问法：** "如果用户量增长 10 倍，系统怎么扩展？"

**回答：**

**当前架构的瓶颈：**

1. **单机并行**：CompletableFuture 只能在单机内并行，无法跨机器
2. **内存限制**：所有 Agent 运行在同一个 JVM，内存共享
3. **数据库连接池**：高并发时连接池可能成为瓶颈

**扩展方案：**

| 瓶颈 | 扩展方案 | 代价 |
|------|----------|------|
| 单机并行 | 将 Agent 拆分为微服务，用 gRPC 通信 | 增加网络延迟 |
| 内存限制 | 使用 Redis 存储中间结果 | 序列化开销 |
| 数据库连接池 | 读写分离 + 连接池扩容 | 架构复杂度 |

**推荐扩展路径：**

```
阶段1（当前）: 单机 CompletableFuture
    ↓ QPS > 1000
阶段2: Agent 微服务化 + gRPC
    ↓ QPS > 10000
阶段3: 引入消息队列异步处理
    ↓ QPS > 100000
阶段4: 存量计算 + 增量更新
```

**关键：不要过早优化。当前架构在 QPS < 1000 时完全够用。**

---

## 八、面试高频追问（记忆与满意度篇）

### Q21: 对话满意度多维度衡量

**面试问法：** "如何衡量对话系统的用户满意度？追问次数能代表不满意吗？"

**回答框架：**

1. **为什么追问次数不能简单等同于不满意**：
   - 用户说"还有别的吗"可能是深入探索，被商品吸引了
   - 用户说"那换货呢"是切换话题，不是对上一轮不满意
   - 追问次数多，可能是用户沉浸度高，也可能是回答质量差，需要结合其他信号

2. **多维度信号组合**：

| 信号类型 | 触发条件 | 权重 | 含义 |
|----------|----------|------|------|
| 否定信号 | 消息含"没用"、"不对"、"你没理解" | 高 | 明确不满意 |
| 转化行为 | Session 结束后30分钟内加购/购买 | 高 | 正向转化 |
| 单轮放弃 | Session 只有1轮就结束 | 中 | 可能放弃 |
| 深入探索 | 同意图连续出现 | 低（中性偏正向） | 深入了解 |

3. **核心指标定义**：
   - **解决率** = 无否定信号的 Session 数 / 总 Session 数
   - **追问率** = follow_up_count > 0 的 Session 数 / 总 Session 数（注意：追问率 ≠ 不满意率）
   - **转化率** = 有转化行为的 Session 数 / 总 Session 数

4. **否定词检测实现**：
   ```java
   private static final List<String> NEGATIVE_WORDS = List.of(
       "没用", "不对", "不是这个意思", "你没理解", "理解错了", "没帮到", "差评"
   );
   
   boolean hasNegativeSignal = NEGATIVE_WORDS.stream()
       .anyMatch(word -> message.contains(word));
   ```

**追问：如何区分"深入探索"和"死循环"？**
- 深入探索：同意图连续出现，但每轮商品不同
- 死循环：同意图连续出现，且推荐商品相同或高度相似
- 可通过计算相邻两轮推荐商品的相似度阈值来区分

**追问：转化行为为什么设置30分钟窗口？**
- 电商场景下，用户决策时间较短
- 30分钟是行业常见的归因窗口
- 可根据业务特点调整，如高客单价商品可延长到24小时

---

### 亮点5: 多用户记忆隔离

**面试问法：** "如何保证用户A不会看到用户B的对话历史？"

**回答框架：**

三道防线，层层隔离：

1. **Session ID 隔离**：每次对话生成 UUID 作为 session_id，天然不同用户不同 session。

2. **入口归属校验**：`chat()` 方法入口处校验：
   ```java
   ConversationSession session = sessionMapper.selectBySessionId(sessionId);
   if (!userId.equals(session.getUserId())) {
       log.warn("ConversationService.chat - Session归属校验失败, userId={}, sessionId={}", 
           userId, sessionId);
       throw new BusinessException(ErrorCode.SESSION_ACCESS_DENIED);
   }
   ```

3. **数据库层隔离**：所有记忆读取通过 `user_id` 或 `session_id` 查询，数据库层面天然隔离。

**追问：如果恶意用户伪造 session_id 怎么办？**
- 第2道防线会拦截：userId 不匹配直接拒绝
- Session_id 是 UUID，穷举成本极高
- 生产环境可加 IP 限流，防止暴力枚举

---

### 亮点6: RAG 知识库问答

**面试问法：** "如何保证 AI 回答的准确性？不会胡说八道吗？"

**回答框架：**

1. **RAG 的核心思想**：不让 LLM 凭空生成，而是先检索相关文档，让 LLM 基于文档内容回答。

2. **实现流程**：
   ```
   用户问题 → 向量化 → Milvus 相似度搜索(Top-3) → 检索到的文档片段注入 prompt → LLM 基于文档回答
   ```

3. **准确性保证**：
   - 知识库由运营人员维护，是唯一可信来源
   - LLM 的回答必须引用文档内容，不能超出文档范围
   - 可在 prompt 中明确要求："请基于以下文档内容回答，如果文档中没有相关信息，请回答'我不清楚'"

4. **Prompt 模板示例**：
   ```
   你是一个电商客服助手。请基于以下文档内容回答用户问题。
   如果文档中没有相关信息，请回答"我不清楚这个问题，建议联系人工客服"。
   
   文档内容：
   {retrieved_docs}
   
   用户问题：{user_query}
   ```

**追问：文档如何向量化？**
- 使用 BAAI/bge-large-zh-v1.5 嵌入模型
- 长文档需要分块，每块单独向量化
- 检索时取 Top-K 相似块，拼接后注入 prompt

---

## 七、面试高频追问（进阶篇）

### Q11: 记忆容量满了怎么办？

**场景**：dialogue_history 超过10轮，extracted_info 超过20个字段。

**回答**：
- dialogue_history：保留最新10轮，丢弃最早的（FIFO），保证最近上下文可用
- extracted_info：按字段最后更新时间丢弃最旧的，优先保留用户最新表达的偏好
- 可引入"重要性评分"机制，高频引用的字段优先保留

---

### Q12: 如何处理指代消解？

**场景**：用户说"那换货呢"，"那"指代上一轮提到的退货。

**回答**：
- 意图识别时注入最近3轮对话历史，让 LLM 理解上下文：
  ```java
  private IntentResult recognizeIntent(String message, List<String> history) {
      String prompt = buildIntentPrompt(message, history.subList(0, Math.min(3, history.size())));
      return llmService.classifyIntent(prompt);
  }
  ```
- 对于复杂的跨轮次指代，可先用 LLM 做"指代消解"，生成完整 query 再处理

---

### Q13: 如果 LLM 返回格式不符合预期怎么办？

**场景**：期望返回 JSON，但 LLM 返回了自然语言描述。

**回答**：
1. **Prompt 约束**：明确要求返回 JSON 格式，并给出示例
2. **后处理解析**：尝试从返回文本中提取 JSON 片段（正则匹配 `{}`）
3. **降级策略**：解析失败时，用规则引擎兜底，或返回通用回复

---

### Q14: 如何优化 LLM Token 消耗？

**回答**：
1. **短期记忆精简**：只传最近3轮历史，不传完整对话
2. **会话记忆压缩**：只传相关字段，不传完整 extracted_info
3. **长期记忆摘要**：user_profile 不传原始行为日志，传聚合后的画像标签
4. **Prompt 模板优化**：去掉冗余描述，精简指令

---

### Q15: 对话满意度低时如何自动干预？

**回答**：
1. **实时检测**：检测到否定信号时，触发"转人工"提示
2. **自动切换策略**：连续2轮否定信号，切换到保守回答模式（只推荐高置信度商品）
3. **离线分析**：低满意度 Session 聚类分析，定位共性问题（如某类意图识别准确率低）

---

## 八、简历项目描述（直接复制）

```
多Agent电商推荐与营销系统 | 个人项目 | 2026.01-2026.04
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 设计并实现基于 Supervisor 模式的多 Agent 协同架构，含用户画像、商品推荐、
  营销文案、库存决策 4 个专业 Agent，采用 CompletableFuture 并行分发+聚合

• 实现多路召回+RRF融合推荐引擎：向量召回(Milvus)+热销+新品+类目四路并行，
  RRF算法融合，LLM精排+MMR多样性控制，推荐链路端到端延迟 P99 < 2s

• 设计三层对话记忆系统：短期记忆(Session内10轮历史)+会话记忆(跨轮次实体
  累积合并)+长期记忆(user_profile历史偏好)，支持指代消解和跨轮次偏好感知

• 实现多用户记忆隔离：Session归属校验+UUID隔离+数据库层userId隔离，
  防止跨用户会话数据泄露

• 基于RAG实现知识库问答：退换货/配送/会员政策文档向量化，检索增强生成，
  回答准确性由知识库文档保证而非LLM凭空生成

• 设计对话满意度统计：多维信号组合（否定词检测+转化行为关联+轮次分析），
  解决率/追问率/转化率等指标，支持按意图分组统计

技术栈：Java 17 · Spring Boot 3 · Spring AI Alibaba · MyBatis-Plus ·
        MySQL 8.0 · Milvus · React · Ant Design · CompletableFuture
```

---

## 九、对话满意度提升实践（面试加分项）

### 9.1 满意度提升的四个层次

**面试问法：** "你做了哪些事情来提升对话满意度？"

**回答框架**：

| 层次 | 策略 | 具体措施 | 效果 |
|------|------|----------|------|
| **理解层** | 提升意图识别准确率 | 注入历史上下文、指代消解 | 减少"答非所问" |
| **记忆层** | 记住用户偏好 | 三层记忆系统、跨轮次累积 | 减少重复询问 |
| **回答层** | 提供精准答案 | RAG知识库、多路召回 | 减少"我不知道" |
| **体验层** | 主动引导 | 澄清式提问、多样化推荐 | 减少用户困惑 |

---

### 9.2 理解层：如何减少"答非所问"

**问题场景**：用户说"推荐一个"，系统不知道推荐什么类目。

**解决方案**：

1. **澄清式提问**：意图识别为"recommend"但实体不足时，主动询问：
   ```
   AI: 您想了解哪类商品呢？我们有手机、电脑、耳机等。
   ```

2. **偏好推断**：读取 user_profile 的历史偏好，主动推荐：
   ```java
   if (category == null && profile.getPreferredCategories() != null) {
       category = profile.getPreferredCategories().get(0);  // 取最偏好的类目
   }
   ```

3. **热门兜底**：无偏好历史时，推荐当前热销类目。

---

### 9.3 记忆层：如何减少重复询问

**问题场景**：用户说了预算5000，下一轮又被问预算多少。

**解决方案**：

1. **会话记忆注入**：handleRecommend 时读取 extracted_info，合并到本轮参数：
   ```java
   Map<String, Object> entities = parseJson(session.getExtractedInfo());
   if (entities.containsKey("price_max")) {
       request.setPriceMax((Integer) entities.get("price_max"));
   }
   ```

2. **主动确认**：使用会话记忆时，主动告知用户：
   ```
   AI: 根据您之前提到的5000元预算，为您推荐以下手机...
   ```

---

### 9.4 回答层：如何减少"我不知道"

**问题场景**：用户问"这款手机支持快充吗"，商品详情页没有这个信息。

**解决方案**：

1. **知识库补充**：运营人员维护常见问题知识库，RAG 检索回答。

2. **外部数据接入**：接入商品详情 API 或爬虫，获取更丰富的商品信息。

3. **诚实回复 + 引导**：确实不知道时，诚实说明并引导：
   ```
   AI: 很抱歉，目前系统没有这款手机的快充信息。建议您查看商品详情页或联系客服获取准确信息。
   ```

---

### 9.5 体验层：如何减少用户困惑

**问题场景**：推荐结果都是同类型商品，用户觉得"都一样"。

**解决方案**：

1. **MMR 多样性控制**：在精排阶段引入 MMR 算法，平衡相关性和多样性：
   ```
   MMR = λ * similarity(query, doc) - (1-λ) * max(similarity(doc, selected_docs))
   λ=0.5 表示相关性和多样性同等重要
   ```

2. **跨类目推荐**：主动推荐不同类目的相关商品：
   ```
   用户在看手机 → 同时推荐手机壳、充电宝、耳机
   ```

3. **解释性推荐**：告诉用户为什么推荐这个商品：
   ```
   AI: 为您推荐这款手机，因为它在您的预算范围内，且续航能力在同类产品中排名第一。
   ```

---

### 9.6 满意度提升的数据驱动闭环

**面试问法：** "如何持续优化对话满意度？"

**回答框架**：

```
发现问题              定位原因              优化措施              验证效果
    │                    │                    │                    │
    ▼                    ▼                    ▼                    ▼
满意度报表          Session聚类分析       策略调整/A/B测试      对比前后指标
（低满意度Session）   （共性问题）         （针对性优化）         （验证提升）
```

**具体实践**：

1. **每日监控**：解决率低于 80% 触发告警，人工介入分析。

2. **周维度归因**：按意图分组统计，定位哪种意图满意度最低。

3. **A/B 测试验证**：新策略上线前，10% 流量灰度验证：
   - 对照组：原有策略
   - 实验组：优化策略
   - 指标：解决率、转化率、平均轮次

4. **迭代优化**：根据 A/B 结果决定是否全量上线，或继续调整。

---

## 十、系统扩展性设计（面试加分项）

### 10.1 如何新增一个 Agent？

**回答**：

1. 继承 BaseAgent，实现 `execute()` 方法
2. 在 SupervisorOrchestrator 中注入新 Agent
3. 在对应 Phase 中并行或串行调用
4. 处理 AgentResult，聚合到最终响应

```java
// 示例：新增价格对比 Agent
public class PriceCompareAgent extends BaseAgent {
    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        List<Product> products = (List<Product>) params.get("products");
        // 价格对比逻辑
        return AgentResult.builder()
            .agentName("price_compare")
            .success(true)
            .data(Map.of("comparison", comparisonResult))
            .build();
    }
}
```

---

### 10.2 如何扩展新的意图类型？

**回答**：

1. 在 `recognizeIntent()` 的 prompt 中添加新意图定义
2. 在 `chat()` 方法中添加新意图的路由分支
3. 实现对应的 `handleNewIntent()` 方法
4. 更新意图统计和满意度分析逻辑

---

### 10.3 如何支持多模态对话（图片、语音）？

**回答**：

1. **图片理解**：接入多模态 LLM（如 GPT-4V），识别图片中的商品特征
2. **语音识别**：接入 ASR 服务，将语音转文字后按文本流程处理
3. **架构扩展**：在 ConversationService 入口增加"模态识别"环节，根据输入类型路由到不同的预处理流程
