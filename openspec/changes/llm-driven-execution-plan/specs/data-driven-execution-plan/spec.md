## ADDED Requirements

### Requirement: 意图声明数据需求而非意图依赖

`IntentItem` 必须通过 `requiredData: List<DataRequirement>` 声明需要什么**数据**，而非通过 `dependsOn` 声明依赖哪个意图。

`DataRequirement` 必须包含：
- `type`：预定义的 `DataType` 枚举值
- `filterDescription`：从用户消息中提取的筛选条件自然语言描述

`DataType` 枚举必须包含：`PRODUCT_LIST`、`POLICY_INFO`、`ORDER_INFO`、`USER_PROFILE`、`GENERAL_KNOWLEDGE`。

`DataRequirement` 禁止包含 `minCount` 或任何数量约束。

**端到端意图规则**：`recommend` 是端到端意图——内部自动搜索商品+获取画像，`requiredData` 必须为空数组 `[]`。`PRODUCT_LIST` 和 `USER_PROFILE` 仅供 `compare`/`product_query` 使用。

#### Scenario: 对比意图声明数据需求

- **WHEN** 用户说"比较vivo和小米的手机"
- **THEN** `recognizeIntents` 输出 `{intent: "compare", requiredData: [{type: PRODUCT_LIST, filterDescription: "vivo和小米品牌的手机"}]}`

#### Scenario: 推荐意图不声明数据需求

- **WHEN** 用户说"给我推荐几个手机"
- **THEN** `recognizeIntents` 输出 `{intent: "recommend", requiredData: []}`（端到端，内部处理）

#### Scenario: 无数据依赖的意图

- **WHEN** 用户说"你好"（闲聊）
- **THEN** `recognizeIntents` 输出 `{intent: "chitchat", requiredData: []}`

---

### Requirement: A2AOrchestrator 通过 LLM 生成 A2A Task 列表

`A2AOrchestrator.generatePlan()` 必须调用 LLM 生成 `List<A2ATask>`。

LLM 必须接收以下输入：
1. 历史可用数据（`extracted_info` 合并实体）
2. 历史轮次摘要（`round_intents`）
3. 对话摘要和最近对话
4. 当前用户消息
5. 识别到的意图列表及其 `requiredData`
6. 从 `AgentRegistry` 动态获取的 capability 列表（含描述）

LLM 必须输出 `ExecutionPlan`（`BeanOutputConverter` 解析），包含：
- `contextAnalysis`：可用数据与用户需求匹配情况的分析（仅日志）
- `tasks`：有序的 `A2ATask` 列表，每个 task 必须包含 `capabilityId`、`reasoning`

#### Scenario: 历史有匹配数据 — 品牌筛选

- **WHEN** Round 1 推荐了6台手机（含小米和vivo）
- **AND** `extracted_info` 包含 `recommended_product_ids: [P01...P06]`
- **AND** Round 2 用户说"比较vivo和小米的手机"
- **THEN** LLM 生成 `capabilityId: "search_by_keyword"`, `input: {query: "从历史商品中筛选vivo和小米品牌"}`
- **AND** A2AOrchestrator 注入 `contextData` + `history_*` entities 到 Agent params
- **AND** ProductQueryAgent 调用 `getProductsByIds` 获取全部商品后按品牌筛选

#### Scenario: 历史数据不匹配 — 重新搜索

- **WHEN** Round 1 只推荐了 iPhone 和华为
- **AND** Round 2 用户说"比较vivo和小米的手机"
- **AND** `extracted_info` 中不包含 vivo 或小米
- **THEN** LLM 生成 `capabilityId: "search_by_keyword"`, `input: {query: "搜索vivo和小米品牌手机"}`
- **AND** ProductQueryAgent 通过 `retrieveSimilarProducts` 从数据库搜索

#### Scenario: 位置引用 — 解析为精确 ID

- **WHEN** `extracted_info` 包含 `recommended_product_ids: [P01, P02, P03, P04, P05, P06]`
- **AND** 用户说"比较第1个和最后一个"
- **THEN** LLM 生成 `capabilityId: "get_by_ids"`, `input: {product_ids: ["P01", "P06"]}`
- **AND** ProductQueryAgent 直接调用 `getProductsByIds(["P01", "P06"])`

#### Scenario: 端到端能力 — 单 task

- **WHEN** 用户说"推荐手机" → intent: recommend, requiredData: []
- **THEN** LLM 仅生成 1 个 task：`capabilityId: "recommend_products"`
- **AND** 不生成额外的 `search_by_keyword` 或 `get_user_profile` 任务

---

### Requirement: A2AOrchestrator 禁止直接查询数据库和硬编码过滤

`A2AOrchestrator` 必须只做三件事：生成计划、调度执行、传递数据。禁止以下行为：
- 直接注入 `ProductService` 或任何数据访问 Service
- 对中文 `filterDescription` 做硬编码的 `.contains()` 匹配
- 从数据库回表查询完整 Product 对象

数据获取和筛选必须由 Agent 通过 Tool 完成。历史数据通过 `contextData` 和 `history_*` entities 注入 Agent params。

#### Scenario: Orchestrator 不持有 ProductService

- **WHEN** 检查 `A2AOrchestrator` 的依赖注入
- **THEN** 不存在 `@Resource private ProductService` 字段

---

### Requirement: recognizeIntents 输出 requiredData 而非 dependsOn

`ConversationAgent.recognizeIntents()` 的 Prompt 必须指导 LLM 输出 `requiredData`（含 `DataType` 和 `filterDescription`），而非 `dependsOn`。Prompt 中必须包含完整的 `DataType` 枚举说明，并标注哪些意图是端到端（如 recommend）。

#### Scenario: Prompt 产出正确的 requiredData 结构

- **WHEN** LLM 分析用户消息"比较vivo和小米手机，给出退换货政策"
- **THEN** LLM 输出 intents 含 `requiredData`，不含 `dependsOn`
- **AND** compare 意图: `requiredData: [{type: "PRODUCT_LIST", filterDescription: "vivo和小米品牌手机"}]`
- **AND** knowledge_query 意图: `requiredData: [{type: "POLICY_INFO", filterDescription: "退换货政策"}]`

#### Scenario: 推荐意图不附带多余数据需求

- **WHEN** 用户说"给我推荐几款手机"
- **THEN** `recommend` 意图的 `requiredData` 为空数组 `[]`
- **AND** 不会为"获取用户画像"或"搜索商品"生成独立意图

---

### Requirement: 完整执行计划必须打印日志

计划生成后，必须以 INFO 级别打印完整的 A2A 任务计划，禁止只打印任务数量。

#### Scenario: 完整计划打印日志

- **WHEN** LLM 生成包含 3 个 task 的执行计划
- **THEN** 日志输出包含 `contextAnalysis`、每 task 的 `id`/`capabilityId`/`reasoning` 等全部字段
- **AND** 日志不是仅显示 `taskCount: 3`
