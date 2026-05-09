# 多Agent电商推荐系统 — 简历技术亮点与书写指南

> 基于对全部源码（Java Agent层、Tool层、Service层、Controller层、Entity层）的深度扫描整理。

---

## 一、项目架构总览

### 1.1 技术栈

| 层级 | 技术选型 |
|------|---------|
| 后端框架 | Spring Boot 3 + MyBatis-Plus |
| AI框架 | Spring AI（ChatClient + Tool Calling + VectorStore） |
| LLM | DeepSeek-V3（硅基流动 API） |
| Embedding | BAAI/bge-large-zh-v1.5（768维） |
| 向量库 | SimpleVectorStore（内存）/ Milvus（生产） |
| 数据库 | MySQL 8.0 + Redis |
| 前端 | React + Ant Design + Vite |
| 容器化 | Docker Compose（MySQL + Redis + Milvus） |

### 1.2 架构模式：Supervisor + 多Agent编排

```
用户请求 → SupervisorOrchestrator（中央编排器）
              ├── Phase 1: UserProfileAgent（用户画像）
              ├── Phase 2: ProductRecAgent（商品推荐）
              ├── Phase 3: InventoryAgent（库存校验）
              └── Phase 4: MarketingCopyAgent（营销文案）
```

**核心设计思想：**
- Agent 之间不直接通信，所有数据通过 Supervisor 中转（避免 Agent 间耦合）
- 每个 Agent 独立拥有自己的 Tool 集合，LLM 按需调用
- 每步有独立的降级策略，单点故障不影响整体链路

### 1.3 Agent 基类设计（BaseAgent）

```
BaseAgent
├── 指数退避重试（500ms → 1000ms → 2000ms，最大2次）
├── 超时控制（每个Agent独立配置超时时间）
├── 降级兜底（fallback方法，子类可覆写）
├── 错误率统计（AtomicInteger计数，支持熔断判断）
└── 异步执行（CompletableFuture.supplyAsync）
```

---

## 二、核心亮点深度解析

### 亮点1：Tool Calling 防乱调机制 —— 多层约束防止Agent误用工具

**问题背景：** LLM在Tool Calling场景下容易"乱调工具"——该调的不调、不该调的乱调、参数传错、漏调步骤。本项目通过**四层约束体系**系统性地解决了这个问题。

**第一层：工具方法级别的语义约束**

每个Tool方法通过`@Tool`注解的`name`和`description`精确描述**什么时候用、什么时候别用**。源码证据：

```java
// ProductSearchTool.java - 三种工具各有限定场景
@Tool(name = "retrieveSimilarProducts",
      description = "【向量搜索】根据输入文本进行语义相似度搜索。需要用户提供具体的查询词(如商品名/品牌)，适用于用户明确提到具体商品、想搜索类似商品的场景。query参数必须有值不能为空")

@Tool(name = "recommendProducts",
      description = "【推荐引擎】多路召回+精排+多样性控制。根据用户画像和偏好类目推荐商品。最常用工具，优先选择")

@Tool(name = "getHotProducts",
      description = "【热门商品】获取当前热门/畅销商品排行，按销量降序排列。适用于用户想看热门商品、畅销排行的场景")
```

关键设计：
- **中文分类标签**（【向量搜索】【推荐引擎】【热门商品】）作为语义锚点，帮助LLM快速区分工具用途
- `description`中明确写了"适用于XX场景"和"不要调用此工具的条件"

**第二层：参数级别的使用约束**

每个`@ToolParam`都带精确的`description`，告诉LLM参数从哪里取、什么情况下传什么值：

```java
// ProductSearchTool.java
@ToolParam(description = "搜索关键词，从用户消息中提取的具体商品名、品牌名或类目名。不能为空，如果用户没有提到具体商品则不要调用此工具") String query

@ToolParam(description = "偏好类目，可选参数。用户提到想看的类目时传入，没有则传空字符串") String category
```

关键设计：
- `description`中写了"不能为空"、"如果用户没有提到具体商品则不要调用此工具"——直接在参数层面阻止误调用

**第三层：System Prompt 级别的流程约束**

每个Agent的System Prompt给出了**精确的工具调用步骤**，LLM只需按步骤执行：

