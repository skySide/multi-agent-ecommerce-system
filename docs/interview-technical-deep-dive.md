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
│  SupervisorOrchestrator (CompletableFuture串行编排)           │
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
   MySQL 8.0          SimpleVectorStore       LLM API
  (业务数据)         (内存向量库+知识库)    (MiniMax/通义)
```

### 1.2 Supervisor 编排模式详解

**核心概念**：Supervisor（编排器）是 Agent 系统的中央控制器，负责接收用户请求、拆解任务、分发给各 Agent 执行、聚合结果。Agent 之间不直接通信，所有数据通过 Supervisor 中转。

```
                    ┌─────────────────────────────────────┐
                    │      SupervisorOrchestrator         │
                    │  - 接收用户请求                      │
                    │  - 拆解任务分发 Agent                │
                    │  - 处理降级决策                      │
                    │  - 聚合最终结果                      │
                    └──────┬──────────────┬──────────────┘
                           │              │
                    ┌──────▼──────┐ ┌─────▼──────┐
                    │ Agent A     │ │ Agent B    │
                    │ (领域逻辑)   │ │ (领域逻辑)  │
                    └─────────────┘ └────────────┘
```

**与微服务的区别**：
- Agent 是 JVM 内方法调用，无网络开销
- Supervisor 是普通 Service，不是独立部署单元
- Agent 之间不直接通信，数据通过 Supervisor 中转

**当前编排策略**：串行编排。画像 → 推荐 → 库存 依次执行，每步通过 `CompletableFuture.runAsync().join()` 包装。串行的好处是链路清晰、调试简单、每步可独立处理降级。互不依赖的步骤（如画像和召回）可轻松改为并行。

### 1.3 推荐链路全流程

```
用户请求 POST /api/v1/recommend
    │
    ▼
RecommendationController.recommend()
    │
    ▼
SupervisorOrchestrator.recommend()  [串行编排]
    │
    ├── 步骤1: UserProfileAgent
    │   └── LLM + UserBehaviorTool → 分析RFM/分群/偏好
    │       → saveOrUpdateProfile(user_profile表)
    │       → 失败降级: 默认画像(segments=active)
    │
    ├── 步骤2: ProductRecAgent
    │   └── 主路: LLM + ProductSearchTool 获取推荐
    │       工具返回完整 Product 实体 → 映射到 RecResult
    │       降级: RecommendEngineService 规则推荐 → 热门商品
    │
    └── 步骤3: InventoryAgent
        └── LLM + InventoryTool 检查库存
            → 过滤无货商品 → 设置限购策略
            → LLM失败降级 → fallbackCheck 硬编码规则检查
```

### 1.4 ProductRecAgent 内部流程（详细）

```
ProductRecAgent.execute()
    │
    ├── 主路 (优先): LLM + ProductSearchTool（Spring AI Tool模式）
    │   │
    │   ├── 步骤1: 构建 system prompt + user message
    │   │   systemPrompt = "你是一个资深的电商商品推荐专家..."
    │   │   userMessage = "用户ID: xxx, 用户画像: ..., 查询需求: ..."
    │   │
    │   ├── 步骤2: LLM 调用 ProductSearchTool.recommendProducts()
    │   │   │ 参数: userId, category, numItems
    │   │   │ 工具内部调用 RecommendEngineService.recommend()
    │   │   │ → 多路召回 → RRF融合 → 精排 → MMR多样性控制
    │   │   │ 返回: List<Product>（完整的 entity.Product）
    │   │   ▼
    │   │   LLM 获取到商品列表后，从中选取合适商品
    │   │   输出 JSON: {"products": [{"productId":"P001",...}]}
    │   │
    │   ├── 步骤3: chatClient.call().entity(RecResult.class)
    │   │   RecResult.products = List<Product>（完整实体）
    │   │
    │   └── 步骤4: 结果验证
    │       LLM返回结果不为空 → 直接使用
    │       LLM返回为空 → 降级到推荐引擎
    │
    └── 降级: RecommendEngineService.recommend()
        ├── Query改写 (LLM)
        ├── 多路召回 (向量0.4 + 热销0.2 + 新品0.2 + 类目0.2)
        ├── RRF融合 (k=60)
        ├── 精排 (规则排序 → LLM尝试调序)
        ├── MMR多样性控制 (λ=0.5)
        └── 最终兜底: listHotProducts()
```

> **关键设计**：ProductSearchTool 的 `recommendProducts()` 方法内部已经包含了完整的推荐引擎链路（多路召回+RRF+精排+MMR），返回的是 `entity.Product` 完整实体。LLM 在 Tool 模式下获取的是完整的商品数据，不涉及"LLM只输出ID然后回表"的逻辑。RecResult 中的 product 字段直接映射工具返回的完整 Product 对象。

### 1.5 对话链路全流程

```
用户消息 POST /api/v1/conversation/chat
    │
    ▼
