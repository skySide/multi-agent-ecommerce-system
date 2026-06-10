## ADDED Requirements

### Requirement: 从会话数据构建结构化对话上下文

`A2AOrchestrator` 在调用 LLM 生成计划前，必须从 `ConversationSession` 构建结构化的 `ConversationContext`。

`ConversationContext` 必须包含：
- `availableData`：从 `extracted_info` 解析的合并实体（Map<String, Object>）
- `previousRounds`：从 `round_intents` 解析的历史轮次快照列表
- `dialogueSummary`：来自 `summary` 字段
- `recentDialogue`：来自 `dialogue_history` 的最近 N 条对话

#### Scenario: 首轮推荐后构建上下文

- **WHEN** Round 1 完成，`extracted_info: {recommended_product_ids: ["P01","P02"], category: "手机"}`
- **AND** `round_intents: [{"round":0, "intent":"recommend", "entities":{...}}]`
- **AND** Round 2 开始
- **THEN** `ConversationContext.availableData` 包含 `recommended_product_ids` 和 `category`
- **AND** `ConversationContext.previousRounds` 包含 1 条 recommend 意图记录

#### Scenario: 空会话构建上下文

- **WHEN** Round 1 开始（无历史轮次）
- **AND** `extracted_info` 为 `"{}"`
- **AND** `round_intents` 为 `"[]"`
- **THEN** `ConversationContext.availableData` 为空 Map
- **AND** `ConversationContext.previousRounds` 为空列表

---

### Requirement: 执行任务时注入 contextData 到 Agent params

`A2AOrchestrator` 在 `buildTaskInput()` 中必须将 `ConversationContext.availableData` 注入到 Agent 的 params 中：

- `subParams.put("contextData", availableData)` — 完整的历史累积数据
- `subParams.get("entities").put("history_<key>", value)` — 以 `history_` 前缀将关键字段合并到 entities 层

同时注入 task 自身的 input（query、product_ids 等）作为独立字段，不覆盖原始 `message`。

Agent 内 LLM 可通过 Tool（如 `ProductSearchTool.getProductsByIds()`）使用这些数据完成获取和筛选。

#### Scenario: ProductQueryAgent 收到历史商品 ID + 任务指令

- **WHEN** `extracted_info` 包含 `recommended_product_ids: ["P01","P02","P03"]`
- **AND** LLM 计划生成 `capabilityId: "search_by_keyword"`, `input: {query: "筛选小米和华为"}`
- **THEN** Agent params 包含 `contextData: {recommended_product_ids: [...]}`
- **AND** Agent params 包含 `query: "筛选小米和华为"`（不覆盖 message）
- **AND** Agent entities 包含 `history_recommended_product_ids: ["P01","P02","P03"]`
- **AND** Agent Prompt 展示：【任务指令】+ 【用户原始问题】+ 【历史可用数据】

#### Scenario: ProductQueryAgent 收到精确 product_ids

- **WHEN** A2A LLM 解析位置引用后生成 `capabilityId: "get_by_ids"`, `input: {product_ids: ["P01","P06"]}`
- **THEN** Agent params 包含 `product_ids: ["P01","P06"]`
- **AND** Agent Prompt 展示：【指定商品ID】[P01, P06] → 引导 LLM 直接调用 getProductsByIds

#### Scenario: compare Agent 收到上游任务数据

- **WHEN** task_1（search_by_keyword）产出 `{products: [小米14, vivo X100]}`
- **AND** task_2（compare_products）依赖 task_1
- **THEN** task_2 的 params 包含 `products: [小米14, vivo X100]`
- **AND** params 包含 `upstream_task_1: {products: [...]}`

---

### Requirement: 数据不可用时如实告知用户

当任何类型的数据完全不可用（历史中不存在，且 Agent 通过 Tool 也无法获取），系统必须返回诚实的回复说明数据不可用，禁止编造结果。

#### Scenario: 知识库检索无结果

- **WHEN** `requiredData: [{type: POLICY_INFO, filterDescription: "退换货政策"}]`
- **AND** RAG 知识库检索无匹配文档
- **THEN** Agent 返回: "抱歉，暂时没有找到关于退换货政策的相关信息，建议您联系人工客服获取帮助。"

#### Scenario: 指定品牌商品不存在

- **WHEN** `requiredData: [{type: PRODUCT_LIST, filterDescription: "vivo品牌手机"}]`
- **AND** Agent 通过 Tool 查询后数据库中无 vivo 品牌手机
- **THEN** Agent 返回: "抱歉，暂时没有找到vivo品牌的手机商品。"

---

### Requirement: `filter_from_history` 和 `execute_intent`/`fetch_data` action 已废除

A2A 模式下不再使用 `ExecutionStep` 的 action 模型（`execute_intent`/`fetch_data`/`filter_from_history`）。替代方式：LLM 生成 `A2ATask`（含 `capabilityId`），由 `AgentRegistry.findByCapability()` 匹配 Agent 执行。数据筛选由 Agent 内部 LLM 通过 Tool 完成，Orchestrator 不参与。

#### Scenario: 执行计划不含旧 action 类型

- **WHEN** LLM 生成 A2A 执行计划
- **THEN** 所有 task 仅有 `capabilityId` 字段
- **AND** 不存在 `action`、`intent`、`dataSource` 等旧 ExecutionStep 字段
