# 项目经历 — 多Agent电商推荐系统

> 针对2年经验后端工程师岗位优化

---

## 项目基本信息

```
项目名称: 多Agent电商智能推荐与对话系统
项目角色: 核心开发
项目周期: 2026.01 - 2026.05
技术栈: Spring Boot 3.2 · Spring AI 1.0 · MyBatis-Plus · Redis · MySQL · SimpleVectorStore · React
```

---

## 项目描述

基于Supervisor模式的多Agent协同架构，实现电商场景下的个性化商品推荐、智能对话和营销文案生成。系统包含4个专业Agent（用户画像、商品推荐、营销文案、库存决策），支持多轮对话式推荐、向量检索、A/B测试等核心功能。

---

## 核心职责与技术亮点

### 1. 多Agent架构设计与A2A通信机制

**架构设计**
- 设计并实现Supervisor模式的多Agent编排架构，包含UserProfileAgent、ProductRecAgent、MarketingCopyAgent、InventoryAgent四个专业Agent
- 采用串行编排流程：用户画像 → 商品推荐 → 库存校验 → 结果聚合，端到端延迟P99<2s
- BaseAgent抽象类封装统一的重试（指数退避）、超时控制、降级回退和熔断机制，Agent调用成功率从85%提升至99%

**A2A通信机制（Agent-to-Agent）**
- **工具调用链模式**: Agent通过Spring AI Tool机制实现A2A通信，每个Agent独立定义Tool工具类
  - UserProfileAgent → UserBehaviorTool（收集用户行为数据）
  - ProductRecAgent → ProductSearchTool（商品检索与推荐）
  - MarketingCopyAgent → ProductSearchTool + SensitiveWordTool（商品信息查询 + 敏感词过滤）
  - InventoryAgent → InventoryTool（库存实时查询）
- **数据传递方式**: Agent间通过`Map<String, Object> params`上下文传递参数，支持UserProfiles、商品ID列表、查询条件等结构化数据
- **编排器协调**: SupervisorOrchestrator通过`CompletableFuture`实现Agent并行执行，协调多个Agent的结果聚合
- **降级策略**: 当Agent调用失败时，编排器自动降级到默认逻辑（如推荐Agent失败降级为热门商品）

### 2. 推荐引擎与多路召回策略

**多路召回架构**
- 设计四路召回策略：向量召回（SimpleVectorStore）、热门召回、新品召回、类目召回，召回系数3倍冗余
- 实现RRF（Reciprocal Rank Fusion）融合算法，按通道权重（向量40%、热门20%、新品20%、类目20%）加权排序
- Query改写服务：基于LLM对用户查询进行同义词扩展、意图识别、实体抽取，提升召回精准度

**精排与多样性控制**
- 规则打分 + LLM重排的双阶段排序，综合考虑销量、评分、用户画像匹配度
- MMR算法控制推荐多样性，避免同类目商品堆叠，Lambda=0.5平衡相关性与多样性

### 3. 对话系统与RAG知识库

**多轮对话能力**
- 实现意图识别（推荐/商品查询/知识问答/对比/闲聊）+ 槽位填充的对话管理
- 滑动窗口管理对话历史（最大10轮），支持对话摘要压缩，降低Token消耗
- Query改写结合历史上下文，解决省略、指代消解问题，降低幻觉率

**RAG知识库集成**
- DocumentVectorService实现文档分块、Embedding和SimpleVectorStore存储
- 向量检索结合数据库回表，保证数据实时性（向量库异步同步，数据库实时查询）
- 知识库支持FAQ、售后政策、活动规则等结构化问答

### 4. 用户画像与实时特征工程

**RFM模型与分群**
- UserProfileAgent通过LLM分析用户行为数据，输出RFM评分、偏好类目、价格区间、用户分群（新用户/活跃/高价值/价格敏感/流失风险）
- UserBehaviorTool自动过滤误操作行为（如单次浏览），保证画像质量

**实时特征计算**
- Redis Sorted Set实现滑动窗口行为统计（1h/24h/7d多时间窗口）
- 用户行为实时记录，特征更新延迟<100ms

### 5. A/B测试与实验平台