ConversationServiceImpl.chat()
    │
    ├── 1. 会话归属校验
    │   sessionId为null → createSession()
    │   session不存在 → createSession()
    │   userId != session.userId → createSession()（不拒绝，保证体验）
    │
    ├── 2. 短期记忆读取
    │   dialogue_history（JSON数组，最新10轮，滑动窗口）
    │   + summary（对话摘要，由 ConversationSummaryTask 定时生成）
    │   → buildHistoryContext() 返回: 摘要 + 最近6条历史（约3轮对话）
    │
    ├── 3. 会话记忆读取（跨轮次实体累积）
    │   extracted_info（JSON对象）
    │   由 mergeWithSessionMemory() 合并当前实体和历史实体
    │
    ├── 4. 长期记忆读取（跨 Session 历史偏好）
    │   buildLongTermContext() → userProfileService.getByUserId()
    │   → 读取 preferred_categories + preferred_brands + price_range
    │   → 失败时 catch 返回空字符串，不影响主流程
    │
    ├── 5. 意图识别 (LLM + 历史上下文)
    │   → recommend / product_query / knowledge_query
    │     / compare / chitchat
    │
    ├── 6. 意图路由
    │   recommend     → mergeWithSessionMemory() → RecommendEngineService
    │   product_query → Query改写 → 向量召回 + 数据库关键词搜索
    │   knowledge_query → Query改写 → RAG检索知识库(SimpleVectorStore)
    │   compare       → 搜索多商品 → LLM生成对比分析
    │   chitchat      → 带历史上下文闲聊
    │
    └── 7. 记忆持久化
        dialogue_history: 追加本轮对话，超过10轮(20条)截断最早的
        extracted_info: mergeExtractedInfo() 合并式更新
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

### 2.2 ER 图

```
┌───────────────┐     ┌───────────────────┐     ┌───────────────┐
│     user      │     │   user_profile    │     │   product     │
├───────────────┤     ├───────────────────┤     ├───────────────┤
│PK id          │1    │PK id              │1    │PK id          │
│   user_id (U) │─────│   user_id (U) FK  │     │   product_id  │
│   username    │     │   segments        │     │   category_id │
│   email       │     │preferred_categories│     │   price       │
│   phone       │     │preferred_brands   │     │   stock       │
│   register_time│    │   price_range_min │     │   sales_count │
└───────┬───────┘     │   price_range_max │     └───────┬───────┘
        │             │   rfm_recency     │             │
        │             │   rfm_frequency   │             │
        │             │   rfm_monetary    │             │
        │             └───────────────────┘             │
        │                                               │
        │1     ┌───────────────────┐                    │
        ├──────│  user_behavior    │                    │
        │      ├───────────────────┤                    │
        │      │PK id              │                    │
        │      │   user_id         │                    │
        │      │   product_id      │────────────────────┘
        │      │   behavior_type   │
        │      │   search_keyword  │
        │      │   create_time(IDX)│
        │      └───────────────────┘
        │
        │1     ┌───────────────────┐     ┌───────────────────┐
        ├──────│conversation_session│     │   shopping_cart   │
        │      ├───────────────────┤     ├───────────────────┤
        │      │PK id              │     │PK id              │
        │      │   session_id (U)  │     │   user_id         │
        │      │   user_id         │     │   product_id      │
        │      │   dialogue_history│     │   quantity        │
        │      │   summary         │     │(user_id,product_id)U│
        │      │   extracted_info  │     └───────────────────┘
        │      │   status          │
        │      └───────────────────┘     ┌───────────────────┐
        │                                │   user_favorite   │
        │                                ├───────────────────┤
        │                                │PK id              │
        │                                │   user_id         │
        │                                │   product_id      │
        │                                │(user_id,product_id)U│
        │                                └───────────────────┘
        │
        │1     ┌───────────────────┐
        └──────│user_realtime_features    │
               ├───────────────────┤
               │PK id              │
               │   user_id (U)     │
               │   view_count_1h   │
               │   view_count_24h  │
               │   click_count_24h │
               │   cart_count_24h  │
               └───────────────────┘
```

### 2.3 关键设计决策

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

### 3.1 多路召回 + RRF融合（推荐引擎层）

```
RRF公式: score(d) = Σ weight_i / (k + rank_i(d))
k=60（平滑常数，避免排名靠前的商品分数过高）

各通道权重: 向量0.4, 热销0.2, 新品0.2, 类目0.2
通道内先稳定排序再参与RRF，保证rank语义准确
```

优势：不依赖各通道原始分数（向量相似度和销量是不同量纲），只用排名位置，天然解决量纲不统一问题。

### 3.2 向量召回后为什么要回表MySQL

向量库（SimpleVectorStore）存储的是 Embedding 向量和商品快照元数据，存在数据延迟：商品下架/价格调整/库存清零后，向量库中的数据不一定最新。回表 MySQL 获取最新数据，避免推荐已下架商品（降低幻觉）。

回表后还需要按向量相似度顺序重排（`reorderByIds()`），因为 MySQL IN 查询返回顺序不保证与输入 ID 顺序一致。

### 3.3 三层记忆系统

```
短期记忆:
  - dialogue_history（JSON数组，最新10轮=20条，滑动窗口）
  - summary（LLM生成的对话摘要，由 ConversationSummaryTask 定时在历史>5轮时生成）
  - 注入方式: buildHistoryContext() = 摘要 + 最近6条历史（约3轮对话）

会话记忆:
  - extracted_info（JSON对象，跨轮次实体累积）
  - 合并策略: mergeWithSessionMemory() → existing.putAll(newEntities)
  - 用途: "预算5000"→下一轮"推荐一个"，系统仍知道预算

长期记忆:
  - user_profile表（跨 Session 历史偏好）
  - 读取时机: buildLongTermContext() 在对话开始时调用
  - 注入内容: preferred_categories + preferred_brands + price_range
  - 降级: catch异常返回空字符串，不影响主流程
```

### 3.4 多用户记忆隔离

三道防线：
1. **Session ID 隔离**：每次对话生成 UUID 作为 session_id，天然不同用户不同 session
2. **入口归属校验**：`chat()` 中校验 `userId != session.userId`，不匹配时创建新会话（不拒绝请求）
3. **数据库层隔离**：所有记忆读取通过 `sessionId` 或 `userId` 查询，数据库层面天然隔离

