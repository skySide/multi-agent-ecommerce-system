## Context

当前架构：`ConversationAgent.recognizeIntents()` 调用 LLM 识别意图 + 依赖关系（`dependsOn`），`AgentOrchestrator.buildPlan()` 用拓扑排序生成执行计划。核心缺陷是 LLM 只输出"compare 依赖 recommend"这种意图级依赖，但 `recommend` 不在意图列表中导致依赖被静默忽略——因为没有把**数据**作为一等公民。

`conversation_session` 表已有完善的数据基础：`extracted_info`（跨轮累积实体）、`round_intents`（每轮意图+实体快照）、`dialogue_history`（对话文本）、`summary`（LLM 摘要）。这些数据在计划生成时未被利用。

## Goals / Non-Goals

**Goals:**
- 意图声明**数据需求**（`requiredData`），而非依赖其他意图
- `AgentOrchestrator` 调用 LLM，基于历史数据上下文（extracted_info + round_intents）生成完整执行计划
- **`AgentOrchestrator` 不直接查询数据库、不做硬编码关键字过滤**——数据获取和筛选由 Agent 通过 Tool 完成
- 历史数据以 `contextData` 形式注入 Agent params，Agent 内 LLM 通过 `ProductSearchTool.getProductsByIds()` 等 Tool 使用
- **废除单意图快速路径——所有意图统一由 AgentOrchestrator 编排**，确保历史上下文不丢失
- 执行计划打印完整日志，便于追溯

**Non-Goals:**
- 不改变任何 ReActAgent 内部逻辑
- 不修改 ConversationSession 表结构
- 不修改对外的 ConversationResponse API
- 不增加新的外部依赖
- 不缓存执行计划

## Decisions

### 1. 依赖模型：从 `dependsOn` 到 `requiredData`

**决策**：`IntentItem.dependsOn: List<String>` → `IntentItem.requiredData: List<DataRequirement>`

核心思想转变：
```
❌ 旧: compare 依赖 recommend（意图级依赖，recommend 不在意图列表中 → 被忽略）
✅ 新: compare 需要 PRODUCT_LIST 数据 → LLM 判断数据来源
       → 历史 extracted_info 有 recommended_product_ids
       → LLM 计划: 生成 product_query 步骤 → Agent 通过 Tool 获取和筛选
       → 结果作为 compare 的输入
```

`DataRequirement` 数据结构（**不包含 minCount**——LLM 无法预先知道匹配数量）：
```java
public class DataRequirement {
    DataType type;           // 数据类型（预定义枚举）
    String filterDescription; // 从用户消息中提取的筛选条件，供 LLM 理解数据需求
}
```

`DataType` 预定义枚举：
```java
public enum DataType {
    PRODUCT_LIST,       // 商品列表 — 推荐/对比/查询场景
    POLICY_INFO,        // 政策信息 — 退换货/物流/保修/优惠活动
    ORDER_INFO,         // 订单信息 — 订单状态/物流追踪
    USER_PROFILE,       // 用户画像 — 偏好/历史行为
    GENERAL_KNOWLEDGE   // 通用知识 — 品牌介绍/使用教程等
}
```

### 2. 核心设计原则：AgentOrchestrator 不碰数据

**决策**：AgentOrchestrator 只做三件事——生成计划、调度执行、传递数据。**绝不直接查询数据库、不做硬编码过滤**。

```
AgentOrchestrator 的边界：
  ✅ LLM 生成执行计划
  ✅ 从 session 读取 extracted_info / round_intents 构建上下文（只读元数据）
  ✅ 将上下文以 contextData + history_* 形式注入 Agent params
  ✅ 按步骤拓扑序调度 Agent 执行
  ✅ 通过 outputKey 传递步骤间结果

  ❌ 直接调用 ProductService / ProductSearchTool
  ❌ 硬编码中文关键字做 .contains() 匹配
  ❌ 从数据库回表查商品对象
  ❌ 做任何需要 LLM 判断的数据筛选
```

**为什么**：Orchestrator 是调度器，不是数据处理器。数据筛选需要语义理解（"vivo和小米品牌" → 匹配 Brand 字段），这是 LLM 的职责。硬编码的 `brand.contains("小米")` 既脆弱又不可扩展。

### 3. 数据流：contextData 注入 + Tool 获取

**决策**：历史数据通过两条路径流入 Agent：