- 流量分桶：MD5哈希分桶，100个桶位，50/50流量分配
- Thompson Sampling动态流量调整，实验周期较传统A/B缩短50%
- 支持Agent策略、Prompt模板、召回策略三层实验正交

### 6. 工程化与代码规范

**Spring AI集成**
- ChatClient统一LLM调用，支持SiliconFlow/MiniMax/OpenAI兼容API
- Tool注解实现Function Calling，自动处理参数映射和结果解析
- BeanOutputConverter实现JSON→POJO自动映射，简化结构化输出

**代码规范**
- 严格遵循日志规范（类名.方法名格式，error打印完整堆栈）
- 统一使用@Resource注入，Objects.isNull/StringUtils.isBlank判空
- Result<T>统一返回格式，DTO/VO分层设计
- 线程池完整7参数配置，自定义线程名前缀

**数据层设计**
- MyBatis-Plus + MySQL业务数据存储，支持动态SQL和逻辑删除
- Redis实时特征缓存，Lettuce连接池优化
- 业务ID（productId/userId）VARCHAR(32)设计，支持分布式ID生成

---

## 核心成果

| 指标 | 数值 |
|------|------|
| 推荐CTR提升 | 15% |
| Agent调用成功率 | 99% |
| 端到端延迟P99 | <2s |
| 库存缺货推荐率 | 从12%降至0.5% |
| 营销文案合规率 | 100% |

---

## 技术难点与解决方案

### 难点1: Agent间数据传递与协同

**问题**: 多Agent系统中，Agent间如何高效传递上下文数据？如何保证数据一致性？

**解决方案**:
- 采用共享上下文Map模式，SupervisorOrchestrator构建context传递给各Agent
- Agent通过Tool机制获取外部数据，避免直接依赖Service层导致的职责混乱
- Tool独立定义在tool包，通过@Resource注入Service，Agent通过Spring AI调用Tool
- 降级策略保证单个Agent失败不影响整体流程

### 难点2: 向量召回与数据库数据不一致

**问题**: SimpleVectorStore内存向量库与MySQL数据库数据同步延迟，导致召回结果过期

**解决方案**:
- 向量召回后，使用productId回表MySQL查询最新数据
- 按向量相似度顺序重排数据库结果，保证召回质量
- 异步同步机制，向量库定时同步数据库变更

### 难点3: LLM调用不稳定

**问题**: LLM调用存在超时、响应格式错误、网络抖动等问题

**解决方案**:
- BaseAgent封装重试机制（指数退避，最多3次）
- 超时控制（Agent级别配置，默认5-10秒）
- 降级策略：推荐Agent降级为热门商品，画像Agent降级为默认画像
- 错误率统计支持熔断判断

### 难点4: 对话历史Token消耗

**问题**: 多轮对话历史累积导致Token消耗快速增长，成本升高

**解决方案**:
- 滑动窗口限制历史轮数（最多10轮）
- 超过阈值触发LLM摘要生成，压缩历史为摘要
- Query改写结合摘要和最近历史，避免完整历史传入

---

## 项目架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (React)                         │
│                   对话界面 / 商品详情 / 用户中心                    │
└─────────────────────────────────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot 3.2 Backend                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ Conversation│  │Recommendation│  │  Knowledge  │              │
│  │  Controller │  │  Controller  │  │  Controller │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
│         ↓                 ↓                 ↓                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              SupervisorOrchestrator                      │    │
│  │         (Agent编排器 - 串行编排 + 降级)                    │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ UserProfileAgent │→ │ ProductRecAgent  │                    │
│  │  (用户画像Agent)   │  │  (推荐Agent)      │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                              ↓                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ MarketingCopyAgent│→ │ InventoryAgent   │                    │
│  │   (营销文案Agent)  │  │  (库存决策Agent)   │                    │
│  └──────────────────┘  └──────────────────┘                    │
│         ↓                                                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   Tool Layer (A2A通信)                   │    │
│  │  UserBehaviorTool | ProductSearchTool | InventoryTool   │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓                                                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                     Service Layer                        │    │
│  │  RecommendEngine | Conversation | DocumentVector | ABTest│    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────┐
│                       Data Layer                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐            │
│  │  MySQL  │  │  Redis  │  │SimpleVector│ │SiliconFlow│           │
│  │ 业务数据 │  │ 实时特征 │  │  向量存储  │  │   LLM API  │           │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

