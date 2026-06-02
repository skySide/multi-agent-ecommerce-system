## Why

当前智能客服系统仅支持单意图路由——ConversationAgent 识别一个意图后查表路由到对应子Agent。但真实用户对话常包含多个意图（如"比较这2个商品，找到对应优惠券，总结哪个优惠力度更大，生成购买计划"），当前系统无法一次并行处理多个意图。同时所有子Agent 均继承 BaseAgent，execute() 内部硬编码调用 Service 或 `@Resource` 注入其他 Agent Bean，缺乏统一的 ReAct（Observe→Think→Act）循环，Agent 行为不可观测、推理过程无法追踪。

## 整体架构

采用 **Plan & Execute + ReAct 两层组合架构**：

```
用户消息
  │
  ▼
ConversationAgent（意图识别：单意图 or 多意图？）
  │
  ├─ 意图数 = 1（简单意图）
  │     │
  │     └─ Route ──→ ReActAgent（Think → Act → Observe）
  │
  └─ 意图数 > 1（复杂意图）
        │
        ▼
      AgentOrchestrator（Plan & Execute）
        │
        ├─ Plan: LLM 分析依赖关系 → 生成执行计划 DAG
        │
        └─ Execute: 按拓扑序调度
              │
              ├─ 并行组（无依赖）:
              │    ReActAgent A ─┐
              │    ReActAgent B ─┼─ 各自 Think→Act→Observe
              │    ReActAgent C ─┘
              │
              └─ 串行组（有依赖）:
                   ReActAgent D（接收上游 Agent 输出作为输入）
                     └─ Think→Act→Observe
```

**两层职责：**
- **编排层（AgentOrchestrator）**：Plan & Execute —— 决定"谁先谁后"，生成 DAG 并按拓扑序调度
- **Agent 层（ReActAgent）**：Think→Act→Observe —— 决定"怎么做"，每个子Agent 独立完成自己的推理与工具调用循环

**单意图快速路径：** 跳过编排层，ConversationAgent 直接 Route 到 ReActAgent，保持当前低延迟。

## What Changes

**编排层——按意图数分流：**
- **简单意图（意图数 = 1）**：保持现有 Route 模式，ConversationAgent 查表路由到对应 ReActAgent，逻辑不变
- **复杂意图（意图数 > 1）**：走 AgentOrchestrator 的 Plan & Execute 模式——LLM 分析依赖关系 → 生成 DAG → 无依赖并行、有依赖串行（上游输出传下游）

**Agent 层——统一 ReAct：**
- 新增 `ReActAgent extends BaseAgent`，定义标准 Observe→Think→Act 循环（上限 3 轮防死循环）+ Tool 注册与调用 + 推理追踪。每轮循环前检查 `Thread.currentThread().isInterrupted()`，配合 BaseAgent 现有 `future.cancel(true)` 机制，前端取消会话时可及时中断 Agent 执行
- 所有意图子Agent 统一继承 ReActAgent，内部从硬编码改为 ReAct 循环。简单 Agent 一轮收敛，复杂 Agent 多轮迭代
- 删除 Agent 间硬编码 Bean 注入（如 CompareIntentAgent `@Resource RecommendIntentAgent`），依赖由 AgentOrchestrator 在 Plan & Execute 阶段传递上游结果

## Capabilities

### New Capabilities
- `multi-intent-recognition`: LLM 从单条用户消息中识别多个意图、关联实体及依赖关系，输出意图列表
- `agent-orchestration`: 新增 AgentOrchestrator，复杂意图走 Plan & Execute（DAG 构建 + 拓扑序调度），单意图走 Route 快速路径。支持后续扩展 PlanExecutorAgent 等新型 Agent
- `react-agent-framework`: 新增 ReActAgent 抽象基类（extends BaseAgent），统一 Observe→Think→Act 循环、Tool 注册与调用、推理追踪

### Modified Capabilities
- *（无现有 specs 需要修改）*

## Impact

- **Agent 层**：所有意图子Agent 从 `extends BaseAgent` 改为 `extends ReActAgent`，execute() 重构为 Observe→Think→Act 循环。ConversationAgent 不变（仍 extends BaseAgent，它是编排器）
- **编排层**：新增 AgentOrchestrator（Plan & Execute 调度）和 IntentAgentResolver（意图→Agent 映射工具类，ConversationAgent 和 AgentOrchestrator 共用）；ConversationAgent 意图识别升级为多意图输出，分流逻辑：单意图 → Route（不变），多意图 → AgentOrchestrator（新增）。识别失败或意图不存在时返回明确提示而非降级闲聊
- **删除硬编码注入**：CompareIntentAgent 中 `@Resource RecommendIntentAgent` 删除，依赖改为编排层传递上游结果
- **工具层**：现有 Tool 类（ProductSearchTool 等）不变，作为 ReActAgent Act 阶段的可调用工具
- **配置**：ThreadPoolExecutorConfig 新增专用线程池 `agentParallelExecutor`，供 AgentOrchestrator 并行调度使用
- **前端**：ConversationResponse 扩展 `subTasks` 字段，展示多意图各子任务状态和结果
