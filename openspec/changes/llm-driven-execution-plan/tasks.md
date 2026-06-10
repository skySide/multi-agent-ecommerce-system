## 1. 数据模型层

- [x] 1.1 创建 `DataType` 枚举类（PRODUCT_LIST / POLICY_INFO / ORDER_INFO / USER_PROFILE / GENERAL_KNOWLEDGE）
- [x] 1.2 创建 `DataRequirement` 类（type + filterDescription，不含 minCount）
- [x] 1.3 创建 `ExecutionStep` 类（id / action / intent / description / dataSource / requiredData / dependsOn / outputKey / reasoning）
- [x] 1.4 修改 `IntentItem`：删除 `dependsOn: List<String>`，新增 `requiredData: List<DataRequirement>`
- [x] 1.5 修改 `ExecutionPlan`：新增 `contextAnalysis` 字段，`tasks: List<A2ATask>`（原 `steps` 已删除——AgentOrchestrator 废弃后不再需要 ExecutionStep）
- [x] ~~1.6 新增 `ExecutionStep.agentParams` 字段~~ → **废弃**：A2A Protocol 下由 `A2ATask.input` 承担参数传递

## 2. 意图识别改造

- [x] 2.1 修改 `ConversationAgent.recognizeIntents()` 的 Prompt：LLM 输出 `requiredData`（含 type + filterDescription）替代 `dependsOn`
- [x] 2.2 Prompt 中嵌入 `DataType` 枚举说明，供 LLM 选择正确类型
- [x] 2.3 Prompt 中移除 `dependsOn` 示例，替换为 `requiredData` 示例
- [x] 2.4 修改 `MultiIntentRecognitionResult` 和 `IntentItem` 的序列化/反序列化，确保 `requiredData` 正确映射
- [x] 2.5 Prompt 区分端到端 vs 构建块意图：`recommend` 标注为端到端（requiredData 应为 []），`PRODUCT_LIST`/`USER_PROFILE` 标注为仅 compare/product_query 使用
- [x] 2.6 Prompt 添加防幻觉规则：USER_PROFILE 是数据类型不是意图，禁止生成独立的"获取用户画像"意图

## 2.5 单意图路由改造 ✅ 已完成

- [x] 2.5.1 移除 `ConversationAgent.execute()` 中的单意图快速路径分支（原 L128-141）
- [x] 2.5.2 所有意图（包括单意图）统一委托给 `A2AOrchestrator.execute(intentItems, params)`
- [x] 2.5.3 `buildSubAgentParams()` 不再在 ConversationAgent 中直接调用（职责移交给 A2AOrchestrator）
- [x] 2.5.4 A2AOrchestrator 通过 `buildTaskInput()` 统一注入 contextData + history_* entities
- [x] 2.5.5 `@Lazy` 解决 AgentRegistry → ConversationAgent → A2AOrchestrator → AgentRegistry 循环依赖

## 3. 对话上下文构建

- [x] 3.1 创建 `ConversationContext` 类（availableData / previousRounds / dialogueSummary / recentDialogue）
- [x] 3.2 在 `A2AOrchestrator` 中实现 `buildConversationContext(sessionId)` 方法
- [x] 3.3 添加单元测试：验证空会话、有历史数据的会话两种场景（跳过：无测试基础设施，后续补充）

## 4. LLM 计划生成（A2AOrchestrator）

- [x] 4.1 实现 `A2AOrchestrator.generatePlan()` 方法，调用 LLM 生成 `List<A2ATask>`
- [x] 4.2 编写 Plan 生成的 System Prompt + User Prompt 模板
- [x] 4.3 使用 `BeanOutputConverter<ExecutionPlan>` 解析 LLM 响应（自动处理 Markdown 代码块）
- [x] 4.4 Prompt 从 AgentRegistry 动态获取 capability 列表（含描述）
- [x] 4.5 日志打印完整 A2A 任务计划（`toPrettyString`）
- [x] 4.6 Prompt 区分端到端 vs 构建块能力（规则 6）
- [x] 4.7 Prompt 添加引用解析规则（规则 7）：位置/序号引用 → 解析为具体 product_ids