---

## A2A通信流程示例

### 推荐场景Agent协作流程

```
用户请求: "推荐一款适合学生的笔记本电脑"
        ↓
┌───────────────────────────────────────────────────────────┐
│ SupervisorOrchestrator                                    │
│                                                           │
│ Step 1: UserProfileAgent.runAsync()                       │
│   ├─ 调用 UserBehaviorTool.collectUserBehavior(userId)    │
│   ├─ LLM分析行为数据，输出用户画像                          │
│   └─ 返回: UserProfile(segments=price_sensitive,          │
│                       categories=笔记本, priceMax=6000)   │
│                                                           │
│ Step 2: ProductRecAgent.runAsync()                        │
│   ├─ 调用 ProductSearchTool.recommendProducts(...)        │
│   ├─ 推荐引擎四路召回 + RRF融合 + LLM重排                   │
│   └─ 返回: List<Product> (6件商品)                        │
│                                                           │
│ Step 3: InventoryAgent.runAsync()                         │
│   ├─ 调用 InventoryTool.queryProductStock(...)            │
│   ├─ 过滤无货商品，计算限购数量                             │
│   └─ 返回: List<String> availableProductIds               │
│                                                           │
│ 聚合结果 → RecommendationResponse                          │
└───────────────────────────────────────────────────────────┘
```

### 营销文案生成Agent工具链

```
MarketingCopyAgent执行流程:
┌───────────────────────────────────────────────────────────┐
│ 1. LLM决策 → 调用 ProductSearchTool.getProductInfo(P001)  │
│    返回: Product(名称=iPhone 16, 价格=7999, 品牌=Apple)   │
│                                                           │
│ 2. LLM生成文案 → "这款iPhone 16性能最好，全球第一..."      │
│                                                           │
│ 3. LLM调用 SensitiveWordTool.filterSensitiveWords(...)    │
│    返回: "这款iPhone 16性能***，全球***..."               │
│                                                           │
│ 4. 输出最终文案                                           │
└───────────────────────────────────────────────────────────┘
```

---

## 关键技术点总结

| 技术领域 | 技术点 | 应用场景 |
|---------|--------|---------|
| 多Agent架构 | Supervisor模式、Tool机制、A2A通信 | Agent编排、数据传递、降级策略 |
| 推荐系统 | 多路召回、RRF融合、LLM重排、MMR多样性 | 商品推荐、搜索排序 |
| 对话系统 | 意图识别、槽位填充、RAG、Query改写 | 智能客服、对话式推荐 |
| 用户画像 | RFM模型、实时特征、行为分析 | 个性化推荐、用户分群 |
| LLM集成 | Spring AI、Function Calling、Prompt模板 | 文案生成、智能问答 |
| 向量存储 | SimpleVectorStore、Embedding（BAAI/bge-large-zh-v1.5，768维） | 语义检索、RAG知识库 |
| 工程化 | 重试/超时/降级、线程池、代码规范 | 系统稳定性、可维护性 |

---

## 个人成长与收获

1. **架构设计能力**: 掌握多Agent系统设计模式，理解Agent间通信、编排、降级等核心问题
2. **LLM应用经验**: 深入理解Spring AI框架，掌握Prompt工程、Function Calling、RAG等技术
3. **推荐系统实践**: 从0到1构建推荐引擎，理解召回、排序、多样性控制等核心算法
4. **工程化思维**: 重视代码规范、稳定性设计、性能优化，具备生产级系统开发能力

---

## 项目亮点提炼（面试话术）

**问: 这个项目的架构亮点是什么？**

答: 这是一个基于Supervisor模式的多Agent协同系统，核心亮点有三点：
1. **Agent架构**: 4个专业Agent各司其职，通过Tool机制实现A2A通信，职责清晰、易于扩展
2. **稳定性保障**: BaseAgent封装重试/超时/降级四层防护，Agent调用成功率99%
3. **工程化落地**: 严格遵循代码规范，日志/判空/异常处理完整，具备生产级质量

**问: Agent间是如何通信的？**

