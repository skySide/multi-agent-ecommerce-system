## Context

当前系统采用单意图 Route 模式：ConversationAgent 调用 LLM 识别一个意图 → 查静态 Map 路由到子Agent → 子Agent.execute() 硬编码执行业务逻辑。子Agent 统一继承 BaseAgent（提供异步/重试/超时基础设施），但 execute() 内部是黑盒——有的用 LLM+Tool（RecommendIntentAgent），有的硬编码调 Service（ProductQueryIntentAgent），有的直接 `@Resource` 注入其他 Agent Bean（CompareIntentAgent）。

约束：
- Spring Boot 3.2.5 + Spring AI 1.0.0-M6（OpenAI 兼容接口）
- 现有 8 个 Agent（1 个编排器 ConversationAgent + 5 个意图子Agent + Pipeline Agent）+ 4 个 Tool
- 已有线程池 `recommendRecallExecutor`（推荐召回专用）
- 已有会话取消机制：`ConversationAgent.runAndTrack(sessionId, future)` + `cancelGeneration(sessionId)` 通过 `future.cancel(true)` 中断线程

## Goals / Non-Goals

**Goals:**
- 支持单条用户消息识别多个意图
- 简单意图（1个）保持现有 Route 模式不变
- 复杂意图（>1个）走 AgentOrchestrator 的 Plan & Execute：生成 DAG → 无依赖并行、有依赖串行
- 所有意图子Agent 统一继承 ReActAgent，内部走 Observe→Think→Act 循环（上限 3 轮）
- ReActAgent 循环每轮前检查线程中断，复用现有 `future.cancel(true)` 实现及时中断
- 新增专用线程池 `agentParallelExecutor` 支持并行 Agent 执行
- 删除 Agent 间 `@Resource` 注入，依赖由编排层传递

**Non-Goals:**
- 不新增业务意图类型
- 不修改 Tool 层
- 不修改数据库表结构
- 不修改 ConversationAgent 的记忆管理、重复检测等非路由逻辑
- 不修改 SupervisorOrchestrator

## Decisions

### 决策 1：两层架构 —— AgentOrchestrator（Plan & Execute）+ ReActAgent（Think→Act→Observe）

编排层用 AgentOrchestrator 做 Plan & Execute（决定"谁先谁后"），Agent 层用 ReActAgent 做 Think→Act→Observe（决定"怎么做"）。单意图直接 Route 跳过编排层。

**替代方案**：把 Plan & Execute 和 ReAct 揉在一个类里 → 拒绝，违反单一职责，无法扩展。

### 决策 2：ReActAgent 继承 BaseAgent，不修改 BaseAgent

新建 `ReActAgent extends BaseAgent`。

**原因**：ConversationAgent 是编排器，不需要 ReAct 循环。BaseAgent 保持纯粹的执行基础设施（异步/重试/超时/中断）。

### 决策 3：ReAct 循环基于 Spring AI ChatClient + Tools，上限 3 轮

```
while (iteration < 3 && !Thread.currentThread().isInterrupted()) {
    response = chatClient.prompt().tools(tools).user(context).call()
    if (response.hasToolCalls()) → 执行 Tool，结果追加到 context，继续循环
    else → 提取最终回复，终止
}
if (iteration >= 3) → 强制终止，返回已有结果
```

**原因**：Spring AI 已提供 Tool Calling 基础设施。3 轮上限防止死循环，中断检查复用 BaseAgent 现有的 `future.cancel(true)` 机制——前端取消会话时，LLM 调用抛出 `InterruptedIOException`，BaseAgent 不再重试直接 fallback，同时循环入口的 `isInterrupted()` 检查确保及时退出。

### 决策 4：多意图识别扩展自现有 IntentRecognitionResult

新增 `MultiIntentRecognitionResult { List<IntentItem> intents }`，IntentItem = `{ intent, entities, dependsOn }`。ConversationAgent 新增 `recognizeIntents()`，与现有 `recognizeIntent()` 并存。

### 决策 5：意图依赖关系完全由 LLM 从用户消息中分析

LLM 在多意图识别时直接根据用户消息的语义分析意图间的依赖关系，输出 `dependsOn` 字段。不需要静态依赖映射表——依赖关系是用户问题决定的，不是固定规则（例如同一组意图在不同语境下可能有不同依赖）。LLM 无法分析出依赖时，视为无依赖并行执行。

### 决策 6：IntentAgentResolver —— 提取意图→Agent 映射为独立工具类

新增 `IntentAgentResolver` Spring Bean，将 `INTENT_AGENT_MAPPING`（意图 → Agent 名称映射）和 `intentRouter` 构建逻辑从 ConversationAgent 中抽出。ConversationAgent 和 AgentOrchestrator 共用同一个 Resolver 查找 ReActAgent。

```java
@Component
public class IntentAgentResolver {
    private static final Map<String, String> INTENT_AGENT_MAPPING = Map.of(...);
    private final Map<String, ReActAgent> intentRouter = new LinkedHashMap<>();

    @PostConstruct
    void init() { /* 注入 List<ReActAgent>，构建 intent → ReActAgent 映射 */ }

    public ReActAgent resolve(String intent) { ... }
    public List<ReActAgent> resolveAll(List<String> intents) { ... }
}
```

**原因**：ConversationAgent 和 AgentOrchestrator 都需要根据意图查找对应的 ReActAgent，共用同一 Resolver 保证数据源唯一。同时删除 `AgentConstants` 中重复的映射定义。

### 决策 7：AgentOrchestrator 独立组件，ConversationAgent 负责分流

新增 `AgentOrchestrator`。ConversationAgent 分流：单意图 → Route，多意图 → 委托 AgentOrchestrator。

### 决策 7：并行执行使用新线程池 agentParallelExecutor

在 ThreadPoolExecutorConfig 中新增 `agentParallelExecutor` Bean，与推荐召回的 `recommendRecallExecutor` 隔离。

### 决策 8：意图子Agent 全量统一继承 ReActAgent

所有意图子Agent（RecommendIntentAgent、ProductQueryIntentAgent、KnowledgeIntentAgent、CompareIntentAgent、ChitChatIntentAgent）全部继承 ReActAgent。简单 Agent 一轮收敛，代码结构一致。

### 决策 9：结果聚合统一由 ConversationAgent 完成

Route 和 AgentOrchestrator 两条路径最终都返回 `AgentResult` 给 ConversationAgent，返回结构一致。ConversationAgent 负责统一聚合回复（拼接各子任务结果 + 调 LLM 总结），不区分来源路径。

## Risks / Trade-offs

- **[风险] LLM 多意图识别准确率** → 识别失败或意图不存在时，返回明确提示"抱歉，无法理解您的需求，请换个方式描述"而非降级闲聊
- **[风险] 并行执行增加 LLM 调用并发** → 线程池大小可配置，初始保守（core=4, max=8）
- **[权衡] ReActAgent 改造工作量大** → 但长期扩展性收益大于短期成本
- **[风险] ReAct 循环超过 3 轮** → 强制终止并返回已有结果，日志告警

## Open Questions

- 无