```
路径 A（计划生成时）：conversationContext → LLM Prompt
  → LLM 看到 availableData，决定计划结构

路径 B（步骤执行时）：contextData + history_* → Agent params
  → Agent 内 LLM 通过 Tool 使用历史数据
```

执行步骤时的 params 注入：
```java
// AgentOrchestrator.executeIntentStep() 注入:
subParams.put("contextData", conversationContext.getAvailableData());
// 同时注入到 entities:
entities.put("history_recommended_product_ids", ["P01","P02",...]);
entities.put("history_category", "手机");
```

Agent 内 LLM 看到这些数据后，可以调用 `ProductSearchTool.getProductsByIds(ids)` 获取完整 Product 对象，再判断哪些匹配用户条件。

### 4. 步骤 action 类型（仅两种）

**决策**：仅保留 `execute_intent` 和 `fetch_data`。废除 `filter_from_history`。

| action | 说明 | 示例 |
|--------|------|------|
| `execute_intent` | 执行意图 Agent，生成用户回复 | compare, knowledge_query, recommend |
| `fetch_data` | 调用 Agent 获取数据，不生成回复，仅为后续步骤提供数据 | PRODUCT_LIST → product_query Agent, POLICY_INFO → knowledge_query Agent |

`filter_from_history` 被废除的原因：它让 Orchestrator 承担了数据筛选职责（查 DB + 关键字匹配），违反了设计原则。

替代方案：需要从历史获取商品时，LLM 计划生成 `fetch_data(PRODUCT_LIST)` 步骤 → Orchestrator 注入 `contextData`（含 `recommended_product_ids`）→ product_query Agent 内 LLM 通过 Tool 完成获取和筛选。

### 5. AgentOrchestrator 承担 LLM 计划生成

```
ConversationAgent:
  recognizeIntents() → List<IntentItem> (意图 + requiredData)   ← 保留
  单意图 → 委托 AgentOrchestrator                                ← 废除直接路由
  多意图 → agentOrchestrator.execute(intentItems, params)        ← 传入意图列表

AgentOrchestrator:
  buildConversationContext(params)                                ← 从 session 构建上下文
  generatePlan(intentItems, conversationContext)                  ← LLM 生成计划
    → ExecutionPlan (只含 execute_intent / fetch_data 步骤)
  executePlan(plan, params)                                       ← 按拓扑序调度
```

**关键变化：单意图也走 AgentOrchestrator**

原设计中单意图直接路由是性能优化，但导致了上下文丢失问题：

```java
// ❌ 旧实现：单意图直接路由，无上下文注入
if (intentItems.size() == 1) {
    IntentItem singleIntent = intentItems.get(0);
    ReActAgent subAgent = intentAgentResolver.resolve(singleIntent.getIntent());
    Map<String, Object> subParams = buildSubAgentParams(...);  // 只有基础参数
    AgentResult subResult = subAgent.runAsync(subParams).join();
}

// ✅ 新实现：所有意图统一编排
if (!CollectionUtils.isEmpty(intentItems)) {
    // 单意图或多意图，都走 AgentOrchestrator
    subResults = agentOrchestrator.execute(intentItems, params);
}
```

**为什么单意图也需要编排：**

1. **上下文注入**：AgentOrchestrator 会从 `extracted_info` 读取历史数据，注入到 `contextData`
2. **数据获取**：如果意图需要数据（如 compare 需要 PRODUCT_LIST），LLM 会生成 `fetch_data` 步骤
3. **一致性**：避免单意图和多意图走不同的代码路径，降低维护成本
4. **用户体验**：用户连续对话时，历史数据始终可用

### 6. LLM 计划生成 Prompt 设计

三层结构：可用数据 → 意图需求 → 生成计划。Prompt 明确告知 LLM 不要生成 `filter_from_history`。