> **关键点**：多用户隔离与向量库（Milvus/SimpleVectorStore）无关。隔离机制完全基于 sessionId（UUID 随机性）+ 数据库 userId 查询过滤。

### 3.5 BaseAgent 重试与降级

```
指数退避重试:
  maxRetries=2（最大尝试2次）
  第1次失败 → 等待 500ms 后重试
  第2次也失败 → 进入降级

降级: 全部重试失败返回 success=false 的 AgentResult

Supervisor对降级的处理:
- UserProfileAgent降级 → 使用默认画像(active用户)
- ProductRecAgent降级 → RecommendEngineService兜底 → 热门商品
- InventoryAgent降级 → fallbackCheck 硬编码规则检查库存
- MarketingCopyAgent降级 → 返回空文案列表
```

### 3.6 RAG知识库问答

```
知识库文档: 退换货政策 / 配送说明 / 会员权益 / 手机选购指南
向量存储: SimpleVectorStore（内存向量库，基于 Spring AI 的 EmbeddingModel）
分块策略: 500字符/块，50字符重叠 → Embedding → 存入 SimpleVectorStore

查询流程:
用户问题 → Query改写（rewriteQuery，结合历史和摘要）
→ 向量化 → SimpleVectorStore.similaritySearch(Top-3)
→ 检索到的文档片段注入prompt
→ LLM基于文档内容回答，而非凭空生成
```

**为什么是 SimpleVectorStore 而不是 Milvus？**
- SimpleVectorStore 是 Spring AI 提供的内存向量库，基于 EmbeddingModel 构建
- 数据存储在 JVM 内存中，重启后消失
- 当前适用于开发环境和演示，生产环境可替换为持久化 Milvus/Redis/PGVector

**准确性保证**：LLM 的回答来源于知识库文档，不是训练数据。知识库由运营人员维护，是唯一可信来源。

---

## 四、实现难点

### 难点1: 向量召回后回表顺序不稳定

MySQL IN 查询返回顺序不保证与输入 ID 顺序一致，导致向量相似度排序被破坏。

解决：`reorderByIds()` 以向量库返回的 ID 顺序为准，重新排列 MySQL 回表结果。

### 难点2: user_profile 唯一约束冲突

直接 `save()` 第二次调用抛 DuplicateKeyException。MyBatis-Plus 的 `saveOrUpdate()` 依赖自增主键，不适用业务主键场景。

解决：`saveOrUpdateProfile()` 先查后更新，手动实现 upsert 语义。

### 难点3: 对话意图识别的指代消解

"那换货呢"中的"那"指代上文的退货政策，LLM 不知道上下文会误判意图。

解决：意图识别 prompt 中注入最近对话历史 + 摘要，先进行 Query 改写（`rewriteQuery()`），让 LLM 理解上下文指代。

### 难点4: 会话记忆的合并策略

每轮直接覆盖 `extracted_info` 会丢失上一轮的"预算5000"等信息。

解决：合并式更新，`existing.putAll(newEntities)`，新值覆盖旧值但旧字段保留。

### 难点5: Agent 异常不传播到 Supervisor

Agent 抛异常会导致整个推荐链路失败。

解决：BaseAgent 内部捕获所有异常并返回降级的 AgentResult，不向外抛出。Supervisor 通过 `result.isSuccess()` 决定是否使用兜底数据。

### 难点6: 向量库数据一致性

SimpleVectorStore 是内存向量库，重启后数据消失，每次启动必须全量同步。

当前方案：`SystemBootstrap.run()` 启动时全量同步商品到向量库 + 初始化知识库文档。商品变更时通过 `ProductChangeEvent` + `VectorSyncListener` 异步增量同步。

生产方案：换用持久化向量库（Milvus/PGVector/Redis），存量只同步一次，增量走事件驱动。

### 难点7: 多路召回异常隔离（已解决）

四路召回中任一通道异常（如向量库不可用）会级联导致整个推荐失败。

解决：`multiChannelRecall()` 方法中每路召回单独 try-catch，异常时返回空列表并打印 error 日志，不中断其他通道。RRF 融合时自动跳过空通道。

### 难点8: Tool 调用机制的设计与约束

Agent 通过 Spring AI 的 Tool 机制调用外部工具获取数据，面临三个核心问题。

#### 问题1: Agent 如何知道调用哪个工具？

Agent 本身没有"选择工具"的能力，工具选择依赖于三层设计：

```
第一层: 注册隔离
  每个 Agent 只注册自己的工具集合：
  - ProductRecAgent  → .tools(productSearchTool)        → 只有商品检索工具
  - UserProfileAgent → .tools(userBehaviorTool)         → 只有用户行为工具
  - InventoryAgent   → .tools(inventoryTool)            → 只有库存工具
  - MarketingCopyAgent → .tools(productSearchTool, sensitiveWordTool) → 两个工具

  工具不在 Agent 的可见范围内 → LLM 不会得知该工具的存在
  → 从根本上杜绝跨域调用

第二层: System Prompt 指令约束
  每个 Agent 的 system prompt 明确告知 LLM 该用什么工具：
  - UserProfileAgent: "你可以使用 collectUserBehavior 工具收集用户的真实行为数据"
  - InventoryAgent: "请使用 queryProductStock 工具逐个查询每个商品的库存信息"
  - ProductRecAgent: "请根据用户的查询需求选择合适的商品检索工具"
  - MarketingCopyAgent: 明确四步骤调用链，指明每步用什么工具

第三层: @Tool 注解描述约束
  每个工具的 name 和 description 字段告诉 LLM 工具的用途和限制：
  @Tool(name = "recommendProducts", description = "【推荐引擎】...最常用工具，优先选择")
  @Tool(name = "retrieveSimilarProducts", description = "【向量搜索】...query参数必须有值不能为空")
  @Tool(name = "getHotProducts", description = "【热门商品】...适用于用户想看热门商品")
```

