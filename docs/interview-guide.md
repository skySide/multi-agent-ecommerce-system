# 多Agent电商推荐系统 — 面试完全指南

> 基于 Spring AI + Spring Boot 3 生产级多Agent系统，覆盖：架构设计 · A2A通信 · 数据闭环调优 · LangChain/LangGraph对比 · 八股文40题 · STAR话术 · 代码讲解

---

## 一、简历项目经验（直接复制）

```
多Agent电商推荐与智能对话系统 | 个人项目 | 2026.01-2026.04
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 设计并实现Supervisor + ConversationAgent双编排模式的多Agent协同架构，
  含11个专业Agent，支持意图路由分发、A2A数据传递、并行/串行混合编排
• 基于Spring AI的Tool Calling机制实现Agent工具链调用（如营销文案Agent的
  "查商品→生成文案→合规过滤"三步工具链），支持生成中断与取消
• 构建三层记忆系统（短期/会话/长期），跨轮次实体累积合并，支持指代消解
  与多轮偏好继承，对话历史滑动窗口+定时摘要降低Token消耗
• 实现推荐引擎多路召回（向量40%+热销20%+新品20%+类目20%）+RRF融合
  +LLM精排+MMR多样性控制，向量召回后回表MySQL保证数据一致性
• 设计全链路质量数据闭环：用户点赞/点踩反馈→每日离线分析→
  Agent维度满意度/差评原因/质量事件聚合→质量看板，支撑模型调优决策
• 实现A/B测试引擎（MD5一致性哈希分桶+Thompson Sampling动态调优），
  支持Agent/模型/Prompt三层实验

技术栈：Java 17 · Spring Boot 3.2 · Spring AI 1.0 · MyBatis-Plus ·
        MySQL 8.0 · Redis · SimpleVectorStore · React · Ant Design
```

---

## 二、STAR法面试话术

### 完整版（3分钟自我介绍）

**S（Situation/背景）**

> 电商场景中传统推荐和客服系统存在三个核心痛点：
> 1. 推荐、营销、库存等模块各自为战，缺乏协同，推荐缺货商品率高达12%
> 2. 营销文案千篇一律，无法根据用户特征（新客/VIP/价格敏感）个性化生成
> 3. 缺乏系统性的质量数据闭环，Agent好不好用完全靠"感觉"，无法量化

**T（Task/任务）**

> 设计一个多Agent协同系统，让各专业Agent协作完成从"理解用户→推荐商品→校验库存→生成文案→收集反馈→质量分析→持续优化"的全链路智能化闭环。

**A（Action/行动）**

> 1. **双编排架构**：SupervisorOrchestrator负责批量推荐（UserProfileAgent→ProductRecAgent→InventoryAgent串行链路），ConversationAgent负责对话路由（LLM意图识别→6意图分发→子Agent执行），两个编排器覆盖不同业务场景。
>
> 2. **A2A通信机制**：Agent间通过AgentResult统一数据结构通信。Supervisor将上游Agent的输出（如画像）作为下游Agent的输入参数传递。ConversationAgent通过LinkedHashMap维护意图路由表，@PostConstruct时自动注册。
>
> 3. **多层降级网格**：BaseAgent内置指数退避重试（500ms→1s），全部失败返回success=false的AgentResult；Supervisor检查success标志决定兜底策略（画像失败→默认画像，推荐失败→热门商品，库存失败→不过滤）。
>
> 4. **三层记忆系统**：短期记忆（dialogue_history滑动窗口10轮）+会话记忆（extracted_info跨轮次实体合并）+长期记忆（user_profile表RFM标签），通过MemoryService统一管理，支持指代消解和偏好继承。
>
> 5. **质量数据闭环**：用户点赞/点踩→chat_feedback表记录（含差评原因：inaccurate/irrelevant/incomplete等）→RepeatedQuestionDetector异步检测重复提问→每日2:00 AgentQualityAnalysisTask离线汇总各Agent维度的满意度、差评原因Top5、异常事件数→支撑Prompt调优和模型选择决策。
>
> 6. **多工具调用链**：MarketingCopyAgent通过System Prompt定义四步工作流（getProductInfo查商品→按模板生成文案→filterSensitiveWords合规过滤→输出JSON），LLM在单次调用中按序调用多个Tool，实现复杂业务流程的自动化。

**R（Result/结果）**

> - 推荐缺货商品率从12%降至0.5%（库存Agent强校验）
> - 个性化文案点击率比通用文案高23%（5套模板×用户分群）
> - Agent异常自动降级，系统整体可用性99%+
> - 每日自动产出各Agent质量分析报告，支撑持续优化决策

### 精简版（1分钟项目介绍）

> 我做了一个多Agent电商系统，核心是双编排架构：SupervisorOrchestrator负责批量推荐流程（画像→推荐→库存），ConversationAgent负责对话路由（LLM意图识别→6个子Agent分发）。Agent间通过统一的AgentResult数据结构通信，Supervisor将上游输出作为下游输入传递。BaseAgent内置重试+降级机制，任一Agent失败自动兜底。三层记忆系统实现跨轮次偏好继承。完整的质量数据闭环（反馈收集→每日离线分析→Agent维度看板）支撑模型持续调优。

---

## 三、系统架构深度解析

### 3.1 整体分层架构

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
│  ProductController  UserController  QualityController         │
│  ChatFeedbackController  FavoriteController                   │
│                                                               │
│  编排层（两个编排器，覆盖不同场景）                              │
│  ┌─ SupervisorOrchestrator（批量推荐，串行编排）               │
│  │   UserProfileAgent → ProductRecAgent → InventoryAgent      │
│  └─ ConversationAgent（对话路由，意图分发）                    │
│      LLM意图识别 → 6意图路由 → 子Agent执行                     │
│                                                               │
│  Agent层（11个Agent，均继承BaseAgent）                         │
│  UserProfileAgent  ProductRecAgent  InventoryAgent            │
│  MarketingCopyAgent  RecommendIntentAgent                     │
│  ProductQueryIntentAgent  KnowledgeIntentAgent                │
│  CompareIntentAgent  ChitChatIntentAgent                      │
│  ConversationAgent（自身也是Agent）                            │
│                                                               │
│  Tool层（Spring AI @Tool注解，4个工具类）                     │
│  ProductSearchTool  InventoryTool                             │
│  SensitiveWordTool  UserBehaviorTool                          │
│                                                               │
│  服务层                                                       │
│  RecommendEngineService  MemoryService                        │
│  QueryRewriteService  RepeatedQuestionDetector                │
│  ABTestService  AgentQualityAnalysisService                   │
└──────────────────────────────────────────────────────────────┘
        │                   │                   │
   MySQL 8.0         SimpleVectorStore       LLM API
  (业务数据)         (内存向量库+知识库)    (SiliconFlow/DeepSeek-V3)
```

**与微服务的本质区别**：
- Agent 是 JVM 内方法调用（`CompletableFuture.runAsync`），无网络开销
- Agent 间不直接通信，数据通过编排器（Supervisor/ConversationAgent）中转
- Supervisor 是普通 Spring Service，不是独立部署单元
- 每个 Agent 可独立配置超时、降级策略，互不影响

### 3.2 双编排模式详解

系统采用**两个编排器**覆盖不同业务场景：

| 编排器 | 场景 | 编排方式 | Agent数量 | 典型延迟 |
|--------|------|----------|-----------|----------|
| **SupervisorOrchestrator** | 批量推荐（/api/v1/recommend） | 串行链式调用 | 3个（画像→推荐→库存） | 3-8s |
| **ConversationAgent** | 对话交互（/api/v1/conversation/chat） | LLM意图路由→子Agent | 6个（按意图分发） | 2-5s |

**SupervisorOrchestrator 串行链路**：

```java
// SupervisorOrchestrator.recommend()
// 步骤1: 获取用户画像
UserProfile profile = getUserProfile(userId, agentResults);
//   → UserProfileAgent.runAsync({"userId"}).join()