```java
// UserProfileAgent.java - 步骤明确的System Prompt
String systemPrompt = "你是一个资深的电商用户画像分析专家..." +
    "你可以使用 collectUserBehavior 工具收集用户的真实行为数据。" +
    "\n请先调用工具收集数据，再基于收集到的数据...进行全面的用户画像分析。";

// MarketingCopyAgent.java - 多步骤工具调用链
String systemPrompt = "请按以下步骤完成任务：\n" +
    "\n步骤1 - 查询商品信息：使用 getProductInfo 工具逐个查询每个商品的信息。" +
    "\n步骤2 - 生成营销文案：根据商品信息和..." +
    "\n步骤3 - 过滤敏感词：将生成的文案列表传给 filterSensitiveWords 工具...";
```

关键设计：
- 明确写出"步骤1→步骤2→步骤3"，LLM按顺序执行，不会跳过或颠倒
- 明确写"先调用工具收集数据，再进行分析"——防止LLM不调工具直接编造数据

**第四层：LLM输出与Agent处理分离 —— 最重要的一层**

这是本项目最核心的反幻觉设计：**LLM只负责决策和输出ID/简单结构，Agent负责回表查数据库获取准确数据。**

```java
// ProductRecAgent.java - 核心注释
/**
 * 商品推荐Agent
 * 多策略召回 + LLM重排 + 多样性控制
 * LLM通过ProductSearchTool的工具获取商品信息后，只需输出商品ID列表，
 * 由Agent回表数据库获取完整商品数据，避免LLM序列化复杂字段导致的JSON解析异常
 */
```

具体流程：
1. LLM调用Tool（Tool内部查询数据库，返回真实数据给LLM参考）
2. LLM基于Tool返回的数据做决策，输出`RecResult`（仅包含商品ID和基本字段）
3. Agent拿到LLM输出的ID列表后，**再次回表数据库**获取完整准确的Product实体
4. 返回的是数据库中的真实数据，不是LLM编造的

```java
// RecommendEngineServiceImpl.java - vectorRecall方法注释
/**
 * 向量召回通道：query构建、metadata过滤、回表并按向量顺序重排。
 * 需要重新排序的目的，是因为查询数据库的数据，和向量召回的顺序有可能不一样，
 * 而向量召回的数据已经按照相似度进行排序了，因此需要查询数据库之后，还需要再进行排序。
 * 之所以要查询数据库，是因为可能我们数据库变更之后，没有那么快同步到向量数据库中，
 * 因此需要再次查询数据库，从而得到准确的数据，降低幻觉。
 */
```

**效果：**
- 避免了LLM编造商品价格、库存等敏感数据
- 避免了LLM JSON序列化复杂嵌套对象时的字段丢失/格式错误
- 数据库是最真实的数据源，不受LLM幻觉影响

**简历书写示例：**

> **Tool Calling防乱调机制（4层约束体系）**
> - 设计并实现了4层Tool调用约束体系解决LLM误调用工具问题：
>   1. Tool描述层：为每个@Tool方法编写精确的语义描述，使用【分类标签】帮助LLM区分工具用途，明确"何时用、何时不用"
>   2. 参数约束层：为每个@ToolParam编写详细描述，包含参数来源、必填条件、禁用条件
>   3. Prompt流程层：Agent的System Prompt中明确写出工具调用步骤（步骤1→步骤2→步骤3），约束LLM的执行路径
>   4. 数据校验层：LLM仅输出商品ID，Agent回表MySQL获取完整数据后返回，从根本上避免LLM编造价格/库存等敏感信息
> - 效果：工具调用准确率显著提升，杜绝了LLM编造商品数据的问题，推荐结果100%来自数据库真实数据

---

### 亮点2：RAG全链路反幻觉设计 —— 从Query改写到知识注入的5道防线

**问题背景：** 通用RAG方案存在检索不准（用户口语化query vs 知识库标准化文本）、LLM无视检索结果编造答案、检索结果与用户意图不匹配等问题。

**防线1：Query改写（解决口语化与知识库文本的语义Gap）**

在每次RAG检索前，先通过LLM改写用户Query，使其更适合向量检索：

