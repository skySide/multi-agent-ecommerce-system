## ADDED Requirements

### Requirement: 多意图识别
系统 SHALL 支持从单条用户消息中识别多个意图。当用户消息包含多个独立或依赖的需求时，LLM 须返回意图列表，每个意图包含意图类型、关联实体、以及 LLM 根据语义分析出的依赖关系（dependsOn）。

#### Scenario: 识别复杂多意图消息
- **WHEN** 用户发送"比较这2个商品，找到对应优惠券，总结哪个优惠力度更大，生成购买计划"
- **THEN** 系统返回意图列表，包含多个意图项（compare、knowledge_query 等），LLM 根据语义分析意图间依赖关系

#### Scenario: 识别简单单意图消息
- **WHEN** 用户发送"推荐一款手机"
- **THEN** 系统返回意图列表包含单个意图 [{intent: "recommend", entities: {category: "手机"}}]

#### Scenario: 无法分析出依赖
- **WHEN** LLM 无法从用户消息中明确分析出意图间依赖
- **THEN** 意图间 dependsOn 为空，视为无依赖，所有意图可并行执行

### Requirement: 意图数分流
系统 SHALL 根据识别的意图数量选择执行模式：意图数 = 1 时走 Route 模式直接路由到对应 ReActAgent；意图数 > 1 时委托 AgentOrchestrator 走 Plan & Execute 模式。

#### Scenario: 单意图走 Route
- **WHEN** 意图识别返回仅 1 个意图
- **THEN** ConversationAgent 使用 intentRouter 查表路由到对应 ReActAgent，与当前逻辑一致

#### Scenario: 多意图委托 AgentOrchestrator
- **WHEN** 意图识别返回 2 个及以上意图
- **THEN** ConversationAgent 委托 AgentOrchestrator 生成执行计划并调度执行