// 步骤2: 基于画像推荐商品（依赖步骤1的profile）
List<Product> products = recommendProducts(userId, profile, numItems, agentResults);
//   → ProductRecAgent.runAsync({"userId", "userProfile", "numItems"}).join()

// 步骤3: 库存校验（依赖步骤2的商品列表）
List<Product> finalProducts = checkInventory(products, numItems, agentResults);
//   → InventoryAgent.runAsync({"productIds": [...]}).join()
```

**ConversationAgent 意图路由**：

```java
// ConversationAgent.execute()
// 步骤1: LLM意图识别 → intent + entities
IntentRecognitionResult result = recognizeIntent(message, history, summary);

// 步骤2: 路由到子Agent（LinkedHashMap查找）
BaseAgent subAgent = intentRouter.get(intent);  // recommend→RecommendIntentAgent
AgentResult subResult = subAgent.runAsync(subParams).join();

// 步骤3: 更新三层记忆 + 异步质量检测
updateSessionMemory(...);
triggerAsyncDetection(...);  // 重复提问检测，异步不阻塞
```

### 3.3 Agent间A2A通信机制（核心）

Agent间通信是本系统的关键设计。Agent之间**不直接调用**，所有数据和决策通过编排器中转。

#### 3.3.1 A2A通信架构图

```
                    ┌─────────────────────────────────────┐
                    │      SupervisorOrchestrator           │
                    │      (中央编排器，Agent不直接通信)      │
                    │                                       │
                    │  Map<String, AgentResult> agentResults │
                    │  ├── "user_profile" → AgentResult     │
                    │  ├── "product_rec"  → AgentResult     │
                    │  └── "inventory"    → AgentResult     │
                    └──────┬──────────────┬──────────────┘
                           │              │
              ┌────────────┼──────────────┼────────────┐
              │            │              │            │
              ▼            ▼              ▼            ▼
        UserProfileAgent  ProductRecAgent  InventoryAgent
        输入: {userId}    输入: {userId,    输入: {productIds}
                             userProfile,     ← 来自上一步的输出
                             numItems}
        输出: data={        输出: data={      输出: data={
          profile: ...       products: [...]    available_products: [...]
        }                  }                   }
```

#### 3.3.2 核心数据结构：AgentResult

```java
@Data
@Builder
public class AgentResult {
    private String agentName;           // Agent标识，如 "product_rec"
    @Builder.Default
    private boolean success = true;     // 执行状态 —— 编排器据此决定是否降级
    @Builder.Default
    private double latencyMs = 0.0;     // 执行耗时
    private String error;               // 错误信息
    private Map<String, Object> data;   // 灵活数据载体，任意类型可放入
    @Builder.Default
    private double confidence = 1.0;    // 置信度（0.0~1.0），低置信度可降级
}
```

**设计要点**：
- `success` 是最关键的字段 —— 编排器不关心Agent内部异常，只看这个标志
- `data` 用 `Map<String, Object>` 而非泛型，给予各Agent最大灵活度（画像Agent放UserProfile，推荐Agent放List<Product>）
- `confidence` 用于置信度加权聚合，低置信度结果可被高置信度结果覆盖

#### 3.3.3 各Agent的输入输出契约

| Agent | 输入 (Map key) | 输出 (data中的key) | 使用的Tool |
|-------|---------------|-------------------|-----------|
| **UserProfileAgent** | `userId` | `profile` (UserProfile) | UserBehaviorTool |
| **ProductRecAgent** | `userId`, `userProfile`, `numItems`, `userQuery`(可选) | `products` (List\<Product\>), `candidate_count` | ProductSearchTool |
| **InventoryAgent** | `productIds` (List\<String\>) | `available_products`, `low_stock_alerts`, `purchase_limits` | InventoryTool |
| **MarketingCopyAgent** | `userProfile`, `productIds` 或 `products` | `copies` (List\<MarketingCopyVO\>), `template_used` | ProductSearchTool, SensitiveWordTool |
| **RecommendIntentAgent** | `userId`, `message`, `history`, `entities` | `reply`, `products`, `entities` | ProductSearchTool |
| **ProductQueryIntentAgent** | `userId`, `message`, `entities`, `history` | `reply`, `products` | 无（直接调用Service） |
| **KnowledgeIntentAgent** | `userId`, `message`, `history`, `summary` | `reply`, `sources` | 无（RAG检索+LLM） |
| **CompareIntentAgent** | `userId`, `message`, `entities`, `history` | `reply` | 无（调用RecommendIntentAgent） |
| **ChitChatIntentAgent** | `userId`, `message`, `history` | `reply` | 无（纯LLM对话） |

#### 3.3.4 数据传递方式一：Supervisor参数传递

上游Agent的输出直接作为下游Agent的输入参数，这是最直接的A2A通信：

```java
// SupervisorOrchestrator 中的跨Agent数据传递

// Step 1: 画像Agent的输出 → 提取为 UserProfile 对象
AgentResult profileResult = userProfileAgent.runAsync(Map.of("userId", userId)).join();
UserProfile profile = (UserProfile) profileResult.getData().get("profile");
//       ↑ 强类型提取，AgentResult.data 的灵活性体现在这里

// Step 2: 画像对象 → 作为推荐Agent的输入参数
AgentResult recResult = productRecAgent.runAsync(Map.of(
    "userId", userId,
    "userProfile", profile,    // ← 跨Agent数据传递：画像→推荐
    "numItems", numItems
)).join();
List<Product> products = (List<Product>) recResult.getData().get("products");

// Step 3: 商品列表 → 提取ID，作为库存Agent的输入
List<String> productIds = products.stream().map(Product::getProductId).toList();
AgentResult invResult = inventoryAgent.runAsync(Map.of(
    "productIds", productIds   // ← 跨Agent数据传递：推荐→库存
)).join();
```

#### 3.3.5 数据传递方式二：ConversationAgent共享上下文

在对话场景中，共享的 `Map<String, Object> params` 作为上下文在所有Agent间传递：

```java
// ConversationAgent.routeAndExecute()
Map<String, Object> subParams = new HashMap<>();
subParams.put("userId", userId);
subParams.put("message", message);
subParams.put("sessionId", sessionId);
subParams.put("history", history);     // 短期记忆
subParams.put("summary", summary);     // 对话摘要
subParams.put("entities", entities);   // 当前轮识别的实体

// 所有子Agent共享同一份上下文
// 子Agent返回的entities会被merge回去：
// mergeSubAgentEntities() → existing.putAll(newEntities)
// 示例：RecommendIntentAgent返回 {"recommended_product_ids": ["P001","P002"]}
//      下一轮用户说"比较这两个"，CompareIntentAgent读取 recommended_product_ids
```

#### 3.3.6 数据传递方式三：跨轮次会话记忆

Agent通过MySQL持久化的会话记忆实现**跨轮次**数据共享：

```
第1轮: 用户说"推荐手机"
  → RecommendIntentAgent返回 entities: {"category": "手机"}
  → ConversationAgent.mergeSubAgentEntities() 合并到 extracted_info
  → memoryService.saveHistory() 持久化 extracted_info = {"category": "手机"}

第2轮: 用户说"华为的"
  → ConversationAgent 读取 extracted_info = {"category": "手机"}
  → 合并当前实体 → {"category": "手机", "brand": "华为"}
  → RecommendIntentAgent 基于合并后的实体推荐

第3轮: 用户说"比较前两个"
  → CompareIntentAgent 读取 extracted_info 中的 recommended_product_ids
  → 调用 RecommendIntentAgent.resolveProductsForComparison() 定位商品
  → LLM生成对比分析
```

#### 3.3.7 错误隔离：Agent失败不传播

```java
// BaseAgent.runAsync() —— 所有Agent的错误处理统一在基类
public CompletableFuture<AgentResult> runAsync(Map<String, Object> params) {
    return CompletableFuture.supplyAsync(() -> {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                return execute(params);  // 子类实现
            } catch (Exception e) {
                attempt++;
                if (attempt < maxRetries) {
                    Thread.sleep((long) (500 * Math.pow(2, attempt - 1))); // 指数退避
                }
            }
        }
        return fallback(latencyMs, lastError);  // 返回 success=false，不抛异常
    });
}

