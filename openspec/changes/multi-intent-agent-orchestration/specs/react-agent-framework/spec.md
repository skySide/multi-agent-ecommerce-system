## ADDED Requirements

### Requirement: ReActAgent 基类
系统 SHALL 提供 `ReActAgent extends BaseAgent` 抽象基类，定义统一的 Observe→Think→Act 循环（上限 3 轮）。所有意图子Agent 须继承 ReActAgent，通过重写钩子方法适配自身业务。

#### Scenario: ReAct 循环执行
- **WHEN** 子Agent 的 execute() 被调用
- **THEN** Agent 进入 ReAct 循环：构建初始 context → Think（调用 LLM 决策）→ Act（执行 Tool 或生成回复）→ Observe（结果追加到 context）→ 判断是否终止 → 未终止则继续循环

#### Scenario: 单轮收敛
- **WHEN** Agent 任务简单（如 ChitChatIntentAgent 只需生成闲聊回复）
- **THEN** ReAct 循环仅执行 1 轮：Think（LLM 理解语境并生成回复）→ Act（返回回复文本）→ 终止

#### Scenario: 多轮迭代
- **WHEN** Agent 需要先调用 Tool 获取数据再生成回复（如 RecommendIntentAgent）
- **THEN** ReAct 循环执行多轮：Think → Act（调 Tool）→ Observe（Tool 结果回填 context）→ Think → Act（生成最终回复）→ 终止

#### Scenario: 超 3 轮强制终止
- **WHEN** ReAct 循环达到第 3 轮后 LLM 仍想调用 Tool
- **THEN** 强制终止循环，返回已有结果，日志告警

### Requirement: 循环中断检查
ReActAgent SHALL 在每轮循环开始前检查 `Thread.currentThread().isInterrupted()`，当前端取消会话时及时退出执行。BaseAgent 的 `future.cancel(true)` 打断线程后，ReActAgent 在下一轮循环入口感知中断并终止。

#### Scenario: 前端取消会话
- **WHEN** 前端调用取消接口，ConversationAgent.cancelGeneration(sessionId) 触发 future.cancel(true)
- **THEN** ReActAgent 在当前轮 LLM 调用完成后，下一轮循环入口检测到中断标志，立即终止并返回 fallback 结果

### Requirement: Tool 注册与调用
ReActAgent SHALL 提供 Tool 注册方法，子类在初始化时注册所需 Tool。Act 阶段通过 Spring AI ChatClient + Tools 机制自动调度 Tool 执行，Tool 执行结果自动回填到对话 context 供下一轮 Think 使用。

#### Scenario: Tool 注册
- **WHEN** RecommendIntentAgent 初始化
- **THEN** 它向 ReActAgent 注册 ProductSearchTool，LLM 在 Think 阶段可决定调用该 Tool

#### Scenario: Tool 执行与结果回填
- **WHEN** LLM 决定调用 getHotProducts 工具
- **THEN** Spring AI 框架自动执行 getHotProducts，将返回的商品列表追加到对话 context，LLM 在下一轮 Think 中基于此数据生成推荐回复

### Requirement: 推理追踪
ReActAgent SHALL 记录每一步 Think 的推理内容和 Act 的执行结果，通过日志输出。

#### Scenario: 推理日志记录
- **WHEN** ReAct 循环每一步执行
- **THEN** 日志输出包含当前步骤序号、Think 决策内容、Act 执行动作（Tool 名称 + 参数摘要）、Observation 结果摘要

### Requirement: Agent 间解耦
意图子Agent SHALL NOT 通过 `@Resource` 或其他 DI 方式直接注入其他 Agent Bean。Agent 间依赖由 AgentOrchestrator 在 Plan & Execute 阶段通过传递上游 AgentResult 解决。

#### Scenario: 删除硬编码 Agent 注入
- **WHEN** CompareIntentAgent 需要商品列表进行对比分析
- **THEN** CompareIntentAgent 不再 `@Resource RecommendIntentAgent`，而是从输入 params 中获取编排层传入的 products 字段

#### Scenario: 编排层传递上游结果
- **WHEN** compare 意图依赖 recommend 意图
- **THEN** AgentOrchestrator 先执行 RecommendIntentAgent，将其返回的 products 列表合并到 CompareIntentAgent 的 params 中，再执行 CompareIntentAgent