```
System: 你是执行计划生成器。
核心原则：
1. 意图需要的是数据，不是其他意图。
2. 数据获取和筛选由 Agent 通过 Tool 完成，Orchestrator 只负责调度。
3. 如果需要从历史获取商品，生成 fetch_data(PRODUCT_LIST) 或 execute_intent(product_query) 步骤。
4. 不要使用 filter_from_history action——这会让 Orchestrator 承担数据筛选职责。

User:
【可用数据（extracted_info）】
- recommended_product_ids: [P01, P02, P03, P04, P05, P06]
- category: 手机

【历史轮次（round_intents）】
- Round 0: recommend → entities: {category: "手机"}

【对话摘要/最近对话】...

【当前用户消息】比较vivo和小米的手机，并且给出对应的退换货政策

【需要执行的意图】
1. compare: 需要 PRODUCT_LIST → filterDescription: "vivo和小米品牌手机"
2. knowledge_query: 需要 POLICY_INFO → filterDescription: "退换货政策"

输出格式：
{
  "contextAnalysis": "历史有6台推荐手机，包含小米14和vivo X100，品牌匹配",
  "steps": [
    {
      "id": "step_1",
      "action": "execute_intent",
      "intent": "product_query",
      "description": "获取历史推荐的手机并筛选vivo和小米品牌",
      "dataSource": "from_history",
      "requiredData": [{"type": "PRODUCT_LIST", "filterDescription": "vivo和小米"}],
      "dependsOn": [],
      "outputKey": "filtered_products",
      "reasoning": "历史有recommended_product_ids，Agent内LLM通过Tool获取后筛选"
    },
    {
      "id": "step_2",
      "action": "execute_intent",
      "intent": "compare",
      "description": "对比筛选出的vivo和小米手机",
      "dataSource": "from_step",
      "requiredData": [],
      "dependsOn": ["step_1"],
      "outputKey": "comparison_result",
      "reasoning": "依赖step_1产出的商品列表进行对比分析"
    },
    {
      "id": "step_3",
      "action": "execute_intent",
      "intent": "knowledge_query",
      "description": "查询退换货政策",
      "dataSource": "from_rag",
      "requiredData": [{"type": "POLICY_INFO", "filterDescription": "退换货政策"}],
      "dependsOn": [],
      "outputKey": "policy_info",
      "reasoning": "历史无政策数据，需从知识库独立检索，与商品对比无依赖可并行"
    }
  ]
}
```

### 7. 日志规范

`generatePlan()` 必须打印完整 `ExecutionPlan.toPrettyString()`，**禁止只打步骤数量**。

```
========== 执行计划 ==========
上下文分析: 历史推荐了6台手机（含小米14和vivo X100），匹配用户指定品牌。

Step step_1 [execute_intent: product_query]:
  描述: 获取历史推荐的手机并筛选vivo和小米品牌
  数据来源: from_history
  数据需求: PRODUCT_LIST(filter=vivo和小米)
  产出: filtered_products
  依赖: 无
  推理: 历史有recommended_product_ids，Agent通过Tool获取后由LLM筛选

Step step_2 [execute_intent: compare]:
  描述: 对比筛选出的vivo和小米手机
  数据来源: from_step
  产出: comparison_result
  依赖: [step_1]
  推理: 依赖step_1产出的商品列表进行对比

Step step_3 [execute_intent: knowledge_query]:
  描述: 查询退换货政策
  数据来源: from_rag
  数据需求: POLICY_INFO(filter=退换货政策)
  产出: policy_info
  依赖: 无
  推理: 独立于商品对比，可并行执行
==============================
```

---

## Phase 1 → Phase 2：已完成的迁移

> **Phase 1（AgentOrchestrator + ExecutionStep）**：已废弃。`AgentOrchestrator.java` 和 `IntentAgentResolver.java` 均标记 @Deprecated。
>
> **Phase 2（A2AOrchestrator + A2ATask）**：已实现并投入使用。核心变化：
>
> | 维度 | Phase 1（旧） | Phase 2（新） |
> |------|-------------|-------------|
> | 步骤模型 | `ExecutionStep`（action/intent/dependsOn） | `A2ATask`（capabilityId/input/dependsOn） |
> | Agent 匹配 | `IntentAgentResolver.resolve(intent)` 硬编码 | `AgentRegistry.findByCapability(capabilityId)` 动态匹配 |
> | 能力声明 | 无——intent 名称即路由 key | `AgentCapability` + `AgentCard`，Agent 自声明 |
> | 新增 Agent | 修改 Resolver Map + Prompt | 实现 `getAgentCard()` → AgentRegistry 自动发现 |
> | LLM Prompt | 固定 intent 列表 | 从 AgentRegistry 动态获取 capability 列表（含描述） |
> | 术语 | skill / skillId | capability / capabilityId |
> | 单意图 | 快速路径绕过编排层 | 统一走 A2AOrchestrator |
> | 循环依赖 | 无 | `@Lazy` 解决 AgentRegistry → ConversationAgent → A2AOrchestrator 循环 |