**关键设计理念**：不给 LLM 过多的自由选择权，而是通过 system prompt + 工具隔离 + 注解描述三重约束，将 LLM 的工具调用行为限制在可控范围内。

#### 问题2: 如何防止 Agent 乱调用工具？

```
防范策略1 — 工具隔离
  每个 Agent 只注入所需工具，LLM 无法"看到"其他 Agent 的工具。
  如 MarketingCopyAgent 包含 getProductInfo 和 filterSensitiveWords，
  但不会包含 inventoryTool，所以 LLM 不可能发起库存查询。

防范策略2 — 参数校验
  所有 Tool 方法入口做参数校验，无效参数直接返回空：
  if (org.apache.commons.lang3.StringUtils.isBlank(query)) {
      log.error("参数为空");
      return Collections.emptyList();  // 不抛异常，LLM 能继续推理
  }

防范策略3 — 降级兜底（防止连环调用）
  LLM 调用工具返回结果后，如果 JSON 解析失败或结果为空，
  Agent 有降级逻辑接管，不会让 LLM 无限重试或乱调用：
  if (Objects.isNull(result)) {
      result = fallbackCheck(productIds);  // 规则兜底
  }

防范策略4 — 工具描述约束
  @ToolParam 的 description 明确参数约束：
  @ToolParam(description = "搜索关键词...不能为空，如果用户没有提到具体商品则不要调用此工具")
```

#### 问题3: JSON 序列化与上下文窗口截断

这是 Tool 调用中最隐蔽也最常遇到的问题。

```
场景还原:
  1. LLM 调用 ProductSearchTool.recommendProducts()
  2. 工具返回 List<Product> — 每个 Product 包含十多个字段
  3. Spring AI 将工具返回结果序列化为 JSON，注入 LLM 的上下文
  4. 当推荐结果很多（如10个商品），JSON 字符串可能很长
  5. 超过 LLM 上下文窗口 → JSON 被截断 → Jackson 反序列化失败
  6. .entity(RecResult.class) 返回 null → 走降级逻辑

解决思路:
  1. 限制工具返回数量：recommendProducts 默认返回 10 条
  2. 降级兜底：LLM 解析失败时 fallbackRecommend() 接管
  3. 工具内控制数据量：retrieveSimilarProducts 通过 topK 控制返回条数
  4. 精简输出格式：ProductSearchTool 返回的是 entity.Product，
     包含必要的业务字段，没有冗余的大文本字段

实际表现:
  ProductRecAgent:
    LLM 成功 → RecResult.products 不为空 → 直接返回
    LLM 失败（JSON截断/解析异常）→ 降级 RecommendEngineService 规则推荐
    → 规则推荐也失败 → 兜底热门商品
```

**为什么降级在这里特别重要？**

Tools 返回的数据经过 JSON 序列化 → LLM 上下文 → LLM 输出 JSON → Jackson 反序列化，链路中任何一环出问题（尤其是截断）都会导致结果为空。没有降级的话，一次 JSON 截断就会让整个推荐返回空列表。

---

## 五、面试高频问题

### Q1: 为什么用 Supervisor 模式？

三个核心原因：
1. **职责分离**：Controller 只负责接口路由，Supervisor 负责编排，Agent 负责领域逻辑，各层各司其职
2. **上下文隔离**：每个 Agent 只关注自己领域，Token 消耗少、推理准确
3. **独立演进**：各 Agent 可独立升级、独立降级策略，互不影响

> 当前采用串行编排，未来可演进为并行。串行模式下每步可独立配置超时和降级，排查问题更直观。

### Q2: RRF 融合是什么，为什么比加权平均好？

RRF 公式：`score(d) = Σ weight_i / (k + rank_i(d))`

优势：不依赖各通道原始分数（向量相似度和销量是不同量纲），只用排名位置，天然解决量纲不统一问题。长尾商品分数衰减平缓，不会被完全忽略。

### Q3: 三层记忆如何保证多用户隔离？

**不是通过向量库隔离，而是通过三层数据库隔离**：

1. **Session ID 隔离**：`session_id` 是 UUID v4，天然随机、不可预测
2. **入口归属校验**：`chat()` 方法中校验 `session.getUserId()` 与请求 `userId` 是否一致，不一致时创建新会话（安全模式，不拒绝）
3. **数据库层隔离**：`buildLongTermContext()` 通过 `userId` 查询 `user_profile`，数据库 WHERE 子句天然过滤

> 这与向量库无关。多用户隔离只依赖 sessionId 的随机性 + 数据库层的 userId 查询过滤。

### Q4: 向量数据库和 MySQL 各存什么？

**MySQL**：存储所有业务结构化数据（用户、商品、库存、价格、状态等），支持精确查询、事务（ACID）、全文索引。

**SimpleVectorStore（内存向量库）**：存储商品的 Embedding 向量，支持语义相似度搜索。同时也存储知识库文档的向量（用于 RAG）。

两者互补：向量库做语义召回，MySQL 做精确过滤和数据校验（回表）。SimpleVectorStore 重启后数据消失，生产环境应替换为持久化方案。

### Q5: 如何保证 RAG 知识库回答的准确性？

RAG 的核心是"检索增强"，不让 LLM 凭空生成。用户问题先 Query 改写（结合历史和摘要），再向量化检索最相关文档片段（Top-3），注入 prompt 让 LLM 基于文档回答。知识库由运营人员通过 `KnowledgeController` 上传维护，是唯一可信来源。

### Q6: 对话满意度如何衡量？