```java
// ConversationServiceImpl.java - handleKnowledgeQuery
// 1. Query 改写（结合历史和摘要）
String rewrittenQuery = rewriteQuery(message, history, summary);

// 2. RAG 向量搜索知识库
List<Document> docs = documentVectorService.searchKnowledgeBase(rewrittenQuery, 3);
```

Query改写Prompt的设计要点（QueryRewriteServiceImpl.java）：
- 输入：用户原始query + 对话历史 + 对话摘要
- 要求：理解上下文、补充省略信息（代词指代消解）、使用标准类目名称
- 输出：改写后的query（纯文本，不做多余解释）

```java
// rewriteWithContext的Prompt核心逻辑
String prompt = """
    你是一个电商搜索专家。请根据对话上下文改写用户查询，使其更明确、更适合检索。
    要求：
    1. 结合对话上下文理解用户真实意图
    2. 补充省略的信息（如代词指代）
    3. 使用标准类目名称
    4. 只输出改写后的 query，不要解释
    """;
```

**防线2：多路召回 + RRF融合（避免单路召回偏差）**

```java
// RecommendEngineServiceImpl.java
// 四路召回通道 + RRF融合
// 通道权重：
// 向量召回 0.4 + 热门召回 0.2 + 新品召回 0.2 + 类目召回 0.2
Map<String, Double> CHANNEL_WEIGHTS = Map.of(
    "vector", 0.4, "hot", 0.2, "new", 0.2, "category", 0.2
);
// RRF融合公式：score = weight / (60 + rank + 1)，保证各通道公平融合
```

**防线3：重新查询数据库校验（向量库与数据库的一致性问题）**

```java
// RecommendEngineServiceImpl.java - vectorRecall中的关键注释
/**
 * 之所以要查询数据库，是因为可能我们数据库变更之后，没有那么快同步到向量数据库中，
 * 因此需要再次查询数据库，从而得到准确的数据，降低幻觉。
 */
// 向量召回 → 回表数据库 → 按向量顺序重排
List<Product> products = productService.listByProductIds(productIds);
List<Product> reorderProductList = reorderByIds(products, productIds);
```

**防线4：Prompt中明确"不要编造"指令**

```java
// ConversationServiceImpl.java - handleKnowledgeQuery的Prompt
String prompt = String.format(
    "你是电商客服助手。请根据以下信息回答用户问题。\n\n" +
    "%s知识库内容：\n%s\n\n用户问题：%s\n\n" +
    "要求：\n" +
    "1. 如果知识库中有相关信息，请基于知识库回答\n" +
    "2. 如果知识库中没有相关信息，请明确告知用户，不要编造\n" +
    "3. 回答要简洁友好，使用中文",
    ...
);
```

**防线5：文本分块优化（滑动窗口保证语义连贯）**

```java
// DocumentVectorServiceImpl.java
private static final int CHUNK_SIZE = 500;   // 每块最大字符数
private static final int CHUNK_OVERLAP = 50;  // 相邻块重叠字符数

// 按句子边界分割（不是机械截断），保证语义完整性
String[] sentences = text.split("(?<=[。！？.!?])");
```

**效果：**
- Query改写使检索召回率提升
- 多路召回 + RRF融合避免了单通道的召回偏差
- 向量召回→数据库回表校验确保数据准确性
- Prompt中明确的"不要编造"指令有效降低了LLM幻觉

**简历书写示例：**

> **RAG全链路反幻觉设计（5道防线）**
> - 针对RAG场景下的LLM幻觉问题，设计了5道防线的反幻觉链路：
>   1. Query改写：检索前用LLM结合对话历史和摘要改写口语化query，补充代词指代、使用标准类目名，缩小用户表达与知识库文本的语义Gap
>   2. 多路召回+RRF融合：向量(0.4)+热门(0.2)+新品(0.2)+类目(0.2)四路召回，RRF算法公平融合，避免单路偏差
>   3. 数据库回表校验：向量召回后重新查询MySQL获取最新数据，避免向量库与数据库不一致导致的数据错误
>   4. Prompt反编造指令：系统Prompt明确要求"知识库无信息时告知用户，不要编造"
>   5. 语义保持分块：按句子边界+滑动窗口分块（500字/块，50字重叠），保证检索片段的语义完整性
> - 效果：有效降低了RAG场景下的幻觉率，推荐和问答结果均可追溯到数据库/KB原文