// Supervisor 根据 success 决定是否使用兜底
if (!result.isSuccess() || profile == null) {
    log.warn("画像Agent失败，降级使用默认画像");
    profile = UserProfile.builder().userId(userId).segments("active").build();
}
```

**关键设计原则**：Agent内部异常在BaseAgent层被完全吞掉，编排器只看到 `success=true/false`。这让编排器的代码保持简洁——不需要try-catch，只检查布尔标志。

### 3.4 对话链路全流程

```
用户消息 POST /api/v1/conversation/chat
    │
    ▼
ConversationServiceImpl.chat()
    │
    ├── 1. 会话归属校验
    │   sessionId为null → createSession()
    │   userId != session.userId → createSession()（创建新会话，不拒绝请求）
    │
    ├── 2. 短期记忆读取（MemoryService）
    │   dialogue_history（JSON数组，最新10轮=20条消息）
    │   + summary（对话摘要，ConversationSummaryTask定时生成）
    │   → buildHistoryContext() = 摘要 + 最近6条历史
    │
    ├── 3. 长期记忆读取
    │   buildLongTermContext() → user_profile表
    │   → preferred_categories + preferred_brands + price_range
    │
    ├── 4. ConversationAgent.runAndTrack(sessionId, params)
    │   │
    │   ├── 4.1 意图识别（LLM）
    │   │   6种意图：recommend / product_query / knowledge_query
    │   │           / compare / chitchat / transfer_to_human
    │   │
    │   ├── 4.2 路由分发（intentRouter LinkedHashMap）
    │   │   recommend     → RecommendIntentAgent
    │   │   product_query → ProductQueryIntentAgent
    │   │   knowledge_query → KnowledgeIntentAgent
    │   │   compare       → CompareIntentAgent
    │   │   chitchat      → ChitChatIntentAgent
    │   │   transfer_to_human → ChitChatIntentAgent（同chitchat处理）
    │   │
    │   ├── 4.3 子Agent执行
    │   │   subAgent.runAsync(subParams).join()
    │   │
    │   ├── 4.4 合并entities + 更新三层记忆
    │   │   mergeSubAgentEntities() + saveHistory() + updateRoundIntents()
    │   │
    │   ├── 4.5 异步质量检测
    │   │   CompletableFuture.runAsync → RepeatedQuestionDetector.detect()
    │   │
    │   └── 4.6 转人工检测 + 行为记录
    │       checkTransferToHuman() + recordBehavior()
    │
    └── 5. 返回 AgentResult（含reply、products、intent等）
```

### 3.5 推荐引擎全链路

```
POST /api/v1/recommend
    │
    ▼
SupervisorOrchestrator.recommend()
    │
    ├── A/B分桶: MD5(userId:experimentId) % 100
    │   bucket < 50 → control（规则排序）
    │   bucket >= 50 → treatment_llm（LLM重排）
    │
    ├── 步骤1: UserProfileAgent
    │   └── LLM + UserBehaviorTool.collectUserBehavior
    │       → 分析用户行为 → 输出RFM/分群/偏好
    │       → saveOrUpdateProfile(user_profile表)
    │       → 失败降级: segments="active" 默认画像
    │
    ├── 步骤2: ProductRecAgent
    │   └── 主路: LLM + ProductSearchTool.recommendProducts()
    │       → 工具内部: 多路召回→RRF融合→规则排序→LLM调序→MMR
    │       → LLM从召回结果中选取最合适商品
    │       → 失败降级: RecommendEngineService规则推荐→热门商品兜底
    │
    └── 步骤3: InventoryAgent
        └── LLM + InventoryTool.queryProductStock
            → 逐个检查库存 → 过滤无货商品
            → 设置限购策略(stock≤50→限1件, hot+stock≤100→限2件)
            → 失败降级: fallbackCheck硬编码规则检查
```

**多路召回 + RRF融合算法**：

```
用户请求
    │
    ├── 向量召回 (权重0.4): SimpleVectorStore ANN检索 → [P1,P3,P5]
    ├── 热销召回 (权重0.2): MySQL销量排序 → [P1,P2,P4]
    ├── 新品召回 (权重0.2): MySQL新品筛选 → [P3,P6,P7]
    └── 类目召回 (权重0.2): MySQL类目匹配 → [P2,P5,P8]
            │
            ▼
    RRF融合: score(d) = Σ weight_i / (k + rank_i(d)), k=60
    优势: 只用排名位置，天然解决向量相似度和销量量纲不统一问题
            │
            ▼
    规则精排 (LLM调序，A/B实验)
            │
            ▼
    MMR多样性控制 (λ=0.5，平衡相关性与多样性)
```

---

## 四、核心Agent实现详解

### 4.1 BaseAgent —— 模板方法模式

```
BaseAgent（抽象基类）
├── 模板方法: runAsync()        ← 定义骨架（重试→超时→降级→指标）
│   ├── 指数退避重试 (500ms → 1s)
│   ├── 全部失败返回 success=false
│   └── 内置 callCount/errorCount 统计
│
├── 抽象方法: execute()         ← 子类实现具体业务逻辑
│
└── 钩子方法: fallback()        ← 子类可覆盖降级策略
```

```java
// 子类只需实现 execute()，稳定性保障由基类统一处理
@Component
public class InventoryAgent extends BaseAgent {
    public InventoryAgent() {
        super("inventory", 5.0, 2);  // 名称, 超时5s, 最多重试2次
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 只写业务逻辑，不关心重试/超时/降级
        List<String> productIds = (List<String>) params.get("productIds");
        // LLM + InventoryTool 调用...
        return AgentResult.builder().agentName(name).success(true).data(...).build();
    }
}
```

各Agent超时配置：

| Agent | 超时 | 重试次数 | 原因 |
|-------|------|----------|------|
| UserProfileAgent | 5s | 2 | 行为数据查询较快 |
| ProductRecAgent | 8s | 2 | 多路召回+LLM重排耗时较长 |
| InventoryAgent | 5s | 2 | 库存查询轻量 |
| MarketingCopyAgent | 10s | 2 | 多工具调用链（查N个商品+生成N条文案+合规过滤） |
| ConversationAgent | 15s | 2 | 意图识别+子Agent调用+记忆更新 |

### 4.2 ConversationAgent —— 意图路由中枢

**核心职责**：LLM意图识别 → 路由分发 → 记忆管理 → 质量检测

**意图路由表的构建**（@PostConstruct自动注册）：

```java
// 意图 → Agent名称 的静态映射
private static final Map<String, String> INTENT_AGENT_MAPPING = Map.of(
    "recommend", "recommend_intent",
    "product_query", "product_query_intent",
    "knowledge_query", "knowledge_intent",
    "compare", "compare_intent",
    "chitchat", "chitchat_intent",
    "transfer_to_human", "chitchat_intent"  // 转人工复用闲聊Agent
);

// Spring注入所有BaseAgent子类 → 按名称匹配 → 构建路由表
@PostConstruct
public void init() {
    for (BaseAgent agent : intentAgents) {
        for (Map.Entry<String, String> entry : INTENT_AGENT_MAPPING.entrySet()) {
            if (entry.getValue().equals(agent.name)) {
                intentRouter.put(entry.getKey(), agent);
            }
        }
    }
}
```

**生成中断与取消机制**：

```java
// 用户在前端点击"停止生成" → Controller调用cancelGeneration
private final Map<String, ConcurrentHashMap<String, CompletableFuture<AgentResult>>> runningFutures;