通过用户显式反馈（点赞/点踩）衡量：

| 指标 | 计算方式 |
|------|---------|
| 满意度 | 点赞数 / (点赞数 + 点踩数) × 100% |
| 点赞数 | rating=1 的反馈总数 |
| 点踩数 | rating=-1 的反馈总数 |

接口：`POST /api/v1/chat/feedback` 提交反馈，`GET /api/v1/chat/feedback/stats` 获取统计。

### Q7: 系统延迟如何优化？

三个手段：Agent 超时降级（BaseAgent 构造函数指定 timeoutSeconds）、推荐缓存（recommend_cache 表）、LLM Prompt 精简（只传最近 6 条历史 + 摘要）。

### Q8: LLM 失败怎么办？

ProductRecAgent 有双层保障：

```
主路: LLM + ProductSearchTool.recommendProducts()
  → 工具内部是完整的 RecommendEngineService 推荐链路
  → LLM 输出 JSON → 解析为 RecResult
  → RecResult.products 不为空 → 直接返回
  → 为空 → 降级

降级: RecommendEngineService.recommend()（规则推荐链路）
  → 多路召回 + RRF + 精排 + MMR
  → 返回为空 → 最终兜底热门商品
```

### Q9: 为什么每次启动都要全量同步向量库？

当前使用 SimpleVectorStore（内存向量库），服务重启后数据消失。`SystemBootstrap.run()` 启动时自动同步商品数据到向量库 + 初始化知识库文档。生产环境应换用持久化方案。

### Q10: 购物车和收藏为什么建独立表？

`user_behavior` 是只追加的流水日志，不适合做状态查询（查当前购物车需扫描所有 cart 行为再去重）。独立表有唯一约束，支持精确状态查询、幂等操作和 quantity 字段。

---

## 六、技术亮点深度解析（面试必答）

### 亮点1: Supervisor + Agent 编排架构

**面试问法：** "这个项目最大的技术亮点是什么？为什么这样设计？"

**回答框架：**

1. **设计动机**：传统推荐系统的业务逻辑分散在 Service 层，职责不清、难以扩展。推荐策略（基于画像、基于库存、基于营销）耦合在一起，修改一个策略可能影响全局。

2. **Supervisor 编排模式**：采用 Supervisor 作为中央编排器，各 Agent 作为领域专家：
   - Agent 之间不直接通信，所有数据通过 Supervisor 中转
   - Agent 用统一的 `AgentResult` 数据结构返回结果
   - Supervisor 负责编排顺序和降级决策

3. **技术实现**：使用 `CompletableFuture` 统一包装 Agent 调用，当前为串行编排：
   ```java
   // 串行编排（当前实现）
   UserProfile profile = getUserProfile(userId, agentResults);
   List<Product> products = recommendProducts(userId, profile, numItems, agentResults);
   List<Product> finalProducts = checkInventory(products, numItems, agentResults);
   ```
   可轻松演进为并行：
   ```java
   CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(...);
   CompletableFuture<AgentResult> recFuture = productRecAgent.runAsync(...);
   AgentResult profileResult = profileFuture.join();
   AgentResult recResult = recFuture.join();
   ```

**追问：为什么不直接用微服务？**
- Agent 是同一个 JVM 内方法调用，无网络开销
- CompletableFuture 比 RPC 更轻量，适合细粒度编排
- 微服务适合服务间独立部署，Agent 适合功能内聚

---

### 亮点2: Agent 间 A2A 通信机制

**面试问法：** "Agent 之间是怎么通信的？数据怎么传递？"

#### 2.1 A2A 通信架构图

```
                    ┌─────────────────────────────────────┐
                    │      SupervisorOrchestrator         │
                    │      (中央编排器，负责协调)          │
                    └──────┬──────────────┬──────────────┘
                           │              │
                    ┌──────▼──────┐ ┌─────▼──────┐
                    │  Agent A    │ │  Agent B   │
                    │  执行领域逻辑│ │  执行领域逻辑│
                    │  返回结果   │ │  返回结果   │
                    └──────┬──────┘ └──────┬──────┘
                           │              │
                           ▼              ▼
                    ┌──────────────────────────────────┐
                    │   Supervisor 聚合结果              │
                    │   Map<String, AgentResult>        │
                    │   key="user_profile"              │
                    │   key="product_rec"               │
                    │   key="inventory"                 │
                    └──────────────────────────────────┘
```

**A2A 通信的三种方式对比：**

| 方式 | 描述 | 本项目使用 |
|------|------|-----------|
| **直接调用** | Agent A 直接调用 Agent B | ❌ 不支持 |
| **Supervisor 中转** | Agent A→Supervisor→Agent B | ✅ 当前方案 |
| **共享上下文** | 各 Agent 读写共享 Map | ✅ 配合使用 |

#### 2.2 核心数据结构：AgentResult

```java
@Data
@Builder
public class AgentResult {
    private String agentName;           // Agent 标识
    @Builder.Default
    private boolean success = true;     // 执行状态（关键：决定是否降级）
    @Builder.Default
    private double latencyMs = 0.0;     // 执行耗时
    private String error;               // 错误信息
    private Map<String, Object> data;   // 数据载体（灵活类型）
    @Builder.Default
    private double confidence = 1.0;    // 置信度
}
```

#### 2.3 串行通信流程（实际实现）