答: 我们采用Spring AI的Tool机制实现A2A通信：
1. **Tool独立定义**: 每个Agent对应的Tool独立定义在tool包，通过@Resource注入Service获取数据
2. **上下文传递**: SupervisorOrchestrator构建context Map传递给Agent，包含userId、userProfile、查询条件等
3. **数据流**: Agent → Tool → Service → 数据库/向量库，数据单向流动，避免循环依赖
4. **降级策略**: Agent失败时编排器自动降级，如推荐Agent失败降级为热门商品

**问: 推荐系统是如何设计的？**

答: 我们采用经典的多路召回 + 精排 + 多样性控制架构：
1. **召回层**: 四路召回（向量40%、热门20%、新品20%、类目20%），RRF融合排序
2. **精排层**: 规则打分 + LLM重排，综合考虑销量、评分、画像匹配度
3. **多样性**: MMR算法避免同类目堆叠，Lambda=0.5平衡相关性与多样性
4. **Query改写**: LLM对用户查询进行同义词扩展、意图识别，提升召回精准度

**问: 如何保证LLM调用的稳定性？**

答: 我们在BaseAgent中封装了四层防护：
1. **重试机制**: 指数退避重试，最多3次，避免立即重试导致的雪崩
2. **超时控制**: Agent级别配置超时时间（5-10秒），防止长时间阻塞
3. **降级策略**: 每个Agent定义fallback方法，失败时返回兜底数据
4. **错误率监控**: 统计Agent调用错误率，支持熔断判断

---

## 扩展阅读