public boolean cancelGeneration(String sessionId) {
    CompletableFuture<AgentResult> future = runningFutures.remove(sessionId);
    if (future != null && !future.isDone()) {
        return future.cancel(true);  // 中断正在执行的LLM调用
    }
    return false;
}
```

### 4.3 MarketingCopyAgent —— 多工具调用链工作流（重点）

MarketingCopyAgent是本系统中**最复杂的单Agent工作流**，它通过在System Prompt中嵌入SOP（标准操作流程），让LLM在一次调用中按序执行多个Tool，实现"零代码编排"的业务流程。

#### 4.3.1 工作流设计思想

传统做法是写Java代码串联多个Tool调用：

```java
// 传统方式：Java代码写死流程
for (String productId : productIds) {
    Product product = productSearchTool.getProductInfo(productId);  // 步骤1
    String copy = generateCopy(product, template);                   // 步骤2
    copy = sensitiveWordTool.filterSensitiveWords(copy);            // 步骤3
}
```

**问题**：流程变更需要改代码、重新部署。而且LLM无法根据商品特性灵活调整文案风格。

**MarketingCopyAgent的做法**：将工作流以自然语言形式写入System Prompt，让LLM自主决策每一步的调用。

#### 4.3.2 四步工具调用链

```
System Prompt 中定义的工作流:
┌─────────────────────────────────────────────────────────┐
│ 步骤1: getProductInfo → 逐个查询每个商品的信息            │
│   输入: productId                                        │
│   输出: 商品完整信息（名称、价格、类目、品牌、描述）       │
│                                                         │
│ 步骤2: 生成营销文案 → 根据模板+商品信息，每个商品30-50字  │
│   模板选择: selectTemplate(profile)                      │
│   new_user     → "热情友好，突出新人优惠"                  │
│   high_value   → "品质尊享，突出品牌价值"                  │
│   price_sensitive → "突出性价比和促销优惠"                │
│   active       → "突出商品亮点和使用场景"                  │
│   churn_risk   → "突出专属折扣，挽留意图"                  │
│                                                         │
│ 步骤3: filterSensitiveWords → 广告法合规过滤              │
│   禁用词: 最好、第一、国家级、全球首、绝对、100%、永久、万能│
│   匹配到禁用词 → 替换为 "***"                            │
│                                                         │
│ 步骤4: 输出结构化JSON → {"copies":[...]}                 │
└─────────────────────────────────────────────────────────┘
```

#### 4.3.3 实际System Prompt

```
你是一个资深的电商营销文案撰写专家。请按以下步骤完成任务：

步骤1 - 查询商品信息：使用 getProductInfo 工具逐个查询每个商品的信息。
步骤2 - 生成营销文案：根据商品信息和{模板描述}，为每个商品生成一条营销文案。
  要求：必须为提供的每个商品ID生成一条文案，数量必须一致。每条文案30-50字。
  如果某个商品无法生成个性化文案，请根据商品名称、类目、价格等信息写一个简单描述作为默认文案。
步骤3 - 过滤敏感词：将生成的文案列表传给 filterSensitiveWords 工具进行广告法合规过滤。
步骤4 - 将过滤后的结果按以下JSON格式返回。

输出JSON格式示例：
{"copies":[{"productId":"P001","copy":"文案内容"},{"productId":"P002","copy":"文案内容"}]}
```

#### 4.3.4 模板选择策略

```java
// 优先级: new_user > high_value > churn_risk > price_sensitive > active(默认)
private String selectTemplate(UserProfile profile) {
    List<String> priority = List.of("new_user", "high_value", "churn_risk",
                                     "price_sensitive", "active");
    for (String seg : priority) {
        if (profile.getSegments().contains(seg)) {
            return seg;
        }
    }
    return "active";  // 兜底
}
```

#### 4.3.5 这种设计的好处

| 维度 | 硬编码流程 | System Prompt工作流 |
|------|-----------|-------------------|
| 灵活性 | 改流程需改代码+部署 | 改Prompt即可，无需部署 |
| LLM能力利用 | LLM只做单步推理 | LLM自主规划调用顺序 |
| 异常处理 | 需写大量if-else | LLM可自适应（如某商品无信息时自动写默认文案） |
| A/B测试 | 需不同代码分支 | 改Prompt template即可做Prompt层A/B |
| 成本 | 低（纯代码） | 中（多轮Tool调用消耗Token） |

**适用场景判断**：流程步骤≤5步、每步逻辑简单、需要LLM语义理解的场景适合System Prompt工作流；步骤多、逻辑复杂、对延迟敏感的场景应硬编码。

#### 4.3.6 其他Agent的Tool使用模式对比

| Agent | Tool数量 | 调用模式 | System Prompt策略 |
|-------|---------|----------|------------------|
| **MarketingCopyAgent** | 2个 | **顺序链式**：先A→再B→再C | 明确4步骤，必须按序执行 |
| **RecommendIntentAgent** | 5个（同一Tool类） | **按需选择**：LLM根据用户意图选工具 | 描述5个工具场景，LLM自主选择 |
| **ProductRecAgent** | 1个 | **单工具调用**：只调recommendProducts | 告知使用该工具获取推荐 |
| **InventoryAgent** | 1个 | **批量调用**：逐商品调queryProductStock | 要求逐个检查所有商品 |
| **UserProfileAgent** | 1个 | **单次调用**：collectUserBehavior | 告知使用该工具收集行为数据 |

### 4.4 ProductRecAgent —— 双层降级保障

```
ProductRecAgent.execute()
    │
    ├── 主路: LLM + ProductSearchTool.recommendProducts()
    │   → 工具内部: 推荐引擎全链路（多路召回+RRF+精排+MMR）
    │   → LLM从返回的List<Product>中选取合适商品
    │   → 输出 RecResult（含完整Product实体列表）
    │   → 成功 → 直接返回
    │   → LLM解析失败 → 降级
    │
    └── 降级: RecommendEngineService.recommend()
        → 规则推荐链路（多路召回+RRF+规则排序+MMR）
        → 返回为空 → 最终兜底: ProductService.listHotProducts()
```

### 4.5 CompareIntentAgent —— 跨Agent协作

比较意图需要先定位商品，再生成对比。定位商品的逻辑在RecommendIntentAgent中：

```
CompareIntentAgent.execute()
    │
    ├── 步骤1: 解析比较目标
    │   entities中提取 product_names / product_ids / indices / all
    │
    ├── 步骤2: 委托 RecommendIntentAgent 定位商品
    │   recommendIntentAgent.resolveProductsForComparison(params)
    │   → 处理"比较第1个和第2个"（indices=[1,2]→查extracted_info推荐记录）
    │   → 处理"iPhone和华为哪个好"（product_names→多工具搜索定位）
    │   → 处理"比较这几个"（all=true→全部推荐过的商品）
    │
    ├── 步骤3: 商品数量校验
    │   ≥2个 → 正常执行对比
    │   <2个 → 返回提示"需要至少2个商品才能对比"
    │
    └── 步骤4: LLM生成对比分析
        构建结构化Prompt → LLM生成Markdown对比表格
        → 含"商品对比表"+"分析建议"+"购买推荐"三部分
```

---

## 五、LangChain/LangGraph与Spring AI深度对比

虽然本项目基于Spring AI实现，但面试中经常被问到"为什么不用LangChain/LangGraph"，需要从架构层面理解各自的适用场景。

### 5.1 框架定位对比

| 维度 | LangChain/LangGraph (Python) | Spring AI (Java) |
|------|---------------------------|------------------|
| **生态定位** | AI应用快速原型开发 | 企业级AI能力集成 |
| **编程范式** | Chain/Graph DSL → 声明式 | ChatClient Builder → 链式API |
| **状态管理** | LangGraph StateGraph + Checkpointer | Spring Bean + MySQL/Redis |
| **并行模型** | asyncio.gather / Send API | CompletableFuture + 自定义线程池 |
| **工具调用** | @tool装饰器 + ToolNode | @Tool注解 + Spring AI自动处理 |
| **向量存储** | 50+ VectorStore集成 | SimpleVectorStore + Milvus/Redis/PGVector |
| **部署方式** | FastAPI + Uvicorn / LangServe | Spring Boot JAR / Docker |
| **学习曲线** | 中等（Python生态+DSL概念） | 低（Spring开发者零学习成本） |

### 5.2 如果用LangGraph重写本项目

```python
# LangGraph StateGraph 实现 Supervisor 模式
from langgraph.graph import StateGraph, END
from typing import TypedDict

class SupervisorState(TypedDict):
    user_id: str
    profile: UserProfile | None
    products: list[Product]
    final_products: list[Product]
    agent_results: dict[str, AgentResult]