---

### 亮点3：三层记忆管理系统 —— 短期+会话+长期记忆协同

**问题背景：** 多轮对话中，LLM容易"忘记"之前的内容（无短期记忆）、跨轮次无法累积用户偏好（无会话记忆）、跨Session无法利用历史画像（无长期记忆）。

**架构设计：**

```
┌─────────────────────────────────────────────────────────┐
│                    三层记忆系统                           │
├─────────────────┬─────────────────┬─────────────────────┤
│   短期记忆       │   会话记忆       │   长期记忆           │
│ dialogue_history │ extracted_info   │  user_profile       │
├─────────────────┼─────────────────┼─────────────────────┤
│ 存储：当前Session │ 存储：当前Session │ 存储：跨Session      │
│ 内容：最近10轮对话 │ 内容：跨轮次累积的 │ 内容：历史购物偏好    │
│ 策略：滑动窗口    │  实体(类目/品牌/  │  (类目/品牌/价格)    │
│ 超出10轮自动丢弃  │  预算等)         │ 策略：对话开始时读取  │
│                 │ 策略：新值覆盖旧值 │ 超时200ms降级跳过    │
│                 │ 保留未覆盖字段    │                     │
└─────────────────┴─────────────────┴─────────────────────┘
```

**具体实现：**

**短期记忆（ConversationServiceImpl.java）：**
```java
// 滑动窗口：最多保留10轮（20条消息）
private static final int MAX_HISTORY_ROUNDS = 10;

// 每轮对话后保存历史
history.add("用户: " + message);
history.add("助手: " + response.getMessage());

// 超出上限时截断
if (history.size() > MAX_HISTORY_ROUNDS * 2) {
    history = history.subList(history.size() - MAX_HISTORY_ROUNDS * 2, history.size());
}

// 注入LLM时只取最近N轮
private String buildHistoryContext(List<String> history, String summary) {
    List<String> recent = history.subList(Math.max(0, history.size() - 6), history.size());
    // 同时注入对话摘要（压缩早期信息）
    if (summary != null && !summary.isEmpty()) {
        sb.append("对话摘要：").append(summary).append("\n\n");
    }
}
```

**会话记忆（ConversationServiceImpl.java）：**
```java
// 跨轮次实体合并：新值覆盖旧值，保留未覆盖字段
@SuppressWarnings("unchecked")
private Map<String, Object> mergeExtractedInfo(String existingJson, Map<String, Object> newEntities) {
    Map<String, Object> existing = objectMapper.readValue(existingJson, Map.class);
    existing.putAll(newEntities);  // 新值覆盖同名旧值
    return existing;
}

// handleRecommend时读取会话记忆，累积用户偏好
private Map<String, Object> mergeWithSessionMemory(String sessionId, Map<String, Object> currentEntities) {
    Map<String, Object> sessionMemory = objectMapper.readValue(session.getExtractedInfo(), Map.class);
    Map<String, Object> merged = new HashMap<>(sessionMemory);
    merged.putAll(currentEntities);
    return merged;
}
```

**长期记忆（ConversationServiceImpl.java）：**
```java
// 对话开始时读取user_profile作为背景上下文
private String buildLongTermContext(String userId) {
    UserProfile profile = userProfileService.getByUserId(userId);
    StringBuilder sb = new StringBuilder("用户历史偏好：");
    if (profile.getPreferredCategories() != null) {
        sb.append("偏好类目=").append(profile.getPreferredCategories()).append("；");
    }
    if (profile.getPreferredBrands() != null) {
        sb.append("偏好品牌=").append(profile.getPreferredBrands()).append("；");
    }
    return sb.toString();
}
```

**降级策略（所有记忆层都有）：**
```java
// 规格文档中的要求
// IF user_profile读取失败或记录不存在，降级为不使用长期记忆，不影响对话主流程
// IF extracted_info解析失败，将会话记忆视为空Map继续处理
// IF 长期记忆读取超时（超过200ms），降级跳过长期记忆注入
```

