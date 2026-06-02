## ADDED Requirements

### Requirement: IntentAgentResolver 意图解析
系统 SHALL 提供 `IntentAgentResolver` 工具 Bean，维护意图类型到 ReActAgent 的映射。ConversationAgent 和 AgentOrchestrator 通过该 Resolver 根据意图名称查找对应的 ReActAgent，保证数据源唯一。

#### Scenario: 单意图解析
- **WHEN** 传入意图 "recommend"
- **THEN** 返回 RecommendIntentAgent 实例

#### Scenario: 意图不存在
- **WHEN** 传入不存在的意图名称
- **THEN** 返回 null，调用方返回"抱歉，无法理解您的需求，请换个方式描述"给用户

#### Scenario: 批量解析
- **WHEN** 传入意图列表 ["recommend", "compare"]
- **THEN** 返回对应的 ReActAgent 列表

### Requirement: 执行计划生成（Plan）
AgentOrchestrator SHALL 接收多意图列表后，根据每个意图的 dependsOn 字段构建执行计划 DAG。DAG 节点为待执行的子Agent 及其输入参数，边表示依赖关系（上游输出作为下游输入）。无依赖的节点归入同一并行组。

#### Scenario: 无依赖意图并行分组
- **WHEN** 意图列表为 [{intent: "recommend"}, {intent: "knowledge_query"}] 且两者 dependsOn 均为空
- **THEN** 生成的执行计划包含 1 个并行组，组内同时包含 recommend 和 knowledge_query 两个 Agent

#### Scenario: 有依赖意图串行分组
- **WHEN** 意图列表中 compare 的 dependsOn 包含 recommend
- **THEN** 生成的执行计划包含 2 个串行组：组 1 先执行 recommend，组 1 完成后组 2 执行 compare，且 recommend 的输出作为 compare 的输入参数

#### Scenario: 混合依赖分组
- **WHEN** 意图列表中 A 依赖 B、C 无依赖
- **THEN** 生成的执行计划：组 1 并行执行 B 和 C，组 1 完成后组 2 执行 A（接收 B 的输出）

### Requirement: 并行执行调度（Execute）
AgentOrchestrator SHALL 使用专用线程池 `agentParallelExecutor` 并行调度同一执行组内的 Agent。不同执行组之间串行等待，上游组全部完成后才开始下游组。

#### Scenario: 同组并行执行
- **WHEN** 执行组包含 3 个无依赖 Agent
- **THEN** 3 个 Agent 通过 agentParallelExecutor 同时提交执行，编排器等待全部完成后收集结果

#### Scenario: 组间串行等待
- **WHEN** 执行计划包含 2 个串行组
- **THEN** 组 2 必须在组 1 所有 Agent 执行完成后才开始执行

#### Scenario: 上游输出传递下游
- **WHEN** Agent B 依赖 Agent A 的结果
- **THEN** Agent A 的 AgentResult.data 中的关键字段（如 products、reply）被合并到 Agent B 的输入 params 中

### Requirement: 结果返回
AgentOrchestrator SHALL 在所有 Agent 执行完成后，将各子Agent 的 AgentResult 列表返回给 ConversationAgent。聚合逻辑由 ConversationAgent 统一处理。

#### Scenario: 返回子任务结果列表
- **WHEN** 3 个子Agent 全部执行完成
- **THEN** AgentOrchestrator 返回包含各子Agent 的 AgentResult 列表（含执行状态和耗时），由 ConversationAgent 统一聚合

### Requirement: 单意图 Route 快速路径
当 ConversationAgent 识别到仅有 1 个意图时，SHALL 跳过 AgentOrchestrator，直接通过 IntentAgentResolver 路由到对应 ReActAgent。

#### Scenario: 单意图直接 Route
- **WHEN** 意图识别返回仅 1 个意图
- **THEN** 不经过 AgentOrchestrator，ConversationAgent 直接通过 IntentAgentResolver 查找并调用子Agent

### Requirement: 专用线程池
系统 SHALL 在 ThreadPoolExecutorConfig 中新增独立的 `agentParallelExecutor` 线程池 Bean，专用于 AgentOrchestrator 的并行 Agent 调度，与现有 `recommendRecallExecutor` 隔离。

#### Scenario: 线程池隔离
- **WHEN** AgentOrchestrator 并行执行多个 Agent
- **THEN** 使用 agentParallelExecutor 线程池，不影响 recommendRecallExecutor 的推荐召回任务