def profile_node(state: SupervisorState) -> SupervisorState:
    """用户画像Agent节点"""
    profile = user_profile_agent.run(state["user_id"])
    return {"profile": profile}

def recommend_node(state: SupervisorState) -> SupervisorState:
    """商品推荐Agent节点"""
    products = product_rec_agent.run(state["user_id"], state["profile"])
    return {"products": products}

def inventory_node(state: SupervisorState) -> SupervisorState:
    """库存校验Agent节点"""
    final = inventory_agent.check(state["products"])
    return {"final_products": final}

# 构建DAG
graph = StateGraph(SupervisorState)
graph.add_node("profile", profile_node)
graph.add_node("recommend", recommend_node)
graph.add_node("inventory", inventory_node)

graph.add_edge("profile", "recommend")     # 串行: 画像→推荐→库存
graph.add_edge("recommend", "inventory")
graph.set_entry_point("profile")
graph.add_edge("inventory", END)

app = graph.compile(checkpointer=SqliteSaver(...))
```

### 5.3 选型决策树

```
你的团队技术栈是什么？
├── Python + AI背景 → LangGraph
│   优势: 丰富的AI生态、社区活跃、快速原型
│   劣势: 企业级特性需额外集成（监控、配置中心、服务发现）
│
├── Java + Spring生态 → Spring AI
│   优势: 开箱即用的企业级特性、Spring Security/Actuator、类型安全
│   劣势: AI生态不如Python丰富、社区较新
│
└── 混合团队 → 核心路径用Spring AI + 离线/训练用Python
    优势: 各取所长
    劣势: 维护两套技术栈
```

### 5.4 本项目选择Spring AI的原因

1. **零学习成本**：团队已有Spring Boot经验，ChatClient API与RestTemplate风格一致
2. **企业级特性**：Spring Security（认证授权）、Actuator（健康检查）、@Scheduled（定时任务）开箱即用
3. **类型安全**：BeanOutputConverter自动将LLM输出的JSON映射为POJO，编译期类型检查
4. **配置管理**：application.yml统一管理LLM配置（base-url、api-key、model、temperature），支持多环境profile
5. **Tool集成**：@Tool注解将现有Service方法直接暴露给LLM，无需额外适配层

---

## 六、数据闭环与模型调优策略

面试中高频问题："你的系统怎么持续优化？低质量数据怎么反馈到模型？"本节覆盖从数据采集到模型调优的完整闭环。

### 6.1 三层质量数据采集

```
┌─────────────────────────────────────────────────────────────┐
│                    数据采集层                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  第一层: 用户显式反馈 (chat_feedback 表)                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 点赞 (rating=1): helpful / saved_time / other        │    │
│  │ 点踩 (rating=-1): inaccurate / irrelevant /          │    │
│  │                    incomplete / too_generic / outdated│    │
│  │ 每个反馈带 sessionId + messageIndex → 可追溯到具体Agent│   │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  第二层: 系统自动检测 (session_quality_metrics 表)            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ repeated_question: 用户重复提问（LLM没解决用户问题）    │    │
│  │   → RepeatedQuestionDetector 三层检测:                │    │
│  │     ① 意图相同（当前轮与最近5轮比较）                   │    │
│  │     ② 实体重叠（category/brand/product_name）         │    │
│  │     ③ 文本相似度 > 0.85 (EmbeddingService余弦相似度)  │    │
│  │                                                      │    │
│  │ abrupt_end: 前端异常断开（用户中途离开）                │    │
│  │ transfer_to_human: 用户要求转人工（Agent未满足需求）    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  第三层: A/B实验数据 (recommend_cache + ABTestService)       │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 对照组(control): 规则排序                              │    │
│  │ 实验组(treatment_llm): LLM重排                        │    │
│  │ 对比指标: CTR、CVR、满意度                              │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 每日离线分析流程

```
每日 2:00 AM (AgentQualityAnalysisTask cron: "0 0 2 * * ?")
    │
    ▼
AgentQualityAnalysisService.runDailyAnalysis()
    │
    ├── 步骤1: 查询昨日有评分的 chat_feedback 记录
    │
    ├── 步骤2: 批量查询关联的 ConversationSession
    │   → resolveAgentByMessageIndex() 将反馈归属到具体Agent
    │
    ├── 步骤3: 按Agent维度汇总
    │   ├── 点赞数 / 点踩数 / 总反馈数
    │   ├── 满意度 = 点赞数 / 总数 × 100%
    │   └── 差评原因 Top-5（inaccurate/irrelevant/incomplete等）
    │
    ├── 步骤4: 查询昨日质量事件 (session_quality_metrics)
    │   → 归因到Agent：abrupt_end / repeated_question / transfer_to_human
    │
    ├── 步骤5: 统计各Agent的会话数和平均对话轮数
    │
    └── 步骤6: 写入 agent_quality_analysis 表
        (agent_name + analysis_date upsert)
```

### 6.3 质量看板API

```
GET /api/v1/quality/agent-stats?date=2026-05-16
→ 返回所有Agent在该日期的质量数据：
  {
    "agentName": "recommend_intent",
    "satisfactionRate": 87.5,
    "likeCount": 35, "dislikeCount": 5,
    "topDislikeReasons": [
      {"reason": "inaccurate", "count": 3},
      {"reason": "irrelevant", "count": 2}
    ],
    "abruptEndCount": 12,
    "repeatedQuestionCount": 8,
    "transferToHumanCount": 3,
    "totalSessions": 120,
    "avgRounds": 3.5
  }

GET /api/v1/quality/agent-stats/recommend_intent?days=30
→ 返回该Agent近30天的趋势数据（满意度/反馈数/异常事件趋势）

GET /api/v1/quality/overview?days=7
→ 全局质量概览（各Agent的满意度排名、总体满意度趋势）
```

### 6.4 基于质量数据的调优策略

这是面试中的**加分亮点**——展示你对"数据→分析→调优→验证"闭环的理解。

#### 6.4.1 Prompt调优（最轻量，最快见效）

```
触发条件: 某Agent的 inaccurate 差评占比 > 30%
    │
    ▼
分析差评样本 → 发现LLM输出格式不稳定导致解析失败
    │
    ▼
调优手段:
  1. System Prompt中添加Few-shot示例（1-2个完整JSON示例）
  2. 使用BeanOutputConverter自动注入JSON Schema → 约束LLM输出格式
  3. 增加输出校验规则（如"productId必须在候选集中"）
    │
    ▼
A/B验证: Prompt层实验组 vs 原有Prompt → 观察 inaccurate 差评率是否下降
```

**实际案例**：ProductRecAgent初期LLM输出非JSON格式，解析成功率仅85%。通过BeanOutputConverter注入JSON Schema + Few-shot示例 + 解析失败重试，成功率提升至99%。

#### 6.4.2 模型选择调优

```
触发条件: 某Agent的整体满意度持续 < 80%
    │
    ▼
调优手段:
  1. 简单任务（意图识别、库存检查）→ 用小模型（DeepSeek-V3-Lite），降成本
  2. 复杂任务（推荐、对比、文案）→ 用大模型（DeepSeek-V3），保质量
  3. 特定领域的Agent → 评估领域模型（如电商垂直模型）
    │
    ▼
A/B验证: 模型层实验组 vs 当前模型 → 对比满意度 + Token成本
```

#### 6.4.3 微调（Fine-tuning）策略（高阶）

当Prompt优化和模型选择无法满足需求时，考虑微调。本项目的质量数据结构天然支持微调数据集的构建。