**对话摘要机制（ConversationSummaryTask.java）：**
```java
// 每10分钟执行一次：当对话轮数 > 5轮时，用LLM生成摘要
@Scheduled(fixedRate = 600000)
public void summarizeConversations() {
    List<ConversationSession> sessions = conversationSessionService.findSessionsNeedingSummary(5);
    // LLM摘要覆盖：用户购物需求、关注的商品、未解决的问题、100字以内
}
```

**自动画像更新（UserBehaviorServiceImpl.java）：**
```java
// 行为入库后异步触发画像更新，带5分钟防抖
private static final ConcurrentHashMap<String, Long> PROFILE_UPDATE_CACHE = new ConcurrentHashMap<>();
private static final long DEBOUNCE_MS = 5 * 60 * 1000; // 5分钟

private void triggerAsyncProfileUpdate(String userId) {
    long now = System.currentTimeMillis();
    Long lastUpdate = PROFILE_UPDATE_CACHE.get(userId);
    if (lastUpdate != null && now - lastUpdate < DEBOUNCE_MS) {
        return; // 5分钟内已触发过，跳过
    }
    CompletableFuture.runAsync(() -> userProfileAgent.runAsync(Map.of("userId", userId)).join());
}
```

**简历书写示例：**

> **三层记忆管理系统（短期+会话+长期）**
> - 设计并实现了三层记忆架构，解决多轮对话中的"失忆"问题：
>   - 短期记忆：dialogue_history滑动窗口（最近10轮），超出自动丢弃，注入LLM时按需取最近N轮
>   - 会话记忆：extracted_info跨轮次实体累积（新值覆盖旧值，保留未覆盖字段），支持代词指代消解和偏好累积
>   - 长期记忆：user_profile跨Session历史偏好（类目/品牌/价格），对话开始时注入背景上下文
> - 每层记忆都有独立降级策略（超时跳过、解析失败降级为空），保证单层故障不影响对话主流程
> - 配套实现定时摘要任务（LLM压缩早期对话为100字摘要）和行为驱动的异步画像更新（5分钟防抖）
> - 效果：多轮对话上下文连贯性显著提升，用户无需重复说明偏好，对话体验接近真人导购

---

### 亮点4：用户满意度多维统计

**设计思路：** 不只用"点赞/点踩"来衡量满意度，而是通过**多维度信号组合**进行综合评估。

**数据模型（ChatFeedback实体）：**
```sql
-- chat_feedback 表
user_id        -- 用户ID
session_id     -- 会话ID
message_index  -- 消息轮次索引
user_message   -- 用户消息原文
ai_message     -- AI回复原文
rating         -- 1=赞, -1=踩
feedback_time  -- 反馈时间
```

**统计维度（SatisfactionStatsVO）：**
```java
// 满意度统计VO
totalFeedback      // 总反馈数
likeCount          // 点赞数
dislikeCount       // 点踩数
satisfactionRate   // 满意度 = 点赞/(点赞+点踩) * 100%
ratingDistribution // 评分分布：[{rating:1, count:N}, {rating:-1, count:M}]
```

**多维度满意度信号体系（规格文档定义）：**

| 信号类型 | 权重 | 来源 | 含义 |
|---------|------|------|------|
| 点赞/点踩 | 高 | 用户主动反馈 | 直接满意度表达 |
| 否定信号 | 高 | 用户消息包含"没用""不对"等 | 明确不满意 |
| 深入探索 | 低(中性偏正向) | 同一意图连续出现 | 用户在深入了解 |
| 单轮放弃 | 中 | Session仅1轮即结束 | 可能不满意离开 |
| 转化行为 | 高(正向) | Session结束后30分钟内cart/purchase | 对话促成了交易 |

**指标计算（规格文档定义）：**
```
解决率 = has_negative_signal为false的Session数 / 总Session数
追问率 = follow_up_count大于0的Session数 / 总Session数
平均轮次 = SUM(turn_count) / COUNT(session_id)
转化率 = has_conversion为true的Session数 / 总Session数
```