### 8. Agent-to-Agent (A2A) Protocol 集成

**核心原则**：Agent 之间通过标准化的 A2A 协议通信，无需硬编码参数映射。

**当前问题**：

```java
// ❌ 硬编码路由：需要在 Prompt 中指定每个 Agent 需要什么参数
//    注：agentParams 方案在 tasks 1.6 阶段已被废弃，从未在 ExecutionStep 上实现
"agentParams": {"query": "小米和苹果品牌手机"}  // 谁来决定这个参数？

// ❌ 硬编码匹配：IntentAgentResolver 通过 Map 查找 Agent
ReActAgent agent = intentAgentResolver.resolve("product_query");
```

问题：
1. 新增 Agent 需要修改 Prompt 和 Resolver
2. 参数映射逻辑散落在多处
3. Agent 无法自主声明能力和需求

**决策**：引入 Google A2A Protocol，让 Agent 通过标准化的 Agent Card 声明能力，执行计划只需要描述任务，Agent 自己决定如何执行。

### 8.1 Agent Card 结构

每个 Agent 声明自己的能力卡片：

```java
public class AgentCard {
    String name;                    // Agent 名称
    String description;             // 能力描述
    List<AgentSkill> skills;        // 技能列表
    AgentInputSchema inputSchema;   // 输入模式（声明需要什么参数）
    AgentOutputSchema outputSchema; // 输出模式（声明产出什么）
}

public class AgentSkill {
    String id;          // 技能 ID
    String name;        // 技能名称
    String description; // 技能描述
    List<String> tags;  // 标签（如 "product", "compare", "recommend"）
}

public class AgentInputSchema {
    String type;                   // "object"
    Map<String, Property> properties; // 参数定义
    List<String> required;         // 必填参数
}
```

### 8.2 Agent Card 示例

**ProductQueryAgent Card：**
```json
{
  "name": "product_query_agent",
  "description": "查询商品信息，支持关键词搜索、ID 查询、向量召回",
  "skills": [
    {"id": "search_by_keyword", "name": "关键词搜索", "tags": ["product", "search"]},
    {"id": "get_by_ids", "name": "ID 批量查询", "tags": ["product", "query"]}
  ],
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": {"type": "string", "description": "搜索关键词或商品名称"},
      "product_ids": {"type": "array", "description": "商品 ID 列表"}
    },
    "required": []
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "products": {"type": "array", "description": "商品列表"},
      "message": {"type": "string", "description": "回复文本"}
    }
  }
}
```

**CompareAgent Card：**
```json
{
  "name": "compare_agent",
  "description": "对比多个商品，生成对比表格和分析建议",
  "skills": [
    {"id": "compare_products", "name": "商品对比", "tags": ["compare", "product"]}
  ],
  "inputSchema": {
    "type": "object",
    "properties": {
      "products": {"type": "array", "description": "待对比的商品列表"}
    },
    "required": ["products"]
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "comparison_result": {"type": "string", "description": "对比分析结果"}
    }
  }
}
```

### 8.3 A2A Task 结构

执行计划中的步骤改为 A2A Task：

```java
public class A2ATask {
    String id;                      // 任务 ID
    String status;                  // 状态：pending / running / completed / failed
    Map<String, Object> input;      // 输入数据（从上游或用户消息获取）
    Map<String, Object> output;     // 输出数据
    String assignedAgent;           // 分配的 Agent（由 LLM 根据技能匹配）
    String skillId;                 // 使用的技能
    List<String> dependsOn;         // 依赖的任务 ID
}
```

### 8.4 执行流程

```
用户消息: "比较小米和苹果的手机"

1. 意图识别
   → Intent: compare
   → RequiredData: PRODUCT_LIST

2. LLM 生成执行计划（A2A Task 列表）
   Task 1: {
     id: "task_1",
     skillId: "search_by_keyword",  // 只指定技能，不指定 Agent
     input: {"query": "小米和苹果品牌手机"},
     dependsOn: []
   }
   
   Task 2: {
     id: "task_2",
     skillId: "compare_products",
     input: {},  // 从 task_1.output.products 获取
     dependsOn: ["task_1"]
   }

3. Agent Card 匹配
   - task_1 需要技能 "search_by_keyword"
   - 查询 Agent Registry → ProductQueryAgent 有此技能
   - assignedAgent = "product_query_agent"

4. Agent 执行
   - ProductQueryAgent 根据自己的 inputSchema 接收参数
   - 执行并产出 output.products

5. 数据传递
   - task_2.input.products = task_1.output.products
   - CompareAgent 接收 products 参数
   - 执行并产出对比结果
```