```
微调数据集构建流程:
    │
    ├── 正样本构造:
    │   来源: chat_feedback 中 rating=1（点赞）的会话
    │   提取: 用户消息 + 对话历史 → LLM的原始输出
    │   标签: "好的回复"（用户认可的回答模式）
    │
    ├── 负样本构造:
    │   来源: chat_feedback 中 rating=-1 的会话
    │         + repeated_question 对应的对话轮次
    │         + transfer_to_human 前的对话轮次
    │   标签: "差的回复"（用户不满意的回答模式）
    │   用途: DPO（Direct Preference Optimization）训练
    │         让模型学会避免用户不满意的回答模式
    │
    └── 微调策略选择:
    
    ┌──────────────────────────────────────────────────────┐
    │ 策略1: SFT（Supervised Fine-Tuning）全量微调            │
    │   适用: 需要模型学习全新的回答风格或领域知识              │
    │   成本: 高（需要GPU资源，>1000条高质量样本）            │
    │   示例: 让模型学会电商合规文案的特定写法                  │
    │                                                       │
    │ 策略2: LoRA（Low-Rank Adaptation）轻量微调              │
    │   适用: 在基座模型上调整特定行为                         │
    │   成本: 中（显存需求低，>500条样本即可）                │
    │   示例: 调整意图识别准确率、实体提取精度                  │
    │                                                       │
    │ 策略3: DPO（Direct Preference Optimization）偏好对齐  │
    │   适用: 已有明确的"好/坏"回答对比数据                    │
    │   成本: 中（不需要 reward model）                      │
    │   示例: 基于点赞/点踩数据直接优化回答偏好                 │
    │                                                       │
    │ 策略4: RAG+Prompt优化（非微调方案，优先尝试）             │
    │   适用: 问题是"知识不足"而非"能力不足"                   │
    │   成本: 低（更新知识库文档即可）                         │
    │   示例: 更新退换货政策知识库文档                          │
    └──────────────────────────────────────────────────────┘
```

**面试话术**：

> "我们系统的质量数据闭环天然支持微调数据集的构建。点赞数据做正样本，点踩+重复提问+转人工做负样本，可以进行DPO偏好对齐训练。但目前优先走轻量路径：先看Prompt能不能修，再看模型要不要换，最后才考虑微调。因为微调成本高，而且基座模型本身在持续进化（如DeepSeek-V3每季度更新），微调模型的维护成本不容忽视。"

#### 6.4.4 调优优先级决策树

```
质量指标异常
    │
    ├── 差评原因=inaccurate/irrelevant → 先优化Prompt（最快）
    │   └── 无效 → 检查RAG知识库是否过时
    │       └── 无效 → 换更大模型
    │           └── 无效 → 构造微调数据，LoRA微调
    │
    ├── 差评原因=incomplete → 优化Prompt要求更全面的回答
    │   └── 无效 → 增加Tool返回的数据量
    │
    ├── 差评原因=too_generic → 增强个性化Prompt（注入更多用户画像信息）
    │   └── 无效 → SFT微调，学习个性化回答风格
    │
    ├── repeated_question高 → LLM没解决用户问题
    │   └── 检查: 是意图识别错误? 还是回答质量差?
    │       意图错 → 优化意图识别Prompt + 注入更多历史上下文
    │       回答差 → 走上面"差评原因"流程
    │
    └── transfer_to_human高 → Agent能力边界外的问题多
        └── 检查: 是知识库没覆盖? 还是需要新建意图?
            知识库问题 → 补充知识库文档
            新意图 → 新增意图类型 + 新建子Agent
```

---

## 七、八股文面试题（40题精编）

### Agent基础概念（Q1-Q5）

**Q1: 什么是AI Agent？和普通LLM调用有什么区别？**

| 维度 | 单次LLM调用 | AI Agent |
|------|-----------|---------|
| 决策 | 一次性输入→输出 | 多轮推理，动态决策 |
| 工具 | 不调用外部工具 | 可调用搜索/数据库/API等 |
| 记忆 | 无状态 | 有短期/长期记忆 |
| 自纠正 | 无 | 观察结果，自我修正 |

**Q2: 为什么选择Multi-Agent而不是单Agent？**

三个判断标准（来自Anthropic最佳实践）：
1. **上下文污染**：多个独立子任务信息量大，需要上下文隔离
2. **工具过载**：本项目20+工具方法，单Agent选择准确率会下降
3. **独立演进**：不同子任务需要不同的降级策略和超时配置

本项目中：用户画像/商品推荐/文案生成/库存查询/意图识别是领域独立的任务，每个Agent需要不同的工具集合和System Prompt，非常适合Multi-Agent。

**Q3: Supervisor模式 vs Handoffs模式？**

| 模式 | 控制方式 | 优点 | 缺点 | 本项目选择 |
|------|---------|------|------|-----------|
| Supervisor | 集中控制 | 流程清晰，易监控 | 单点瓶颈 | ✅ 推荐流程确定性高 |
| Handoffs | 去中心化 | 灵活，无单点 | 链路难追踪 | 不适合确定性流程 |

本项目选择Supervisor因为推荐流程是确定性的（画像→推荐→库存），Supervisor可以精确控制执行顺序和降级决策。

**Q4: ReAct模式是什么？本项目怎么用的？**

ReAct = Reason + Act：
```
Thought → Action → Observation → Thought → Action → ... → Final Answer
```

本项目中MarketingCopyAgent的Tool调用链就是典型的ReAct：
```
Thought: "需要先查询商品信息" → Action: getProductInfo("P001") → Observation: 获得商品信息
Thought: "生成文案" → Action: 生成文案 → Observation: 文案内容
Thought: "需要合规过滤" → Action: filterSensitiveWords → Observation: 过滤后文案
→ Final Answer: JSON输出
```

**Q5: 什么时候不该用Multi-Agent？**

1. 简单任务：单次LLM调用就能解决
2. 高耦合任务：子任务间强依赖共享状态
3. 低延迟要求：每个Agent增加通信开销
4. 成本敏感：每个Agent都消耗Token

原则：**默认用单Agent，遇到瓶颈再拆分。**

---

### A2A通信与编排（Q6-Q10）

**Q6: Agent之间怎么通信？数据怎么传递？**

本项目采用**编排器中转模式**，Agent之间不直接通信：

1. **Supervisor参数传递**：上游Agent的输出（如UserProfile）作为下游Agent的输入参数
2. **共享上下文传递**：ConversationAgent将params Map传递给所有子Agent，子Agent返回的entities被合并回上下文
3. **会话记忆传递**：跨轮次数据通过MySQL（extracted_info字段）持久化传递
4. **统一数据结构**：所有Agent返回AgentResult，编排器通过`data` Map提取结果

**Q7: 为什么Agent之间不直接通信？**

1. **循环依赖风险**：Agent A调用Agent B，B又间接依赖A → 循环调用
2. **职责不清晰**：Agent应该只关注自己的领域逻辑，不应该知道其他Agent的存在
3. **测试困难**：直接通信让单元测试变成集成测试
4. **统一降级**：编排器可以统一处理"某个Agent失败后是否继续"的决策

**Q8: 如何防止Agent乱调用其他Agent的工具？**

三层防护：

1. **注册隔离**：每个Agent只注册自己需要的Tool，如InventoryAgent只注册InventoryTool，LLM根本不知道ProductSearchTool的存在
2. **System Prompt约束**：明确告知"你可以使用queryProductStock工具"，不给LLM过多自由
3. **@Tool注解描述约束**：每个Tool的name和description精确描述用途和限制

**Q9: Agent调用的稳定性如何保证？**

四层防护网格：

```
第1层: BaseAgent指数退避重试（500ms → 1s，maxRetries=2）
  ↓ 全部重试失败
第2层: BaseAgent.fallback() → 返回 success=false 的 AgentResult
  ↓ 编排器检查 isSuccess()
第3层: 编排器降级决策（画像失败→默认画像，推荐失败→热门商品）
  ↓ 降级也失败
第4层: 全局兜底（热门商品列表、默认回复文案）
```

**Q10: Agent调用超时怎么处理？**

每个Agent构造时指定超时（如`super("inventory", 5.0, 2)`），BaseAgent的CompletableFuture结合Spring AI客户端超时共同保证。编排器通过`.join()`阻塞等待，但由于不同Agent超时不同，不会互相影响。

---

### Agent实现模式（Q11-Q15）

**Q11: BaseAgent用了什么设计模式？**

**模板方法模式**：`runAsync()`定义执行骨架（重试→超时→降级→指标），子类只需实现`execute()`。