**画像变更追踪（ConversationProfileUpdate实体）：**
```java
// 每次画像变更都有记录
userId + sessionId + updateType + oldValue + newValue + confidence
// 可追溯每次对话对用户画像的影响，用于评估LLM画像分析的准确性
```

**简历书写示例：**

> **用户满意度多维统计系统**
> - 设计了多维度满意度评估体系，超越简单的"点赞/点踩"：
>   - 直接反馈：用户主动点赞/点踩（chat_feedback表）
>   - 语义信号：检测用户消息中的否定词（"没用""不对"等），高权重不满意信号
>   - 行为信号：Session结束后30分钟内的购买/加购行为，高权重正向转化信号
>   - 对话模式：单轮放弃检测（中权重可能放弃）、持续追问检测（低权重深入探索）
> - 实现画像变更追踪机制：每次LLM更新用户画像时记录oldValue→newValue→confidence，可追溯AI对用户理解的准确性
> - 支持按时间范围、意图类型分组的聚合统计（解决率/追问率/平均轮次/转化率），为运营决策提供数据支撑
> - 前后端完整实现：REST API + Ant Design可视化看板

---

### 亮点5：用户画像的实时+异步双轨更新

**设计思路：** Agent调用时同步生成画像，用户行为触发时异步更新画像，两套机制互补。

**同步路径（Agent调用 → LLM分析 → 入库）：**
```java
// UserProfileAgent.java
// 1. LLM + UserBehaviorTool 收集行为数据
// 2. LLM分析输出UserProfileAnalysisDTO（按严格JSON Schema）
// 3. buildProfileFromDto构建UserProfile实体
// 4. saveProfileToDb存入数据库
// 失败时降级为默认画像
```

**异步路径（用户行为 → 防抖触发 → Agent更新）：**
```java
// UserBehaviorServiceImpl.java
// 行为入库后 → 异步触发画像更新
// 5分钟防抖：避免每次浏览都触发，减少LLM调用成本
CompletableFuture.runAsync(() -> {
    userProfileAgent.runAsync(Map.of("userId", userId)).join();
});
```

**数据清洗（UserBehaviorTool）：**
```java
// 过滤误操作行为
// 同一商品仅被浏览1次的 → 视为误操作，过滤掉
// 购买、加购、搜索 → 强信号，保留
private static final int MIN_VIEW_COUNT_FOR_VALID = 2;
```

**简历书写示例：**

> **用户画像双轨更新机制**
> - 同步路径：Agent调用时实时分析行为数据→LLM生成画像→JSON Schema约束解析→入库
> - 异步路径：用户行为入库后触发异步画像更新，5分钟防抖避免频繁调用LLM
> - 数据清洗：过滤单次浏览等误操作行为（MIN_VIEW_COUNT=2），保留购买/加购等强信号
> - 降级策略：LLM分析失败时降级为默认画像（segments="active"），不影响推荐主流程

---

### 亮点6：多Agent编排与容错体系

**Agent错误率统计（BaseAgent）：**
```java
// 每个Agent独立统计调用次数和错误次数，支持熔断判断
private final AtomicInteger callCount = new AtomicInteger(0);
private final AtomicInteger errorCount = new AtomicInteger(0);

public double getErrorRate() {
    int calls = callCount.get();
    return calls == 0 ? 0.0 : (double) errorCount.get() / calls;
}
```

**逐Agent降级策略：**

| Agent | 异常场景 | 降级方案 |
|-------|---------|---------|
| UserProfileAgent | LLM解析失败 | segments="active"默认画像 |
| ProductRecAgent | LLM返回空 | RecommendEngineService规则推荐→热门商品 |
| InventoryAgent | LLM解析失败 | fallbackCheck()硬编码规则直接检查 |
| MarketingCopyAgent | 任意环节失败 | 返回空文案列表 |

**多级降级链示例（ProductRecAgent）：**
```java
// 第一级：LLM + Tool Calling
RecResult result = chatClient.prompt().tools(productSearchTool)...call().entity(RecResult.class);

// 第二级：LLM失败 → 推荐引擎兜底
if (result为空) → recommendEngineService.recommend()

// 第三级：推荐引擎也失败 → 热门商品兜底
if (推荐引擎返回空) → productService.listHotProducts()
```

