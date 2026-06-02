## 1. 基础设施搭建

- [x] 1.1 新建 `ReActAgent extends BaseAgent` 抽象基类：Observe→Think→Act 循环（上限 3 轮）、Tool 注册、循环中断检查、推理日志
- [x] 1.2 新建 `IntentAgentResolver` Spring Bean：提取 `INTENT_AGENT_MAPPING` 和 `intentRouter` 构建逻辑，支持 `resolve(intent)` 和 `resolveAll(List<intent>)`，意图不存在返回 null
- [x] 1.3 新建模型类 `MultiIntentRecognitionResult { List<IntentItem> intents }` 和 `IntentItem { intent, entities, dependsOn }`
- [x] 1.4 新建模型类 `ExecutionPlan { List<ExecutionGroup> groups }` 和 `ExecutionGroup { List<ReActAgent> agents, boolean parallel }`
- [x] 1.5 在 `ThreadPoolExecutorConfig` 中新增 `agentParallelExecutor` Bean（core=4, max=8, CallerRunsPolicy）

## 2. AgentOrchestrator 编排器

- [x] 2.1 新建 `AgentOrchestrator`：Plan 阶段根据 dependsOn 构建执行计划 DAG，支持拓扑排序生成串行/并行组
- [x] 2.2 实现 Execute 阶段：使用 `agentParallelExecutor` 并行调度同组 Agent，组间串行等待，上游 AgentResult 传递下游
- [x] 2.3 注入 `IntentAgentResolver`，通过 resolver 查找意图对应的 ReActAgent

## 3. Agent 层重构为 ReActAgent

- [x] 3.1 `RecommendIntentAgent` 改为 `extends ReActAgent`：注册 ProductSearchTool，execute() 改为 ReAct 循环
- [x] 3.2 `ProductQueryIntentAgent` 改为 `extends ReActAgent`：注册对应 Tool，execute() 改为 ReAct 循环
- [x] 3.3 `KnowledgeIntentAgent` 改为 `extends ReActAgent`：注册对应 Tool，execute() 改为 ReAct 循环
- [x] 3.4 `CompareIntentAgent` 改为 `extends ReActAgent`：删除 `@Resource RecommendIntentAgent`，从 params 获取上游传入的 products
- [x] 3.5 `ChitChatIntentAgent` 改为 `extends ReActAgent`：单轮 ReAct 循环（Think→Act→终止）

## 4. ConversationAgent 升级

    - [x] 4.1 新增 `recognizeIntents()` 方法返回 `MultiIntentRecognitionResult`，与现有 `recognizeIntent()` 并存
- [x] 4.2 实现分流逻辑：意图数 = 1 → Route 快速路径（用 IntentAgentResolver），意图数 > 1 → 委托 AgentOrchestrator
- [x] 4.3 意图不存在或识别失败时，返回"抱歉，无法理解您的需求，请换个方式描述"而非降级闲聊
- [x] 4.4 实现结果聚合：Route 和 AgentOrchestrator 两条路径的结果统一在此聚合（拼接 + LLM 总结）

## 5. 清理与一致性

- [x] 5.1 删除 `CompareIntentAgent` 中 `@Resource RecommendIntentAgent` 硬编码注入
- [x] 5.2 确认 `AgentConstants.INTENT_AGENT_MAPPING` 不再用于路由（路由统一走 IntentAgentResolver），保留供 ANALYSIS_AGENT_NAMES 使用
- [x] 5.3 `ConversationAgent.init()` 中 intentRouter 构建逻辑迁移至 `IntentAgentResolver`，`ConversationAgent` 注入 `IntentAgentResolver` 替代直接注入 `List<BaseAgent>`

## 6. 前端适配

- [x] 6.1 `ConversationResponse` 扩展 `subTasks` 字段，展示多意图场景各子任务执行状态和耗时