```java
// 模板方法（父类定义骨架）
public CompletableFuture<AgentResult> runAsync(Map<String, Object> params) {
    // 1. 重试循环（maxRetries次）
    // 2. 指数退避等待（500ms × 2^(attempt-1)）
    // 3. 全部失败 → fallback()
}

// 抽象方法（子类实现业务逻辑）
protected abstract AgentResult execute(Map<String, Object> params) throws Exception;

// 钩子方法（子类可覆盖降级逻辑）
protected AgentResult fallback(double latencyMs, Exception e) { ... }
```

**Q12: MarketingCopyAgent的Tool调用链是怎么设计的？**

通过System Prompt嵌入SOP（标准操作流程），让LLM在一次调用中按序执行多个Tool：

```
步骤1: getProductInfo → 获取商品信息
步骤2: 生成文案 → 根据模板+商品信息生成
步骤3: filterSensitiveWords → 广告法合规过滤
步骤4: 输出JSON
```

优势：改流程只需改Prompt，不需要改代码。LLM可以自适应异常（如商品无详细信息时自动写默认文案）。

**Q13: ConversationAgent的意图路由是怎么实现的？**

```java
// 1. 静态映射：意图 → Agent名称
INTENT_AGENT_MAPPING = {"recommend" → "recommend_intent", ...}

// 2. @PostConstruct 自动注册：Agent名称 → Agent实例
for (BaseAgent agent : intentAgents) {
    intentRouter.put(agent对应的意图, agent);
}

// 3. 运行时路由：LLM识别意图 → 查表 → 调用
BaseAgent subAgent = intentRouter.get(intent);
AgentResult result = subAgent.runAsync(params).join();
```

**Q14: 如何保证Tool调用时JSON不被截断？**

这是Tool调用中最隐蔽的问题：
1. LLM调用Tool → Tool返回大量数据 → Spring AI序列化为JSON注入上下文
2. JSON过长 → 超过LLM上下文窗口 → 截断 → Jackson反序列化失败

解决方案：
1. 控制Tool返回数量（默认10条，topK限制）
2. 降级兜底：LLM解析失败 → fallbackRecommend()接管
3. Tool内精简输出，不返回冗余大字段

**Q15: Agent间如何做决策冲突处理？**

场景：推荐Agent推了高价商品，但库存Agent发现只剩5件。

解决：
1. **库存优先原则**：推荐结果必须经过库存Agent过滤
2. **置信度加权**：每个Agent结果附带confidence分数
3. **编排器仲裁**：优先级：库存安全 > 用户偏好 > 营销策略

---

### 记忆系统（Q16-Q19）

**Q16: 三层记忆分别存什么？**

| 记忆层 | 存储位置 | 容量 | 用途 |
|--------|---------|------|------|
| 短期记忆 | dialogue_history (JSON) + summary | 10轮滑动窗口 | 注入LLM上下文 |
| 会话记忆 | extracted_info (JSON) | 无限制 | 跨轮次实体累积 |
| 长期记忆 | user_profile 表 | 无限制 | 跨Session历史偏好 |

**Q17: 会话记忆如何累积？**

合并式更新：`existing.putAll(newEntities)`，新值覆盖旧值，旧字段保留。

```
第1轮: "推荐手机" → {category: "手机"}
第2轮: "预算5000" → {category: "手机", price_max: 5000}
第3轮: "华为的"   → {category: "手机", price_max: 5000, brand: "华为"}
```

**Q18: 如何保证多用户记忆隔离？**

三层隔离：
1. Session ID UUID v4随机生成，天然隔离
2. 入口校验：`userId != session.userId` → 创建新会话
3. 数据库层：所有查询带userId或sessionId过滤

**Q19: 对话摘要什么时候生成？**

ConversationSummaryTask定时任务（每10分钟检查），当对话历史超过5轮时，调用LLM生成摘要。摘要+最近6条历史注入后续Prompt，大幅降低Token消耗。

---

### 推荐引擎（Q20-Q24）

**Q20: RRF融合是什么？为什么不用加权平均？**

RRF公式：`score(d) = Σ weight_i / (k + rank_i(d)), k=60`

| 对比维度 | 加权平均 | RRF |
|----------|----------|-----|
| 量纲问题 | 向量相似度[0,1]，销量[0,10000]，量纲不统一 | 只用排名位置，天然无量纲 |
| 长尾商品 | 分数低则权重低，容易被忽略 | rank衰减平缓，长尾仍有机会 |

**Q21: 向量召回后为什么回表MySQL？**

SimpleVectorStore存储向量+商品快照，存在数据延迟（商品下架/价格调整/库存变化不会实时同步）。回表MySQL获取最新数据，避免推荐已下架商品。

回表后还需`reorderByIds()`重排——MySQL IN查询返回顺序不保证与向量相似度顺序一致。

**Q22: 四路召回各占什么权重？为什么这样分配？**

- 向量召回 0.4（语义相关性最重要）
- 热销召回 0.2（保证推荐质量底线）
- 新品召回 0.2（探索多样性）
- 类目召回 0.2（精确匹配用户意图）

**Q23: 如何防止多路召回中某路异常导致整体失败？**

每路召回单独try-catch，异常时返回空列表不中断其他通道。RRF融合时自动跳过空通道。

**Q24: LLM重排和传统排序各有什么优劣？**

| 维度 | 传统排序 (GBDT/DeepFM) | LLM重排 |
|------|----------------------|---------|
| 冷启动 | 需要大量标注数据 | 零样本即可工作 |
| 语义理解 | 依赖特征工程 | 天然语义理解 |
| 可解释性 | 黑盒 | 可输出排序理由 |
| 延迟 | ~10ms | ~1-2s |
| 成本 | GPU推理 | Token费用 |

本项目用A/B测试对比两种策略的实际效果。

---

### 质量数据闭环（Q25-Q28）

**Q25: 怎么知道Agent回答得好不好？**

三层数据：
1. **显式反馈**：用户点赞/点踩（chat_feedback表，含具体差评原因）
2. **隐式信号**：重复提问（问题没解决）、突然离开（体验差）、转人工（超出能力）
3. **A/B实验**：对照组vs实验组的CTR/CVR差异

**Q26: 重复提问怎么检测？**

RepeatedQuestionDetector三层检测（异步，不阻塞主流程）：
1. 意图相同：当前轮意图与最近5轮比较
2. 实体重叠：category/brand/product_name有交集
3. 文本相似度 > 0.85：EmbeddingService计算余弦相似度

三层都匹配 → 记录repeated_question质量事件。

**Q27: 每日质量分析做了什么？**

2:00 AM定时任务：
1. 汇总昨日所有Agent的赞/踩/差评原因Top5
2. 统计质量事件（abrupt_end/repeated_question/transfer_to_human）
3. 计算各Agent的会话数、平均轮数
4. 写入agent_quality_analysis表
5. 通过QualityController API暴露给质量看板

**Q28: 有了质量数据后怎么调优？**

优先级：Prompt优化 → 模型选择 → RAG知识补充 → LoRA微调 → 全量微调

原则：
- 先看Prompt能不能修（成本最低）
- 再看模型要不要换（中成本）
- 最后考虑微调（高成本，且基座模型在持续升级）

---

### 工程化与性能（Q29-Q35）

**Q29: 如何做A/B测试？**

```
MD5(userId + experimentId) % 100 → bucket (0-99)
bucket < 50  → control（规则排序）
bucket >= 50 → treatment_llm（LLM重排）

Thompson Sampling: Beta分布动态调整流量分配
每组维护(successes, failures) → 采样 → 选期望收益最高的组
自动将更多流量倾斜到效果好的策略
```

**Q30: LLM失败怎么办？**

以ProductRecAgent为例的双层保障：
```
主路: LLM + Tool → 输出RecResult → products不为空→返回
                                    → 为空→降级
降级: RecommendEngineService.recommend()
      → 多路召回+RRF+规则排序+MMR → 有结果→返回
                                   → 为空→热门商品兜底
```

**Q31: Token成本怎么控制？**