### 8.5 Agent Registry

集中管理所有 Agent Card：

```java
@Component
public class AgentRegistry {
    
    private final Map<String, AgentCard> cards = new ConcurrentHashMap<>();
    private final Map<String, ReActAgent> agents = new ConcurrentHashMap<>();
    
    /**
     * 注册 Agent
     */
    public void register(ReActAgent agent, AgentCard card) {
        cards.put(card.getName(), card);
        agents.put(card.getName(), agent);
    }
    
    /**
     * 根据技能查找 Agent
     */
    public List<AgentCard> findBySkill(String skillId) {
        return cards.values().stream()
            .filter(card -> card.getSkills().stream()
                .anyMatch(skill -> skill.getId().equals(skillId) 
                    || skill.getTags().contains(skillId)))
            .collect(Collectors.toList());
    }
    
    /**
     * 根据 inputSchema 验证参数
     */
    public boolean validateInput(String agentName, Map<String, Object> input) {
        AgentCard card = cards.get(agentName);
        // 验证必填参数是否满足
        return card.getInputSchema().getRequired().stream()
            .allMatch(input::containsKey);
    }
}
```

### 8.6 A2A Orchestrator

替代原来的 AgentOrchestrator：

```java
@Component
public class A2AOrchestrator {
    
    @Resource
    private AgentRegistry agentRegistry;
    
    @Resource
    private ChatClient chatClient;
    
    /**
     * 执行 A2A Task 列表
     */
    public List<AgentResult> execute(List<A2ATask> tasks, Map<String, Object> context) {
        Map<String, A2ATask> taskMap = tasks.stream()
            .collect(Collectors.toMap(A2ATask::getId, t -> t));
        Map<String, Map<String, Object>> outputs = new HashMap<>();
        
        // 按依赖顺序执行
        for (A2ATask task : topologicalSort(tasks)) {
            // 1. 匹配 Agent
            ReActAgent agent = matchAgent(task);
            
            // 2. 构建输入（合并上游输出）
            Map<String, Object> input = buildInput(task, outputs, context);
            
            // 3. 验证输入
            if (!agentRegistry.validateInput(agent.getName(), input)) {
                // 输入不足，可能需要请求用户
                task.setStatus("failed");
                continue;
            }
            
            // 4. 执行 Agent
            AgentResult result = agent.runAsync(input).join();
            
            // 5. 保存输出
            outputs.put(task.getId(), result.getData());
            task.setOutput(result.getData());
            task.setStatus("completed");
        }
        
        return collectResults(tasks);
    }
    
    /**
     * 根据技能匹配 Agent
     */
    private ReActAgent matchAgent(A2ATask task) {
        List<AgentCard> candidates = agentRegistry.findBySkill(task.getSkillId());
        
        // 如果有多个候选 Agent，可以根据负载、能力等策略选择
        AgentCard selected = candidates.get(0);
        task.setAssignedAgent(selected.getName());
        
        return agentRegistry.getAgent(selected.getName());
    }
    
    /**
     * 构建输入：从上游任务输出获取数据
     */
    private Map<String, Object> buildInput(A2ATask task, 
                                            Map<String, Map<String, Object>> outputs,
                                            Map<String, Object> context) {
        Map<String, Object> input = new HashMap<>(task.getInput());
        
        // 注入对话上下文
        input.put("contextData", context.get("contextData"));
        
        // 从依赖任务获取数据
        for (String depId : task.getDependsOn()) {
            Map<String, Object> depOutput = outputs.get(depId);
            if (depOutput != null) {
                // 根据 Agent 的 inputSchema 决定需要什么参数
                // 这里让 Agent 自己从 depOutput 中提取需要的字段
                input.put("upstream_" + depId, depOutput);
            }
        }
        
        return input;
    }
}
```

### 8.7 LLM Prompt 变化

**不需要再硬编码参数映射！**