```java
// SupervisorOrchestrator.java

// 步骤1: 获取用户画像（串行调用 Agent）
private UserProfile getUserProfile(String userId, Map<String, AgentResult> agentResults) {
    AgentResult result = userProfileAgent.runAsync(
        Map.of("userId", userId)
    ).join();  // 阻塞等待结果

    agentResults.put("user_profile", result);

    UserProfile profile = result.getData() != null
        ? (UserProfile) result.getData().get("profile")
        : null;

    // 降级决策：Agent 失败 → 使用默认画像
    if (!result.isSuccess() || profile == null) {
        profile = UserProfile.builder().userId(userId).segments("active").build();
    }
    return profile;
}

// 步骤2: 推荐商品（依赖步骤1的 profile 结果）
private List<Product> recommendProducts(String userId, UserProfile profile, ...) {
    // 画像Agent的结果（profile）作为推荐Agent的输入
    AgentResult result = productRecAgent.runAsync(Map.of(
        "userId", userId,
        "userProfile", profile,    // ← 跨 Agent 数据传递
        "numItems", numItems
    )).join();
    // ...
}

// 步骤3: 库存检查（依赖步骤2的商品列表）
private List<Product> checkInventory(List<Product> products, ...) {
    List<String> productIds = extractIds(products);
    AgentResult result = inventoryAgent.runAsync(
        Map.of("productIds", productIds)  // ← 推荐Agent结果作为输入
    ).join();
    // ...
}
```

#### 2.4 各 Agent 的输入输出契约

| Agent | 输入 (Map<String, Object>) | 输出 (AgentResult.data) | Tool |
|-------|---------------------------|-------------------------|------|
| UserProfileAgent | `userId` | `profile`: UserProfile | UserBehaviorTool |
| ProductRecAgent | `userId`, `userProfile`, `numItems`, `userQuery`（可选） | `products`: List<Product>, `candidate_count` | ProductSearchTool |
| InventoryAgent | `productIds`: List<String> | `available_products`: List<String>, `low_stock_alerts`, `purchase_limits` | InventoryTool |
| MarketingCopyAgent | `userProfile`, `productIds` | `copies`: List<MarketingCopyVO>, `template_used` | ProductSearchTool, SensitiveWordTool |

#### 2.5 错误处理：Agent 失败不传播

```java
// BaseAgent.java - 核心错误处理逻辑
public CompletableFuture<AgentResult> runAsync(Map<String, Object> params) {
    return CompletableFuture.supplyAsync(() -> {
        callCount.incrementAndGet();
        Exception lastError = null;
        int attempt = 0;

        while (attempt < maxRetries) {  // maxRetries=2
            try {
                return execute(params);  // 成功直接返回
            } catch (Exception e) {
                lastError = e;
                attempt++;
                if (attempt < maxRetries) {
                    Thread.sleep((long) (500 * Math.pow(2, attempt - 1)));  // 500ms
                }
            }
        }
        // 全部失败 → 降级（不抛异常）
        return fallback(latencyMs, lastError);
    });
}

// Supervisor 检查 isSuccess 决定是否使用兜底
if (!result.isSuccess()) {
    log.warn("Agent失败，使用默认值");
    profile = defaultProfile();  // 兜底
}
```

### 亮点3: BaseAgent 重试 + 降级两位一体

**面试问法：** "Agent 调用失败怎么办？如何保证系统稳定性？"

**回答框架：**

1. **指数退避重试**：第一次失败后等待 500ms 再重试（maxRetries=2，最多尝试2次），给下游恢复时间

2. **优雅降级**：全部失败返回 `success=false`，不抛异常。Supervisor 根据 `isSuccess()` 决定兜底

3. **错误率统计**：BaseAgent 内置 `callCount` 和 `errorCount`，可在子类中扩展监控

**追问：为什么只重试1次（maxRetries=2）？**
- LLM 调用耗时较长，多次重试会大幅增加总延迟
- 500ms 足够解决大多数网络抖动
- 全局降级兜底比多次重试更可靠

### 亮点4: 推荐引擎多路召回 + RRF 融合算法

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
   │(SimVec)  │  │(MySQL)   │  │(MySQL)   │  │(MySQL)   │
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
                    │  k=60        │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  规则精排    │
                    │  (LLM调序)   │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ MMR多样性    │
                    │ λ=0.5        │
                    └──────┬───────┘
                           │
                           ▼
                    [最终推荐列表]