1. 分层调用：召回用传统方法（零Token），只在精排阶段用LLM（候选集<50）
2. 历史压缩：摘要+最近6条，不传全部历史
3. 模型分层：简单任务小模型（意图识别），复杂任务大模型（推荐/对比）

**Q32: 生成中断怎么实现？**

ConversationAgent维护`ConcurrentHashMap<String, CompletableFuture<AgentResult>>`，用户点击"停止"→Controller调用`cancelGeneration(sessionId)`→`future.cancel(true)`中断LLM调用。

**Q33: 如何评估Query改写的效果？**

A/B测试：
- 对照组：不使用改写，直接用原始query
- 实验组：使用LLM改写

指标：召回覆盖率（有结果的query比例）、用户满意度

**Q34: 如何扩展新Agent？**

1. 继承BaseAgent，实现`execute()`方法
2. 定义输入输出契约（Map参数 + AgentResult.data）
3. 实现Tool（如需要），在Agent构造函数或@PostConstruct中注入
4. 在编排器中注入新Agent，编排链路中调用（ConversationAgent需更新INTENT_AGENT_MAPPING）

遵循**开闭原则**：新增Agent不修改BaseAgent和现有Agent。

**Q35: 部署架构是怎样的？**

```
当前: Spring Boot单体JAR + Docker Compose(MySQL + Redis)
生产建议:
  ┌─ K8s集群 ──────────────────────────────────────┐
  │  api-gateway (Nginx)                            │
  │    ├── conversation-service (多副本)             │
  │    ├── recommendation-service (多副本)           │
  │    └── quality-analysis (单副本，定时任务)       │
  │                                                 │
  │  基础设施:                                       │
  │  MySQL 8.0 (主从) · Redis Cluster               │
  │  Milvus/PGVector (持久化向量库)                  │
  │  Prometheus + Grafana (监控告警)                 │
  └─────────────────────────────────────────────────┘
```

---

### 技术对比（Q36-Q40）

**Q36: Spring AI vs LangChain4j？**

| 维度 | Spring AI | LangChain4j |
|------|-----------|-------------|
| 生态集成 | Spring Boot原生，自动配置 | 需手动集成 |
| 向量存储 | 内置Milvus/Redis/PGVector | 需额外适配 |
| 企业级特性 | Security/Actuator开箱即用 | 需自行集成 |
| 社区成熟度 | 较新（2024年GA） | 更早，社区更大 |

**Q37: CompletableFuture vs asyncio？**

本项目使用CompletableFuture但当前采用串行编排（`.join()`阻塞等待）。设计上支持并行：

```java
// 串行（当前实现）
AgentResult r1 = agentA.runAsync(p1).join();
AgentResult r2 = agentB.runAsync(p2).join();

// 可轻松改为并行
CompletableFuture<AgentResult> f1 = agentA.runAsync(p1);
CompletableFuture<AgentResult> f2 = agentB.runAsync(p2);
AgentResult r1 = f1.join();  // 两者并行执行
AgentResult r2 = f2.join();
```

**Q38: SimpleVectorStore vs Milvus？**

| 维度 | SimpleVectorStore | Milvus |
|------|------------------|--------|
| 存储 | JVM内存 | 持久化磁盘 |
| 重启 | 数据丢失，需全量同步 | 数据持久 |
| 性能 | 内存级，极快 | 近内存级 |
| 适用 | 开发/演示 | 生产环境 |

当前项目使用SimpleVectorStore，启动时SystemBootstrap全量同步数据。生产环境应替换为Milvus/PGVector/Redis。

**Q39: 这个项目最大的技术挑战是什么？**

> "最大挑战是Tool调用时的上下文窗口截断问题。LLM调用Tool后，Tool返回的数据（如10个商品的完整信息）被序列化为JSON注入LLM上下文。当数据量大时，JSON可能超过上下文窗口被截断，导致Jackson反序列化失败。
>
> 我的解决方案：1) 控制Tool返回数量（默认10条）；2) 精简返回字段；3) 解析失败走降级链路（规则推荐→热门商品），保证用户始终能看到结果。
>
> 另一个挑战是LLM输出不稳定，通过BeanOutputConverter注入JSON Schema + Few-shot示例 + 失败重试，将成功率从85%提升至99%。"

**Q40: 如果重新设计这个系统，你会做什么不同？**

> "1. 引入消息队列解耦：将Agent调用改为异步事件驱动（如Kafka/RabbitMQ），支持削峰和重放
> 2. Agent并行化：当前Supervisor是串行的，画像和召回无依赖可以并行执行
> 3. 引入LangFuse/MLflow做LLM调用的全链路追踪（Prompt版本、Token消耗、延迟分布）
> 4. 质量数据闭环接入自动化：当某Agent满意度连续3天下降时自动告警+建议调优方向
> 5. 向量库换Milvus，解决重启数据丢失问题"

---

## 八、面试追问应对

### "这个项目有实际上线吗？"

> "这是一个完整的面试项目，但架构设计参考了NVIDIA Retail Agentic Commerce（企业级参考实现）和京东商家智能助手的设计。核心技术（ReAct/Supervisor/三层记忆/质量数据闭环/A/B Testing）都是生产级方案。"

### "为什么不直接用RAG？"

> "RAG解决的是知识检索问题，本项目解决的是多任务协同编排问题。不过在KnowledgeIntentAgent内部，知识问答用了类RAG的pipeline：Query改写→向量检索→注入Prompt→LLM基于文档回答。另外商品推荐Agent中的向量召回也是类RAG的检索增强思路。"

### "Token成本怎么控制？"

> "四个策略：
> 1. 分层调用：召回用传统方法（零Token），只在精排用LLM
> 2. 历史压缩：对话摘要+最近6条，滑动窗口10轮，不传全量历史
> 3. 模型分层：简单任务用小模型（意图识别），复杂任务用大模型
> 4. 缓存：相同用户短时间重复请求复用画像和推荐结果"

### "Agent调用失败了用户看到什么？"

> "用户不会看到报错。画像失败→使用默认画像（active用户），推荐失败→返回热门商品，库存失败→不进行库存过滤。所有降级都在BaseAgent和编排器层静默处理，用户始终能看到合理的推荐结果。"

### "如果有恶意用户刷差评，质量数据不就失真了？"

> "目前通过sessionId+userId关联+messageIndex定位具体对话轮次，可以在分析时过滤异常模式（如同一用户短时间内大量差评）。生产环境可加入行为异常检测（如统计每个用户的差评率，超出3σ标记为异常）。"

---

## 九、面试前准备清单

- [ ] 能画出完整架构图（Controller→双编排器→11个Agent→4个Tool→3层数据）
- [ ] 能说清楚为什么用Multi-Agent（工具过载、上下文污染、独立演进）
- [ ] 能解释A2A通信的三种方式（Supervisor参数传递、共享上下文、会话记忆）
- [ ] 能画出MarketingCopyAgent的四步工具调用链
- [ ] 能解释BaseAgent的模板方法模式（runAsync骨架 + execute子类实现）
- [ ] 能说出三层记忆的存储位置和合并策略
- [ ] 能解释RRF融合公式和k值含义
- [ ] 能说出质量数据闭环的完整链路（采集→分析→看板→调优）
- [ ] 能讲清楚微调策略（SFT/LoRA/DPO）各适用什么场景
- [ ] 能对比LangGraph和Spring AI的适用场景
- [ ] 能说出Spring AI的Tool Calling机制原理
- [ ] 能解释BeanOutputConverter如何保证LLM输出格式
- [ ] 能解释为什么Agent不直接通信（循环依赖、职责不清、测试困难）
- [ ] 能说出多路召回的4个通道和权重分配
- [ ] 能说清楚向量回表MySQL的原因（数据一致性+reorderByIds）
- [ ] 能解释A/B测试的MD5哈希分桶+Thompson Sampling
- [ ] 能说出4层降级网格的具体策略
- [ ] 能对比SimpleVectorStore和Milvus的差异
- [ ] 准备了"最大挑战"的回答（上下文截断+LLM输出不稳定）
- [ ] 准备了"重新设计会怎么做"的回答（消息队列+并行+全链路追踪）