```
System: 你是执行计划生成器。根据用户意图生成 A2A Task 列表。

可用技能（从 Agent Registry 获取）：
- search_by_keyword: 关键词搜索商品
- get_by_ids: 根据 ID 批量获取商品
- compare_products: 对比商品
- recommend_products: 推荐商品
- query_knowledge: 知识库查询

输出格式：
{
  "tasks": [
    {
      "id": "task_1",
      "skillId": "search_by_keyword",
      "input": {"query": "从小米和苹果品牌手机"},
      "dependsOn": []
    },
    {
      "id": "task_2", 
      "skillId": "compare_products",
      "input": {},
      "dependsOn": ["task_1"]
    }
  ]
}

注意：
- input 字段只填写从用户消息中提取的参数
- 如果参数需要从上游任务获取，input 留空，系统会自动传递
```

### 8.8 为什么使用 A2A Protocol

| 方面 | 硬编码方案 | A2A Protocol |
|------|-----------|--------------|
| 参数映射 | Prompt 硬编码 `agentParams` | Agent Card 自声明 `inputSchema` |
| Agent 匹配 | `IntentAgentResolver` Map 查找 | 技能标签匹配 |
| 新增 Agent | 修改 Prompt + Resolver | 只需注册 Agent Card |
| 可扩展性 | 低（每新增 Agent 需改代码）| 高（配置化）|
| Agent 协作 | 无标准，硬编码传递 | 标准化 Task 和 Message |

---

### 8.9 Capability 分类：端到端 vs 构建块

**决策**：每个 AgentCapability 在描述中标注 `【端到端】` 或 `【构建块】`，让 LLM 在生成计划时区分。

| 类型 | 含义 | 示例 | LLM 行为 |
|------|------|------|---------|
| 【端到端】 | 内部自行处理所有数据需求，无需前置任务 | `recommend_products`（内部自动获取画像+搜索商品） | 直接生成 1 个 task |
| 【构建块】 | 提供特定数据，通常作为其他 task 的前置步骤 | `search_by_keyword`（为 compare 提供商品列表） | 作为依赖链中的一环 |

**A2A Prompt 规则 6**：端到端能力不要生成额外的前置数据获取任务。

**为什么**：避免 `recommend` 场景下生成 3 个 task（search_by_keyword + get_user_profile + recommend_products），实际只需 1 个。

### 8.10 引用解析：位置/序号引用 → 具体 ID

**决策**：当用户用位置或序号引用历史商品（如"第1个"、"最后一个"、"刚才推荐的"），A2A LLM 必须在生成计划时从可用数据中解析出具体 product_ids，使用 `get_by_ids` 而非 `search_by_keyword`。

**A2A Prompt 规则 7**：
```
recommended_product_ids=[P01,P02,P03] + "第1个和最后一个" → input={"product_ids":["P01","P03"]}
recommended_product_ids=[P01...P06] + "比较刚才推荐的" → input={"product_ids":["P01",...,"P06"]}
```

**为什么**：`search_by_keyword` 的 query="第1个和最后一个" 无法被 ProductQueryAgent 理解。编排层有完整历史数据，应在计划阶段解析引用。

### 8.11 Task Input → Agent Prompt 的数据流

**决策**：task 的 `input.query` 和 `input.product_ids` 不覆盖原始 `message`，而是作为独立字段传递给 Agent。

ProductQueryIntentAgent 的 `buildUserMessageWithContext()` 展示层级：
```
【任务指令】{query}           ← A2A LLM 的精确指令
【指定商品ID】{product_ids}   ← 编排层解析的精确 ID
用户原始问题：{message}       ← 完整用户上下文
【历史可用数据】{contextData}
【实体信息】{entities}
```

**为什么**：保留原始 `message` 让 Agent 理解用户完整意图（如"比较"vs"查询"），同时 task 指令提供精确的执行引导。覆盖 message 会导致上下文丢失。

### 9. ProductQueryIntentAgent 改造：感知 contextData + 使用 Tool

**问题背景**：

当前 `ProductQueryIntentAgent` 的硬编码逻辑：

```java
// ❌ 错误：硬编码从 entities 取 product_name，取不到就用 message
String productName = entities.get("product_name") instanceof String
        ? (String) entities.get("product_name") : message;
```

这完全忽略了 `contextData` 中注入的历史数据，导致：
1. 没有利用 `history_recommended_product_ids` 去调用 `getProductsByIds()`
2. 直接用模糊的 message 做向量召回和关键词搜索
3. 召回结果与用户意图完全不匹配

**决策**：`ProductQueryIntentAgent` 必须改造为 ReAct 模式，注册 `ProductSearchTool`，让 LLM 自主决定：
1. 是否需要从历史数据中获取商品（通过 `contextData` 判断）
2. 调用哪个 Tool（`getProductsByIds`、`retrieveSimilarProducts`、`recommendProducts`）
3. 如何筛选商品（品牌、类目等）