```

#### 4.2 RRF 算法详解

```java
private List<Product> mergeByRrf(Map<String, List<Product>> channelResult, int limit) {
    Map<String, Product> productMap = new HashMap<>();
    Map<String, Double> scores = new HashMap<>();

    for (Map.Entry<String, List<Product>> entry : channelResult.entrySet()) {
        String channel = entry.getKey();
        if ("merged".equals(channel)) continue;
        List<Product> products = entry.getValue();
        if (products == null || products.isEmpty()) continue;

        double weight = CHANNEL_WEIGHTS.getOrDefault(channel, 0.1);
        // 向量0.4, 热销0.2, 新品0.2, 类目0.2

        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            if (p == null || p.getProductId() == null) continue;
            productMap.putIfAbsent(p.getProductId(), p);
            double inc = weight / (RRF_K + i + 1.0);  // rank = i+1
            scores.merge(p.getProductId(), inc, Double::sum);
        }
    }
    return scores.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
        .limit(limit)
        .map(e -> productMap.get(e.getKey()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
}
```

#### 4.3 为什么 RRF 比加权平均好？

| 对比维度 | 加权平均 | RRF |
|----------|----------|-----|
| **量纲问题** | 向量相似度 [0,1]，销量 [0,10000]，量纲不统一 | 只用排名位置，天然无量纲 |
| **长尾商品** | 分数低则权重低，容易被忽略 | rank 衰减平缓，长尾仍有机会 |
| **通道稳定性** | 需要各通道分数归一化，实现复杂 | 直接用排名，简单可靠 |

### 亮点5: 三层记忆系统

**面试问法：** "对话系统如何记住上下文？多轮对话的偏好如何累积？"

#### 5.1 三层记忆架构

| 记忆层 | 存储字段 | 容量 | 用途 | 合并策略 |
|--------|---------|------|------|----------|
| 短期记忆 | `dialogue_history` + `summary` | 10轮 滑动窗口 | 注入 LLM prompt | 追加，超10轮截断最早 |
| 会话记忆 | `extracted_info` | 无固定限制 | 跨轮次实体累积 | `putAll` 新值覆盖旧值 |
| 长期记忆 | `user_profile` | 无限制 | 跨 Session 历史偏好 | 只读，不写入 |

#### 5.2 短期记忆详细设计

```
短期记忆 = dialogue_history（滑动窗口保持最新10轮）
         + summary（LLM 摘要，由 ConversationSummaryTask 定时生成）

注入方式: buildHistoryContext()
1. 如果有 summary，先输出 "对话摘要：{summary}"
2. 取 dialogue_history 最新 6 条（≈3轮对话）作为"最近对话"
3. 拼接后注入所有意图处理的 LLM prompt
```

```java
private String buildHistoryContext(List<String> history, String summary) {
    StringBuilder sb = new StringBuilder();
    if (summary != null && !summary.isEmpty()) {
        sb.append("对话摘要：").append(summary).append("\n\n");
    }
    if (!history.isEmpty()) {
        List<String> recent = history.subList(
            Math.max(0, history.size() - 6), history.size());
        sb.append("最近对话：\n").append(String.join("\n", recent)).append("\n\n");
    }
    return sb.toString();
}
```

#### 5.3 会话记忆合并算法

```java
private Map<String, Object> mergeExtractedInfo(
        String existingJson, Map<String, Object> newEntities) {
    Map<String, Object> existing = new HashMap<>();
    if (existingJson != null && !existingJson.isEmpty()) {
        existing = objectMapper.readValue(existingJson, Map.class);
    }
    existing.putAll(newEntities);  // 新覆旧，旧字段保留
    return existing;
}
```

### 亮点6: 向量召回后回表 MySQL 的设计

**面试问法：** "为什么向量召回后还要回表 MySQL？"

**核心问题**：SimpleVectorStore 存储的是 Embedding 向量和商品快照元数据。MySQL 中的数据变更（价格调整、库存变化、商品下架）不会实时同步到向量库。回表 MySQL 获取最新数据，避免推荐过期商品。

**代码实现：**

```java
public List<Product> vectorRecall(String query, UserProfile profile, int numItems) {
    // 1. 向量检索
    List<Document> docs = vectorStoreService.searchSimilarProducts(query, numItems * 2);
    List<String> productIds = extractIds(docs);

    // 2. 回表 MySQL 获取最新数据
    List<Product> products = productService.listByProductIds(productIds);

    // 3. 按向量相似度顺序重排
    return reorderByIds(products, productIds);
}
```

**为什么需要 reorderByIds？**

```
向量库返回: [P1, P3, P5, P7]（按相似度降序）
MySQL IN 返回: [P5, P1, P7, P3]（顺序不稳定！）
→ 必须重排，否则向量相似度排序被破坏。
```

---

## 七、面试高频追问

### Q11: 如何保证 Agent 执行的顺序？

当前 Supervisor 是串行编排，通过方法调用的天然顺序保证：

```java
// 天然顺序：步骤1 → 步骤2 → 步骤3
UserProfile profile = getUserProfile(userId, agentResults);
List<Product> products = recommendProducts(userId, profile, numItems, agentResults);
List<Product> finalProducts = checkInventory(products, numItems, agentResults);
```

可轻松改为并行：
```java
CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(...);
CompletableFuture<AgentResult> recFuture = productRecAgent.runAsync(...);
AgentResult profileResult = profileFuture.join();
AgentResult recResult = recFuture.join();
AgentResult inventoryResult = inventoryAgent.runAsync(...).join();
```

### Q12: Agent 的超时如何处理？

BaseAgent 构造函数中配置超时时间：

| Agent | 超时时间 |
|-------|----------|
| UserProfileAgent | 5s |
| ProductRecAgent | 8s |
| InventoryAgent | 5s |
| MarketingCopyAgent | 10s |

超时由 Spring AI 的客户端超时配置 + `CompletableFuture` 的线程池任务超时共同保证。

### Q13: 会话归属校验不匹配时怎么处理？

**安全模式**：不拒绝，创建新会话：

```java
if (!userId.equals(session.getUserId())) {
    log.warn("会话归属校验失败...");
    sessionId = createSession(userId);  // 创建新会话
    session = conversationSessionService.getBySessionId(sessionId);
}
```

**为什么不直接拒绝？** 电商场景用户体验优先，拒绝请求可能导致用户流失。创建新会话是安全的——旧会话数据不会被新用户访问。

### Q14: 如何监控 Agent 的执行情况？

1. **AgentResult 内置监控字段**：`success`、`latencyMs`、`confidence`、`error`
2. **BaseAgent 内置错误率统计**：`callCount` / `errorCount` → `getErrorRate()`
3. **Supervisor 日志**：每个步骤结束后输出耗时和状态

### Q15: 这个架构的瓶颈在哪里？如何扩展？

**当前瓶颈**：串行执行、单机部署、数据库连接池。

**扩展路径**：
```
阶段1（当前）: 串行编排 + 单体部署
阶段2: Agent 并行化 + 读写分离
阶段3: Agent 微服务化 + gRPC
阶段4: 引入消息队列异步处理
```

### Q16: 对话满意度如何衡量？

通过 `ChatFeedbackController` 的点赞/点踩反馈：

```java
POST /api/v1/chat/feedback
{ "userId": "U001", "sessionId": "S001", "rating": 1 }
// rating=1 点赞, rating=-1 点踩

GET /api/v1/chat/feedback/stats
// 返回: 满意度 = 点赞数 / (点赞数 + 点踩数) × 100%
```

### Q17: 记忆容量满了怎么办？

- dialogue_history 超10轮 → 截断最早的，保留最新10轮
- extracted_info → 合并式更新，不主动删除
- summary → ConversationSummaryTask 定时（每10分钟）检查超5轮的会话，LLM生成摘要

### Q18: 如何处理指代消解？

- 意图识别注入最近对话历史 + 摘要，让 LLM 理解上下文指代
- knowledge_query 先 Query 改写（`rewriteQuery()`），把"那换货呢"改写为明确问题
- recommend 场景 `mergeWithSessionMemory()` 合并上一轮实体

### Q19: 如果 LLM 返回格式不符合预期怎么办？

1. Spring AI `.entity(Class)` 自动映射 JSON 到 POJO
2. 解析失败时 `catch` 降级：ProductRecAgent → RecommendEngineService 兜底；UserProfileAgent → 默认画像

### Q20: 如何优化 LLM Token 消耗？

1. `buildHistoryContext()` 只传摘要 + 最近 6 条历史
2. `buildLongTermContext()` 只传聚合标签（类目/品牌/价格区间）
3. 意图识别 prompt 精简，只传必要上下文

---

## 八、简历项目描述（直接复制）

```
多Agent电商推荐与营销系统 | 个人项目 | 2026.01-2026.04
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 基于Spring Boot 3+Spring AI构建多Agent电商推荐系统，
  Supervisor编排模式串联用户画像Agent、商品推荐Agent、库存决策Agent串行执行，
  Agent间通过统一数据结构（AgentResult）通信，CompletableFuture统一编排

• 集成多轮对话引擎，三层记忆系统（dialogue_history+summary短期记忆/
  extracted_info会话记忆/user_profile长期记忆），
  ConversationSummaryTask定时生成摘要降低Token消耗

• 实现推荐引擎多路召回（向量40%+热销20%+新品20%+类目20%）+RRF融合+
  规则排序+LLM调序+MMR多样性控制，向量召回后回表MySQL修复数据一致性

• RAG知识库问答：文档分块→Embedding→SimpleVectorStore语义检索，
  运营通过KnowledgeController上传维护知识库

• 多层级降级网格：BaseAgent重试+降级 → Agent内LLM兜底 → 编排器默认值 →
  热门商品保底，任一环节故障自动切换替代路径

• 对话满意度统计：用户点赞/点踩反馈（ChatFeedbackController），
  支持按用户和全局维度统计满意度

技术栈：Java 17 · Spring Boot 3 · Spring AI · MyBatis-Plus ·
        MySQL 8.0 · SimpleVectorStore · React · Ant Design
```

---

## 九、对话满意度提升实践（面试加分项）

### 9.1 满意度提升的四个层次

| 层次 | 策略 | 具体措施 |
|------|------|----------|
| **理解层** | 提升意图识别准确率 | 注入历史上下文+摘要、Query改写 |
| **记忆层** | 记住用户偏好 | 三层记忆系统、mergeWithSessionMemory() |
| **回答层** | 提供精准答案 | RAG知识库、多路召回 |
| **体验层** | 主动引导澄清 | Query改写扩召回、MMR多样性控制 |

### 9.2 理解层：如何减少"答非所问"

- **Query 改写**：RecommendEngineService 入口调用 `queryRewriteService.rewrite()`，用 LLM 从上下文理解用户真实意图
- **偏好继承**：`mergeWithSessionMemory()` 合并 extracted_info 中的历史实体
- **热门兜底**：无偏好时推荐热销商品

### 9.3 记忆层：如何减少重复询问

- `mergeWithSessionMemory()` 合并上一轮实体，记住用户已表达的偏好
- `buildLongTermContext()` 从 user_profile 读取历史偏好

### 9.4 回答层：如何减少"我不知道"

- RAG 知识库：运营维护退换货/配送/会员权益等文档
- 诚实回复 + 引导：不知道时建议联系人工客服

### 9.5 体验层：如何减少用户困惑

- MMR 多样性控制（λ=0.5），避免推荐结果全是同类商品
- Query 改写扩召回：`queryRewriteService.expandVariants()` 生成同义查询变体

---

## 十、系统扩展性设计（面试加分项）

### 10.1 如何新增一个 Agent？

1. 继承 BaseAgent，实现 `execute()` 方法
2. 定义输入输出契约（Map 参数 + AgentResult.data）
3. 实现 Tool（如需要），在 Agent 中注入
4. 在 SupervisorOrchestrator 中注入新 Agent，编排链路中调用

```java
@Component
public class PriceCompareAgent extends BaseAgent {
    public PriceCompareAgent() {
        super("price_compare", 5.0, 2);
    }
    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 业务逻辑
        return AgentResult.builder().agentName("price_compare")
            .success(true).data(Map.of("result", result)).build();
    }
}
```

### 10.2 如何扩展新的意图类型？

1. `recognizeIntent()` 的 prompt 中添加新意图定义
2. `chat()` switch 中添加新意图路由分支
3. 实现 `handleNewIntent()` 方法
4. 更新意图统计逻辑

### 10.3 如何支持多模态对话？

1. 图片理解：接入多模态 LLM，识别图片中的商品特征
2. 语音识别：接入 ASR 服务，语音转文字后按文本流程处理
3. 架构扩展：ConversationService 入口增加"模态识别"环节
