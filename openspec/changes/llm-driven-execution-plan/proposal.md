## Why

当前多意图执行计划存在根本性抽象错误：**意图依赖于意图**（`compare → dependsOn: ["recommend"]`），而非**意图依赖于数据**（`compare → requires: PRODUCT_LIST`）。

核心矛盾：**LLM 只作为 Intent Labeler，不是 Execution Planner**。具体表现：

1. **依赖静默忽略**：LLM 按 Prompt 示例写出 `dependsOn: ["recommend"]`，但 `recommend` 不在意图列表中被代码静默跳过
2. **数据来源盲区**：`extracted_info` 已经跨轮累积了 `recommended_product_ids`，但执行计划完全不感知
3. **单意图上下文丢失**：`ConversationAgent` 的单意图快速路径直接路由 Agent，绕过 Orchestrator，不注入 `contextData`，导致 Round 2 的 compare 拿不到 Round 1 推荐的商品

解法分两阶段：

- **Phase 1（已实现）**：数据驱动执行计划。意图声明 `requiredData` 替代 `dependsOn`，`AgentOrchestrator` 调用 LLM 基于历史数据上下文生成执行计划，历史数据以 `contextData` 注入 Agent params
- **Phase 2（已实现 ✅）**：A2A Protocol 集成。用 Agent Card 能力声明（capability）+ `AgentRegistry` 替代 `IntentAgentResolver` 硬编码路由，用 `A2AOrchestrator` 替代 `AgentOrchestrator`。新增端到端 vs 构建块能力分类、位置引用解析、task query → agent message 双层数据流

## What Changes

### Phase 1：数据驱动执行计划（已实现 ✅）

- `IntentItem.dependsOn` → `requiredData: List<DataRequirement>`：意图声明需要什么数据而非依赖哪个意图
- `AgentOrchestrator.buildPlan()` 移除拓扑排序，改为调用 LLM 生成 `ExecutionPlan`（注入 extracted_info + round_intents）
- `AgentOrchestrator` 移除 `ProductService` 依赖——不直接查询数据库、不做硬编码关键字过滤
- 仅保留两种步骤 action：`execute_intent` 和 `fetch_data`。废除 `filter_from_history`
- 新增 `ConversationContext` / `DataType` / `DataRequirement` / `ExecutionStep` 数据结构
- `ConversationAgent.recognizeIntents()` Prompt 改为输出 `requiredData`
- `ProductQueryIntentAgent` 改造为 ReAct + Tool Calling，感知 `contextData`

### Phase 1.5：废除单意图快速路径（待实现 ⚠️）

- `ConversationAgent.execute()` 中 `intentItems.size() == 1` 快速路径分支**仍存在于代码中**（L128-141），需删除
- 所有意图（包括单意图）统一委托给 `AgentOrchestrator`，确保 `contextData` 注入

### Phase 2：A2A Protocol 集成（已实现 ✅）

- `AgentOrchestrator` → `A2AOrchestrator`：基于 capabilityId 匹配 Agent，标准化 Task 通信
- 删除 `IntentAgentResolver` → `AgentRegistry.findByCapability()` + Agent Card 自声明
- 新增 `AgentCard` / `AgentCapability` / `A2ATask`：标准化 Agent 能力声明和任务执行
- LLM 输出从 `ExecutionStep`（含 `intent` 字段）演进为 `A2ATask`（含 `capabilityId`）
- `getAgentCard()` 提升到 `BaseAgent`，`UserProfileAgent` 等非 ReAct Agent 也可参与 A2A
- Capability 描述标注 `【端到端】`/`【构建块】`，A2A Prompt 规则 6/7 防止过度拆分和引用歧义
- task.input.query 不覆盖 message，ProductQueryAgent 同时接收【任务指令】+【用户原始问题】

## Capabilities

### 已实现

- `data-driven-execution-plan`: AgentOrchestrator 调用 LLM 生成执行计划。输入：意图列表 + requiredData + 历史数据(extracted_info) + 历史轮次(round_intents)。输出：`execute_intent` 和 `fetch_data` 两类步骤
- `history-data-context`: 从 `extracted_info` 和 `round_intents` 构建 `ConversationContext`，以 `contextData` + `history_*` 形式注入 Agent params
- `intent-recognition`: `recognizeIntents` 输出 `requiredData` 替代 `dependsOn`

### 已实现

- `a2a-orchestration`: Agent Card capability 声明 → AgentRegistry 动态匹配 → A2ATask 标准化执行
- `pluggable-agent`: 新增 Agent 只需实现 `getAgentCard()` → AgentRegistry 自动发现
- `capability-classification`: 端到端 vs 构建块标注 → LLM 正确判断是否需要前置任务
- `reference-resolution`: A2A LLM 在计划阶段解析位置/序号引用 → 生成精确 product_ids

## Impact

### Phase 1 → Phase 2（已全部落地）

- `ConversationAgent.java`: 单意图快速路径已移除；注入 `A2AOrchestrator`；recognizeIntents Prompt 区分端到端 vs 构建块
- `A2AOrchestrator.java`（~500 行）: 替代 `AgentOrchestrator`，LLM 生成 `List<A2ATask>`，capabilityId 匹配 Agent
- `AgentOrchestrator.java` → @Deprecated 占位（原 724 行）
- `IntentAgentResolver.java` → @Deprecated 占位（原 93 行）
- `AgentRegistry.java`：自动收集所有 `BaseAgent.getAgentCard()`，`findByCapability()` 匹配
- `AgentCard.java` / `AgentCapability.java` / `A2ATask.java`：已创建；`AgentSkill.java` → @Deprecated
- `ExecutionPlan.java`：移除 `steps`，仅 `contextAnalysis` + `tasks`
- `BaseAgent.java`：`getAgentCard()` 默认方法（返回 null）
- `ProductQueryIntentAgent.java`：【必须筛选】+ task query/product_ids 注入 Prompt
- `UserProfileAgent.java`：新增 AgentCard（`get_user_profile` capability）
- 5 个意图 Agent：全部实现 `getAgentCard()`，capability 描述标注端到端/构建块
- `@Lazy` 解决 AgentRegistry → ConversationAgent → A2AOrchestrator 循环依赖

### 不受影响

- Controller、Service 层（除 ConversationServiceImpl）
- ConversationSession 表结构
- 对外的 ConversationResponse API
- 无新增外部依赖，复用现有 Spring AI ChatClient 和 Tool 体系