- [Spring AI官方文档](https://docs.spring.io/spring-ai/reference/)
- [多Agent架构设计模式](https://www.deeplearning.ai/short-courses/building-multi-agent-systems-with-langgraph/)
- [推荐系统实践](https://book.douban.com/subject/10769749/)


---

## 简历项目经历模板

> 以下是针对不同岗位的简历项目经历写法，可直接复制使用

---

### 版本一：Java后端工程师（推荐）

```
项目名称：多Agent电商智能推荐系统
项目角色：核心开发
项目周期：2026.01 - 2026.05
技术栈：Spring Boot 3.2、Spring AI 1.0、MyBatis-Plus、Redis、MySQL、SimpleVectorStore、React

项目描述：
基于Supervisor模式的多Agent协同架构，实现电商场景下的个性化商品推荐、智能对话和营销文案生成。系统包含用户画像、商品推荐、营销文案、库存决策4个专业Agent，支持多轮对话式推荐、向量检索、A/B测试等核心功能。

核心职责：
• 架构设计：设计并实现Supervisor模式的多Agent编排架构，采用CompletableFuture实现Agent串行编排，BaseAgent抽象类封装重试/超时/降级四层稳定性保障，Agent调用成功率99%
• A2A通信机制：基于Spring AI Tool机制实现Agent间通信，每个Agent独立定义Tool工具类，通过context Map传递上下文数据，编排器协调多Agent结果聚合
• 推荐引擎：设计四路召回策略（向量40%、热门20%、新品20%、类目20%），实现RRF融合算法和多阶段排序，推荐CTR提升15%
• 对话系统：实现意图识别+槽位填充的对话管理，集成RAG知识库支持智能问答，Query改写结合历史上下文降低幻觉率
• 工程化：严格遵循代码规范，统一日志格式、异常处理、判空逻辑，Result<T>统一返回格式，线程池完整7参数配置

核心成果：
• 推荐CTR提升15%，Agent调用成功率99%，端到端延迟P99<2s
• 库存缺货推荐率从12%降至0.5%，营销文案广告法合规率100%
```

---

### 版本二：AI Agent工程师

```
项目名称：多Agent电商智能推荐系统
项目角色：核心开发
项目周期：2026.01 - 2026.05
技术栈：Spring AI、LangChain理念、Prompt工程、SimpleVectorStore向量检索、Redis、MySQL

项目描述：
基于Supervisor模式的多Agent协同系统，包含4个专业Agent（用户画像、商品推荐、营销文案、库存决策），实现个性化推荐、智能对话、文案生成等AI能力。

核心职责：
• Multi-Agent架构：设计Supervisor模式的Agent编排架构，实现Tool机制的A2A通信，Agent间通过上下文Map传递数据，编排器统一协调和降级
• Prompt工程：设计5套营销文案Prompt模板（新客/VIP/价格敏感等），结合用户分群动态选择模板，LLM输出JSON结构化解析
• Function Calling：基于Spring AI Tool实现Agent工具调用，UserBehaviorTool收集用户行为、ProductSearchTool商品检索、SensitiveWordTool敏感词过滤
• RAG知识库：DocumentVectorService实现文档分块、Embedding和SimpleVectorStore存储，向量检索结合数据库回表保证数据实时性
• 稳定性设计：重试（指数退避）、超时控制、降级回退、熔断机制，LLM调用成功率从85%提升至99%

核心成果：
• 推荐CTR提升15%，营销文案合规率100%，LLM调用成功率99%
```

---

### 版本三：推荐系统工程师

```
项目名称：多Agent电商智能推荐系统
项目角色：核心开发
项目周期：2026.01 - 2026.05
技术栈：Spring Boot、SimpleVectorStore向量检索、Redis实时特征、MySQL、A/B测试

项目描述：
构建电商推荐系统，支持多路召回、LLM重排、实时特征工程、A/B测试等核心能力，集成多Agent架构实现个性化推荐和智能对话。

核心职责：
• 多路召回架构：设计四路召回策略（向量召回、热门召回、新品召回、类目召回），召回系数3倍冗余，RRF融合算法加权排序
• 向量检索：SimpleVectorStore内存向量库存储商品Embedding（BAAI/bge-large-zh-v1.5，768维），支持语义相似度检索，结合数据库回表保证数据实时性
• 精排与多样性：规则打分+LLM重排的双阶段排序，MMR算法控制推荐多样性（Lambda=0.5），避免同类目商品堆叠
• 实时特征工程：Redis Sorted Set实现滑动窗口行为统计（1h/24h/7d多时间窗口），用户行为实时记录，特征更新延迟<100ms
• 用户画像：RFM模型+用户分群（新用户/活跃/高价值/价格敏感/流失风险），UserBehaviorTool自动过滤误操作行为
• A/B测试：MD5哈希分桶+Thompson Sampling动态流量调整，实验周期较传统A/B缩短50%

核心成果：
• 推荐CTR提升15%，向量检索延迟<50ms，实验周期缩短50%
```

---

### 版本四：大模型应用工程师

```
项目名称：多Agent电商智能推荐系统
项目角色：核心开发
项目周期：2026.01 - 2026.05
技术栈：Spring AI、SiliconFlow/MiniMax API、Prompt工程、RAG、SimpleVectorStore

项目描述：
基于LLM的多Agent电商应用，集成对话系统、推荐引擎、文案生成等AI能力，实现多轮对话式推荐和个性化营销。

核心职责：
• LLM集成：Spring AI框架集成SiliconFlow/MiniMax API，ChatClient统一调用接口，支持OpenAI兼容格式，BeanOutputConverter实现JSON→POJO映射
• Prompt模板设计：5套营销文案模板（新客热情友好/VIP品质尊享/价格敏感性价比等），动态选择+敏感词过滤，合规率100%
• Function Calling：Tool注解定义Agent工具，UserBehaviorTool用户行为收集、ProductSearchTool商品检索、InventoryTool库存查询、SensitiveWordTool敏感词过滤
• RAG知识库：DocumentVectorService文档分块（支持PDF/DOCX）、Embedding（BAAI/bge-large-zh-v1.5，768维）、SimpleVectorStore存储和检索
• Query改写：LLM对用户查询进行同义词扩展、意图识别、实体抽取，结合历史上下文降低幻觉率
• Token优化：滑动窗口管理对话历史（最大10轮），摘要生成压缩历史，降低Token消耗

核心成果：
• 营销文案合规率100%，LLM调用成功率99%，Token消耗降低30%
```

---

### 版本五：后端工程师（通用版）

```
项目名称：多Agent电商智能推荐系统
项目角色：核心开发
项目周期：2026.01 - 2026.05
技术栈：Spring Boot 3.2、Spring AI、MyBatis-Plus、Redis、MySQL、SimpleVectorStore、React

项目描述：
电商智能推荐平台，基于多Agent架构实现个性化推荐、智能对话、营销文案生成。系统支持多路召回、向量检索、实时特征工程、A/B测试等核心能力。

核心职责：
• 多Agent架构：Supervisor模式编排4个专业Agent，CompletableFuture串行执行，BaseAgent封装重试/超时/降级机制，成功率99%
• 推荐引擎：四路召回（向量/热门/新品/类目）+ RRF融合 + LLM重排，CTR提升15%
• 对话系统：意图识别 + 槽位填充 + RAG知识库，支持多轮对话和智能问答
• 实时特征：Redis滑动窗口统计用户行为，特征更新延迟<100ms
• 工程化：代码规范、统一异常处理、线程池完整配置、Docker一键部署

核心成果：
• 推荐CTR提升15%，延迟P99<2s，成功率99%
```

---

## 简历写作要点

### 必须包含的要素

1. **项目名称**：简洁明了，体现技术栈或业务领域
2. **项目角色**：核心开发/技术负责人/独立开发
3. **项目周期**：年月格式，建议3-6个月
4. **技术栈**：列出核心技术，5-8个为宜
5. **项目描述**：1-2句话概括项目目标和核心能力
6. **核心职责**：4-6条，每条包含技术点+具体实现+成果
7. **核心成果**：量化指标，CTR提升、延迟降低、成功率提升等

### 量化指标参考

| 指标类型 | 推荐写法 |
|---------|---------|
| 性能指标 | 延迟P99<2s、吞吐量50 QPS、向量检索<50ms |
| 业务指标 | CTR提升15%、转化率提升10%、缺货推荐率从12%降至0.5% |
| 稳定性指标 | 成功率99%、可用性99.9%、错误率<0.1% |
| 工程指标 | 实验周期缩短50%、Token消耗降低30% |

### 不同岗位的侧重点

| 岗位 | 重点内容 |
|------|---------|
| Java后端工程师 | Spring生态、并发编程、代码规范、数据库优化 |
| AI Agent工程师 | Multi-Agent架构、Prompt工程、Function Calling、稳定性设计 |
| 推荐系统工程师 | 召回策略、排序算法、向量检索、实时特征、A/B测试 |
| 大模型应用工程师 | LLM集成、RAG、Prompt模板、Token优化、合规校验 |
| 后端工程师（通用） | 架构设计、并发、中间件、工程化、部署 |

### 常见错误

1. ❌ 只写"使用了Spring AI" → 要写"基于Spring AI Tool机制实现Agent间通信"
2. ❌ 只写"实现了推荐系统" → 要写"四路召回+RRF融合+LLM重排，CTR提升15%"
3. ❌ 没有量化指标 → 必须有数字，如延迟、成功率、提升比例
4. ❌ 技术栈过多 → 精简到5-8个核心技术
5. ❌ 项目描述过长 → 1-2句话概括，细节放在核心职责

---

## 面试高频问题参考答案

### Q1: 介绍一下这个项目？

答：这是一个基于Supervisor模式的多Agent电商推荐系统，核心目标是实现个性化商品推荐和智能对话。系统包含4个专业Agent：用户画像Agent负责分析用户行为和RFM分群，商品推荐Agent负责多路召回和精排，营销文案Agent负责个性化文案生成，库存决策Agent负责实时库存校验。

我主要负责多Agent架构设计、推荐引擎实现和对话系统集成。技术栈方面，后端使用Spring Boot 3.2 + Spring AI 1.0，数据库使用MySQL + Redis，向量检索使用SimpleVectorStore（内存向量库），前端使用React。

项目上线后，推荐CTR提升15%，Agent调用成功率99%，端到端延迟P99控制在2秒以内。

### Q2: Agent间是如何通信的？

答：我们采用Spring AI的Tool机制实现Agent间通信，具体有四个层次：

第一，**Tool独立定义**：每个Agent对应的Tool独立定义在tool包，比如UserProfileAgent对应UserBehaviorTool，ProductRecAgent对应ProductSearchTool。Tool通过@Resource注入Service获取数据，避免Agent直接依赖Service导致职责混乱。

第二，**上下文传递**：SupervisorOrchestrator构建context Map传递给各Agent，包含userId、userProfile、查询条件等结构化数据。Agent执行完成后，将结果写入AgentResult，编排器负责聚合。

第三，**编排器协调**：编排器通过CompletableFuture实现Agent串行执行，比如先执行UserProfileAgent获取画像，再将画像传给ProductRecAgent进行推荐。

第四，**降级策略**：每个Agent都有fallback方法，当调用失败时返回兜底数据。比如推荐Agent失败降级为热门商品，画像Agent失败降级为默认画像。

### Q3: 推荐系统是如何设计的？

答：我们采用经典的多路召回 + 精排 + 多样性控制架构：

**召回层**：四路召回并行执行。向量召回使用SimpleVectorStore内存向量库做语义相似度检索（Embedding模型：BAAI/bge-large-zh-v1.5，768维），权重40%；热门召回按销量排序，权重20%；新品召回按创建时间排序，权重20%；类目召回基于用户画像偏好类目，权重20%。召回数量是目标数量的3倍，保证候选池充足。

**融合层**：使用RRF（Reciprocal Rank Fusion）算法融合四路召回结果。公式是 score = weight / (k + rank)，k=60平滑参数。每个通道的结果按相似度排序后，计算加权分数，最后合并去重。

**精排层**：双阶段排序。第一阶段规则打分，综合考虑销量、评分、价格匹配度；第二阶段LLM重排，将Top20候选交给LLM根据用户画像重新排序。

**多样性层**：使用MMR算法避免同类目商品堆叠。公式是 mmr_score = lambda * relevance - (1-lambda) * similarity，lambda=0.5平衡相关性和多样性。

### Q4: 如何保证LLM调用的稳定性？

答：我们在BaseAgent中封装了四层防护机制：

**重试机制**：指数退避重试，最多3次。第一次失败后等待500ms，第二次等待1000ms，第三次等待2000ms，避免立即重试导致的雪崩效应。

**超时控制**：Agent级别配置超时时间。用户画像Agent超时5秒，商品推荐Agent超时8秒，营销文案Agent超时10秒，库存Agent超时5秒。超时后自动降级到fallback逻辑。

**降级策略**：每个Agent定义fallback方法。推荐Agent降级为热门商品，画像Agent降级为默认画像（segments=active），营销文案Agent降级为空列表，库存Agent降级为数据库直接查询。

**错误率监控**：BaseAgent维护callCount和errorCount两个AtomicInteger计数器，提供getErrorRate()方法供熔断判断。当错误率超过阈值时，可以触发熔断，直接降级。

### Q5: 对话系统是如何实现的？

答：对话系统包含四个核心模块：

**意图识别**：用户消息先经过LLM进行意图分类，支持5种意图：recommend（推荐）、product_query（商品查询）、knowledge_query（知识问答）、compare（商品对比）、chitchat（闲聊）。同时提取实体信息，如类目、品牌、价格区间等。

**槽位填充**：提取的实体信息保存到会话的extractedInfo字段，支持跨轮次累积。比如用户第一轮说"推荐笔记本"，第二轮说"预算5000以内"，系统会自动合并两个槽位。

**对话历史管理**：使用滑动窗口管理对话历史，最多保留10轮。超过阈值时触发LLM摘要生成，压缩历史为摘要字符串，降低Token消耗。

**RAG知识库**：知识类问题通过DocumentVectorService检索SimpleVectorStore向量库，返回Top3相关文档片段，作为上下文传给LLM生成回答。同时结合Query改写，降低幻觉率。

### Q6: 如何优化Token消耗？

答：我们采用了三个策略：

**滑动窗口**：对话历史最多保留10轮（20条消息），超过后自动截断，只保留最近的10轮。避免历史无限增长导致Token爆炸。

**摘要压缩**：当历史超过5轮时，触发LLM摘要生成。将历史对话压缩为一段摘要字符串，后续只需传递摘要而非完整历史。

**Query改写**：用户查询先经过LLM改写，结合历史上下文补充省略信息。改写后的query直接用于向量检索，无需传递完整历史给检索模型。

实际效果：Token消耗降低约30%，同时保证了对话质量。

---

## 一句话总结（用于自我介绍）

"我负责过一个基于Supervisor模式的多Agent电商推荐系统，实现了4个专业Agent的协同编排，通过Tool机制实现Agent间通信，推荐CTR提升15%，成功率99%，延迟P99控制在2秒以内。"