**改造后的 ProductQueryIntentAgent**：

```java
@Component
public class ProductQueryIntentAgent extends ReActAgent {

    @Resource
    private ProductSearchTool productSearchTool;

    private static final String SYSTEM_PROMPT = """
            你是一个电商商品查询专家。你必须先调用工具获取商品数据，不能直接回答。
            
            【重要】你需要根据上下文决定使用哪个工具：
            1. 如果 contextData 或 entities 中有 history_recommended_product_ids，优先使用 getProductsByIds 获取这些商品
            2. 如果用户指定了筛选条件（如"vivo和小米品牌"），在获取商品后进行筛选
            3. 如果历史数据不满足需求，使用 retrieveSimilarProducts 或 recommendProducts 搜索
            
            可用工具：
            - getProductsByIds：根据商品ID列表批量获取商品详情
            - retrieveSimilarProducts：向量语义搜索
            - recommendProducts：多路召回推荐
            """;

    @PostConstruct
    public void init() {
        registerTool(productSearchTool);
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        // 步骤1: 构建包含 contextData 的用户消息
        String userMessage = buildUserMessageWithContext(params);
        
        // 步骤2: LLM + Tools（Spring AI 自动处理 Tool Calling）
        RecResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .tools(getTools().toArray())
                .user(userMessage)
                .call()
                .entity(RecResult.class);
        
        // 步骤3: 生成回复
        // ...
    }

    @SuppressWarnings("unchecked")
    private String buildUserMessageWithContext(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String message = (String) params.get("message");
        Map<String, Object> entities = (Map<String, Object>) params.get("entities");
        Map<String, Object> contextData = (Map<String, Object>) params.get("contextData");
        
        // 步骤1: 用户消息
        sb.append("用户问题：").append(message).append("\n\n");
        
        // 步骤2: 历史数据上下文（关键！）
        if (contextData != null && !contextData.isEmpty()) {
            sb.append("【历史可用数据】\n");
            for (Map.Entry<String, Object> entry : contextData.entrySet()) {
                sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
            }
            sb.append("\n");
        }
        
        // 步骤3: 实体信息
        if (entities != null && !entities.isEmpty()) {
            sb.append("【实体信息】\n");
            for (Map.Entry<String, Object> entry : entities.entrySet()) {
                sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
            }
        }
        
        sb.append("\n请根据以上信息，选择合适的工具获取商品。");
        return sb.toString();
    }
}
```

**关键改动**：
1. 注入 `ProductSearchTool`，让 LLM 有能力调用 `getProductsByIds`
2. `buildUserMessageWithContext()` 将 `contextData` 显式注入到 Prompt 中
3. System Prompt 明确指示 LLM 如何利用历史数据
4. 移除硬编码的 `entities.get("product_name")` 逻辑

### 10. contextData 注入到 Agent Prompt 的规范

**决策**：所有需要访问数据的 Agent，在构建 UserMessage 时都必须包含 `contextData`。

**注入格式**：

```
【历史可用数据】
- recommended_product_ids: [P01, P02, P03, P04, P05, P06]
- category: 手机
- brands: [小米, Apple, 华为]

【实体信息】
- history_recommended_product_ids: [P01, P02, P03, P04, P05, P06]
- history_category: 手机
```

**原因**：
1. `contextData` 是 Orchestrator 从 `extracted_info` 中读取的结构化数据
2. 同时以 `history_*` 前缀注入到 `entities`，方便不同 Agent 灵活使用
3. LLM 需要看到这些数据才能决定是否调用 `getProductsByIds`

## Risks / Trade-offs

- [Risk] LLM 生成的计划可能不合理 → **Mitigation**: Prompt 含 Few-shot 示例；计划生成失败降级为串行执行
- [Risk] 多一次 LLM 调用增加延迟 → **Mitigation**: 仅多意图场景触发（占比小）
- [Risk] Agent 内 LLM 可能不利用 contextData → **Mitigation**: 通过显式的 Prompt 指令和 `history_*` 前缀的 entities 让 Agent 天然感知历史数据
- [Risk] ProductQueryIntentAgent 改造为 Tool Calling 模式可能增加 LLM 调用次数 → **Mitigation**: 历史数据充足时，LLM 直接调用 `getProductsByIds` 一次即可完成