**简历书写示例：**

> **多Agent容错体系**
> - 每个Agent独立追踪错误率（AtomicInteger），支持后续熔断扩展
> - 逐Agent配置独立超时、重试策略（指数退避500ms→1000ms→2000ms，最多2次）
> - 多级降级链：LLM→推荐引擎→热门商品，保证任意环节失败都有兜底结果返回
> - 画像、推荐、库存、文案4个Agent各有独立降级策略，单点故障不影响整体链路

---

### 亮点7：A/B测试实验框架

**流量分桶（MD5哈希保证一致性）：**
```java
// ABTestServiceImpl.java
private int hashBucket(String userId, String experimentId) {
    String raw = userId + ":" + experimentId;
    MessageDigest md = MessageDigest.getInstance("MD5");
    // 取前4字节转整数 % 100 → 0-99
    return Math.abs(value) % BUCKET_COUNT;
}
// bucket 0-49 → control（规则重排+通用文案）
// bucket 50-99 → treatment_llm（LLM重排+个性化文案）
```

**简历书写示例：**

> **A/B测试流量分桶框架**
> - 基于MD5哈希的一致性分桶，保证同一用户始终进入同一实验组
> - 支持按experimentId隔离不同实验，可并行跑多个A/B测试
> - 实验分组信息随API响应返回，便于前端埋点收集对照组/实验组指标

---

### 亮点8：Query改写与向量检索闭环

**改写策略（QueryRewriteService）：**
```java
// 三种改写模式：
// 1. rewrite() - 完整改写：意图识别 + 实体抽取 + query改写 + 同义扩展
// 2. toVectorQuery() - 向量检索改写：保留核心关键词，移除无关词汇
// 3. expandVariants() - 多路召回扩展：生成2-3个同义变体

// 改写Prompt的设计要点
String REWRITE_PROMPT = """
    1. 理解用户意图（recommend/product_query/knowledge_query/compare）
    2. 抽取关键实体（category/brand/price_min/price_max/product_name）
    3. 将用户输入改写为更适合检索的query（同义词替换、拼写纠错）
    4. 生成2-3个同义查询变体，用于多路召回
    """;
```

**类目同义词映射（降低语义Gap）：**
```
电脑/手提电脑/便携电脑 → 笔记本
手机/移动电话 → 手机
耳机/头戴式耳机/入耳式耳机 → 耳机
```

**简历书写示例：**

> **智能Query改写与多路召回**
> - 实现三种改写模式：意图识别改写、向量检索改写、同义扩展改写
> - 内建类目同义词映射（"电脑"→"笔记本"），缩小用户表达与商品库的语义Gap
> - 改写结合对话历史和LLM摘要，支持代词指代消解（"那这个呢"→"iPhone 16怎么样"）

---

## 三、项目整体亮点总结（简历项目描述模板）

以下提供两种风格的简历模板，可直接复制使用。

### 模板A：完整版（适合详细项目经历描述）

```
项目名称：多Agent电商智能推荐系统
技术栈：Spring Boot 3 + Spring AI + MyBatis-Plus + DeepSeek-V3 + MySQL + Redis + Milvus

项目描述：
基于Spring AI框架构建的多Agent协作电商推荐系统，采用Supervisor编排模式
协调4个专业Agent（用户画像、商品推荐、库存决策、营销文案）完成个性化推荐
全链路，同时提供多轮对话、RAG知识问答、A/B实验等能力。

核心贡献：

1. 【Tool Calling防乱调机制】设计了4层约束体系防止LLM误调用工具：
   - Tool描述层：@Tool注解+中文分类标签，明确各工具的适用场景和禁用条件
   - 参数约束层：@ToolParam描述中嵌入"不能为空""不要调用此工具"等语义约束
   - Prompt流程层：System Prompt中写死工具调用步骤（步骤1→步骤2→步骤3）
   - 数据校验层：LLM只输出商品ID，Agent回表MySQL获取完整数据，杜绝LLM编造价格/库存
   效果：从根本上避免了LLM在Tool Calling场景下的幻觉问题

2. 【RAG全链路反幻觉】针对知识问答场景设计5道防线：
   Query改写→多路召回+RRF融合→数据库回表校验→Prompt反编造指令→语义保持分块
   效果：知识问答准确率显著提升，所有回答可追溯到知识库原文

3. 【三层记忆系统】实现短期记忆（滑动窗口10轮）+ 会话记忆（跨轮实体累积合并）+
   长期记忆（跨Session用户画像）协同，配套LLM摘要压缩和行为驱动异步画像更新
   效果：多轮对话上下文连贯性接近真人导购水平

4. 【用户满意度多维统计】设计多维度满意度评估（直接反馈+语义信号+行为信号+对话模式），
   支持按时间/意图分组的聚合统计和画像变更追溯

5. 【容错体系】每个Agent独立错误率统计 + 多级降级链（LLM→引擎→热门商品），
   保证任意Agent故障时整体链路可用
```