## 5. 执行引擎改造 ✅ 已完成（A2AOrchestrator 替代 AgentOrchestrator）

- [x] 5.1 移除 `AgentOrchestrator.buildPlan()` 的拓扑排序逻辑 → 已废弃整个类
- [x] 5.2 `A2AOrchestrator.executeTask()` 通过 capabilityId + AgentRegistry 匹配 Agent
- [x] 5.3 `buildTaskInput()` 统一注入：task.input → params + contextData + upstream outputs
- [x] 5.4 task 的 `query` 字段不覆盖 `message`，而是作为独立字段传递给 Agent
- [x] 5.5 `executeTasks()` 按 dependsOn 拓扑序 + 同层并行执行

## 6. 降级与日志

- [x] 6.1 所有任务执行结果打印 INFO 日志
- [x] 6.2 数据不可用时 agent 返回诚实提示
- [x] 6.3 LLM 计划生成失败时降级为 `fallbackSerialExecute()`
- [x] 6.4 全链路关键节点日志（generatePlan 入参/出参、每任务执行前后、数据传递）

## 7. A2A Protocol 集成 ✅ 已完成

> **术语变更**：`skill` → `capability`（能力）。`AgentSkill` → `AgentCapability`，`skillId` → `capabilityId`，
> `findBySkill()` → `findByCapability()`。保留 `AgentSkill.java` 为 @Deprecated 占位。
>
> **架构**：`getAgentCard()` 提升到 `BaseAgent`（默认返回 null），非 ReActAgent 也能参与 A2A。
> `AgentRegistry` 注入 `List<BaseAgent>` 替代 `List<ReActAgent>`。
>
> **循环依赖修复**：`A2AOrchestrator` 对 `AgentRegistry` 使用 `@Lazy` 注入。

### 7.1 Agent Card 定义 ✅

- [x] 7.1.1 创建 `AgentCard` 类：name / description / capabilities / inputSchema / outputSchema
- [x] 7.1.2 创建 `AgentCapability` 类：id / name / description / tags（原 `AgentSkill`，已废弃）
- [x] 7.1.3 inputSchema / outputSchema 使用 `Map<String, Object>` 简化实现（无需独立类）

### 7.2 Agent Registry 实现 ✅

- [x] 7.2.1 创建 `AgentRegistry` 组件，管理所有 Agent Card
- [x] 7.2.2 `@PostConstruct` 自动收集所有 `BaseAgent.getAgentCard()` 非 null 的 Agent
- [x] 7.2.3 实现 `findByCapability(capabilityId)` 方法：精确匹配 id 或 tags
- [x] 7.2.4 实现 `getAllCards()` / `getAllCapabilityIds()` 供 Prompt 动态获取

### 7.3 Agent Card 注册 ✅

- [x] 7.3.1 `ProductQueryIntentAgent`：capabilities = [search_by_keyword, get_by_ids]
- [x] 7.3.2 `CompareIntentAgent`：capabilities = [compare_products]
- [x] 7.3.3 `RecommendIntentAgent`：capabilities = [recommend_products]（端到端）
- [x] 7.3.4 `KnowledgeIntentAgent`：capabilities = [query_knowledge]（端到端）
- [x] 7.3.5 `ChitChatIntentAgent`：capabilities = [chitchat]（端到端）
- [x] 7.3.6 `UserProfileAgent`：capabilities = [get_user_profile]（构建块）

### 7.4 Agent Card 描述规范 ✅

- [x] 7.4.1 端到端能力标注 `【端到端】`：recommend_products, query_knowledge, chitchat
- [x] 7.4.2 构建块能力标注 `【构建块】`：search_by_keyword, get_by_ids, get_user_profile, compare_products
- [x] 7.4.3 构建块描述明确依赖关系：compare_products 注明"需要上游提供商品列表"

### 7.5 A2A Orchestrator 实现 ✅