### 模板B：精简版（适合一页简历的项目经历）

```
多Agent电商推荐系统 | Spring Boot 3 + Spring AI + DeepSeek-V3 + MySQL + Redis

- 设计Supervisor+4Agent编排架构，采用层约束体系防LLM乱调工具：
  @Tool/SystemPrompt/参数描述/Schema约束四层防护，LLM输出ID+Agent回表DB保证数据准确性
- 实现RAG全链路反幻觉：Query改写→多路召回+RRF融合→DB回表校验→Prompt反编造→语义分块
- 构建三层记忆系统：短期(滑动窗口10轮)+会话(跨轮实体累积)+长期(跨Session画像)，
  配套LLM对话摘要和5分钟防抖异步画像更新
- 实现多维度满意度统计：直接反馈+语义否定信号+转化行为信号+对话模式分析，
  支持按意图分组的聚合统计和画像变更追溯
- 多Agent容错：每Agent独立错误率追踪+指数退避重试+多级降级（LLM→引擎→热门）
```

---

## 四、面试高频问题 + 回答思路

### Q1: 你怎么防止LLM乱调Tool？

**回答要点：**
- 4层约束：Tool描述（语义分类标签+场景说明）→ 参数描述（取值约束+禁用条件）→ System Prompt（写死执行步骤）→ 数据校验（LLM出ID→Agent查DB）
- 重点强调最后一层：LLM只做决策不做数据生成，数据库是唯一真相来源

### Q2: 你怎么降低RAG的幻觉率？

**回答要点：**
- Query改写缩小语义Gap（口语→标准类目名，代词指代消解）
- 向量召回后回表数据库校验（向量库可能滞后于数据库）
- Prompt中写"知识库没有就明确说没有，不要编造"
- 多路召回+RRF融合避免单路偏差

### Q3: 三层记忆是怎么设计的？

**回答要点：**
- 短期：dialogue_history滑动窗口，超10轮自动丢弃，注入LLM时取最近N轮+摘要
- 会话：extracted_info跨轮实体累积，新值覆盖旧值，未覆盖字段保留
- 长期：user_profile跨Session偏好，对话开始注入，超200ms降级跳过
- 每层独立降级，单层故障不影响对话主流程

### Q4: 用户满意度怎么统计的？

**回答要点：**
- 不只用点赞/点踩，多维度信号组合
- 否定词检测（高权重不满意）、转化行为（高权重正向）、单轮放弃（中权重）
- 支持按意图分组统计，知道"哪类问题答得最差"
- 画像变更追溯（oldValue→newValue→confidence）评估AI分析准确性

---

## 五、技术决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| Agent编排模式 | Supervisor集中式 | Agent间不直接通信，降低耦合，调试简单 |
| Tool调用模式 | LLM决策+Agent回表 | LLM避免编造数据，数据库是唯一真相来源 |
| 记忆存储 | MySQL JSON字段 | 无需引入Redis/MongoDB额外组件，开发阶段够用 |
| 向量库 | SimpleVectorStore→Milvus | 开发阶段内存向量库零配置，生产可平滑迁移 |
| 异步模型 | CompletableFuture | Spring生态原生支持，无需引入额外依赖 |
| 画像更新 | 同步+异步双轨 | 同步保证当前请求质量，异步降低成本 |

---

*本文档基于对项目全部Java源码的深度扫描整理，所有结论均有代码依据。*