- [x] 7.5.1 创建 `A2AOrchestrator` 类（~500 行），替代 `AgentOrchestrator`
- [x] 7.5.2 `execute(intentItems, params)` → `generatePlan()` → `executeTasks()`
- [x] 7.5.3 `executeTask()` 通过 `agentRegistry.findByCapability(capabilityId)` 匹配 Agent
- [x] 7.5.4 `buildTaskInput()` 合并 task.input + contextData + upstream outputs
- [x] 7.5.5 拓扑排序执行 + 同层并行（`agentParallelExecutor`）

### 7.6 执行计划数据模型 ✅

- [x] 7.6.1 创建 `A2ATask` 类：id / status / capabilityId / input / output / assignedAgent / dependsOn / reasoning
- [x] 7.6.2 `ExecutionPlan` 移除 `steps` 字段，仅保留 `contextAnalysis` + `tasks: List<A2ATask>`
- [x] 7.6.3 `ExecutionPlan.toPrettyString()` 改为 A2A Task 格式

### 7.7 ConversationAgent 改造 ✅

- [x] 7.7.1 `execute()` 方法：注入 `A2AOrchestrator`，删除 `AgentOrchestrator` 和 `IntentAgentResolver` 依赖
- [x] 7.7.2 `IntentAgentResolver.java` → @Deprecated 占位（93 行 → 11 行）
- [x] 7.7.3 `AgentOrchestrator.java` → @Deprecated 占位（724 行 → 11 行）

## 8. ProductQueryIntentAgent 改造 ✅

- [x] 8.1 ReAct 模式，注册 `ProductSearchTool`
- [x] 8.2 System Prompt：明确指示 LLM 利用 contextData + Tool 选择
- [x] 8.3 `buildUserMessageWithContext()` 展示：任务指令 + 用户原始消息 + 历史数据 + 指定商品ID
- [x] 8.4 System Prompt 强化筛选指令：`【必须筛选】` 替代弱提示"需要进行筛选"
- [x] 8.5 `product_ids` 显式注入 Prompt（【指定商品ID】section），引导 LLM 直接调用 getProductsByIds
- [x] 8.6 `query` 字段独立展示（【任务指令】section），不覆盖原始 message

## 9. contextData 注入规范 ✅

- [x] 9.1 所有需要访问数据的 Agent 都能接收到 `contextData` 参数
- [x] 9.2 Agent UserMessage 中以固定格式展示 contextData（【历史可用数据】section）
- [x] 9.3 `history_*` 前缀注入到 entities，保持向后兼容
- [x] 9.4 `A2AOrchestrator.buildTaskInput()` 统一处理 contextData + upstream 数据传递

## 10. 验证

### 10.1 A2A 基本流程验证

- [ ] 10.1.1 "推荐手机" → 意图识别输出 recommend + requiredData=[] → A2A 生成 1 个 task（recommend_products）
- [ ] 10.1.2 "比较小米和华为"（有历史） → search_by_keyword(query="筛选小米和华为") → ProductQueryAgent 过滤 → compare
- [ ] 10.1.3 "比较小米和华为"（无历史） → search_by_keyword(query="搜索小米和华为") → ProductQueryAgent 搜索 → compare
- [ ] 10.1.4 "比较第1个和最后一个"（有历史） → A2A LLM 解析位置引用 → get_by_ids(product_ids=[P01, P06]) → compare
- [ ] 10.1.5 "退换货政策" → query_knowledge（端到端，无需前置任务）
- [ ] 10.1.6 "你好" → chitchat（端到端，1 个 task）

### 10.2 数据传递验证

- [ ] 10.2.1 contextData（extracted_info）正确注入 Agent params
- [ ] 10.2.2 upstream 任务输出通过 dependsOn 正确传递
- [ ] 10.2.3 task.input.query 作为【任务指令】展示在 Agent Prompt 中
- [ ] 10.2.4 task.input.product_ids 作为【指定商品ID】展示在 Agent Prompt 中

### 10.3 降级与边界

- [ ] 10.3.1 LLM 计划生成失败 → fallbackSerialExecute 串行执行
- [ ] 10.3.2 capabilityId 无匹配 Agent → 返回错误提示
- [ ] 10.3.3 循环依赖已解决（@Lazy），项目正常启动
