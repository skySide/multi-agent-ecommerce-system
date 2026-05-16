# 智能会话系统改进方案

## 版本记录

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|---------|
| v1.0 | 2026-05-16 | 天边 | 初始版本，涵盖三个核心改进点 |

---

## 1. 智能客服窗口 UI 改进

### 1.1 现状问题

当前的 `ChatWidget.jsx` 是一个固定尺寸（400×580）的右下角悬浮小窗，功能入口分散：

- 历史会话列表通过标题栏右侧的一个小图标按钮打开，不够直观
- 没有"新增会话"的快捷入口
- 窗口尺寸固定，无法适应复杂的对话场景（如商品对比、多轮推荐）

### 1.2 改进方案

在左上角添加 **`<` 展开/收起按钮**，支持两种模式切换：

#### 紧凑模式（默认）

与当前样式一致，400×580 悬浮小窗，标题栏左上角显示 `>` 图标。

#### 展开模式

点击 `>` 后切换为展开模式：

```
┌──────────────────────────────────────────────────────┐
│  <  智能购物助手                              [_][X] │
├───────────────┬──────────────────────────────────────┤
│  菜单栏        │  对话区域                            │
│               │                                      │
│  ➕ 新增会话   │  ┌─────────────────────────────────┐ │
│               │  │ 助手: 您好！我是智能购物助手...   │ │
│  ─────────── │  └─────────────────────────────────┘ │
│               │                                      │
│  📋 历史会话   │  ┌─────────────────────────────────┐ │
│    · 会话1    │  │ 用户: 推荐几款手机               │ │
│    · 会话2    │  └─────────────────────────────────┘ │
│    · 会话3    │                                      │
│               │  ┌─────────────────────────────────┐ │
│               │  │ 助手: 为您推荐以下手机...        │ │
│               │  └─────────────────────────────────┘ │
│               │                                      │
│               │  ─────────────────────────────────── │
│               │  [输入框________________] [发送]     │
└───────────────┴──────────────────────────────────────┘
```

#### 菜单栏布局

菜单栏从上到下依次为：

1. **新增会话**：顶部放置，点击后创建空白新会话，清空当前对话区域
2. **分隔线**
3. **历史会话列表**：展示用户最近 20 条会话，每条显示摘要、轮数、时间、状态；点击切换到对应会话，当前会话高亮

#### 实现要点

- 展开模式下窗口尺寸建议 900×600，左侧菜单栏宽度约 220px
- 菜单栏可以再次点击 `<` 收起回到紧凑模式
- 两种模式切换时保持当前会话状态不丢失
- 历史会话列表支持滚动，按时间倒序排列
- 新增会话按钮放在历史会话上方，方便用户快速创建新会话

---

## 2. 会话突然结束检测逻辑改进

### 2.1 现状问题

当前 `conversation-quality-system.md` 第 6.1 节描述了两种 abrupt_end 触发方式：

1. **前端主动上报**（主要方式）：ChatWidget 组件卸载时调用 `abandonSession`
2. **后端定时任务**（兜底）：超过 30 分钟无新消息且 `status=1` 的会话，自动标记为 `abrupt_end`

**后端定时任务方案的问题**：

> 用户可能只是暂时离开（去开会、接电话、下班），30 分钟后甚至 1 天后回来继续这个会话。如果后端定时任务已经将会话标记为"突然结束"，用户回来继续对话时就会产生矛盾——明明是同一个会话的正常延续，却被标记为"突然结束"。

核心矛盾：**"用户暂时离开后返回"和"用户真正放弃会话"在行为特征上完全一致（都是长时间不发送消息），无法通过时间间隔来区分。**

### 2.2 改进方案

#### 取消后端定时任务兜底扫描

**不使用** `SESSION_TIMEOUT_MINUTES` 常量和定时任务来做自动 abrupt_end 判定。理由如上所述。

#### 保留前端主动上报作为 abrupt_end 的唯一触发方式

ChatWidget 组件卸载时（用户关闭窗口 / 切换页面 / 关闭浏览器），如果满足条件则上报：

```javascript
// 组件卸载时
useEffect(() => {
  return () => {
    if (sessionId && messages.length > 1) {
      api.abandonSession(sessionId, userId).catch(() => {})
    }
  }
}, [sessionId, messages.length, userId])
```

此时执行：
- `conversationService.endSession(sessionId)` → 设置 `status = 0`
- `sessionQualityMetricsService.recordAbruptEnd(...)` → 记录质量事件

#### 新增会话恢复机制

当用户对一个 `status = 0` 的会话发送新消息时（即用户在 abrupt_end 之后回来继续对话）：

```
用户发送消息 → 后端 chat() 方法
  ├── 查找到 session，status = 0
  ├── 检查 update_time 与当前时间的间隔
  │   ├── 间隔 > 30 分钟 → 视为"会话恢复"
  │   │   ├── 重新激活会话：status 设回 1
  │   │   └── 记录质量事件：metric_type = "session_resumed"
  │   │       metric_value = {"gap_minutes": 120, "had_abrupt_end": true}
  │   └── 间隔 ≤ 30 分钟 → 直接重新激活，不额外记录
  └── 正常处理消息
```

#### 新增质量指标类型

在 `QualityConstants` 中新增：

```java
/** 会话恢复（用户离开超过30分钟后回来继续对话） */
public static final String METRIC_SESSION_RESUMED = "session_resumed";
```

#### 判断逻辑总结

| 场景 | 判定 | 数据记录 |
|------|------|---------|
| 用户关闭窗口/切换页面 | abrupt_end | `session_quality_metrics` (metric_type=abrupt_end) |
| 用户在 30 分钟内回来继续 | 正常继续 | 无额外记录 |
| 用户在 30 分钟后回来继续 | 会话恢复 | `session_quality_metrics` (metric_type=session_resumed) |
| 用户主动结束会话 | 正常结束 | status=0，无质量事件 |

### 2.3 方案优势

1. **不会误判**：不会因为用户暂时离开而将会话标记为"突然结束"
2. **可追溯**：通过 `session_resumed` 事件可以统计"有多少用户离开后又回来"，这是正向指标
3. **用户体验好**：用户任何时候回来都能无缝继续对话
4. **数据准确**：`abrupt_end` 仅在前端主动上报时记录，代表用户真正的"放弃"行为

---

## 3. 离线分析结果存储与指标看板

### 3.1 现状问题

当前系统的反馈数据已经落表（`chat_feedback` + `session_quality_metrics`），但存在以下缺口：

1. **分析结果无处存储**：离线任务分析"哪个 Agent 差评最多"、"哪种差评原因最频繁"等，结果没有落表
2. **看板缺失**：前端没有任何页面展示这些质量指标，只能人工查 MySQL
3. **无法按 Agent 维度分析**：`chat_feedback` 表只记录了 session 维度的反馈，没有直接关联到具体的 Agent（recommend / knowledge_query / product_query / compare 等），需要 JOIN `session_quality_metrics` 或解析 `round_intents` 才能关联

### 3.2 改进方案

#### 3.2.1 新建 agent_quality_analysis 表

```sql
CREATE TABLE agent_quality_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    agent_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'Agent名称: recommend/product_query/knowledge_query/compare/chitchat',
    analysis_date DATE NOT NULL COMMENT '分析日期',
    total_feedback INT DEFAULT 0 COMMENT '总反馈数',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    dislike_count INT DEFAULT 0 COMMENT '点踩数',
    satisfaction_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '满意度(%)',
    top_dislike_reasons TEXT COMMENT '差评原因Top5 JSON: [{"reason":"inaccurate","count":15},...]',
    abrupt_end_count INT DEFAULT 0 COMMENT '突然结束会话数',
    repeated_question_count INT DEFAULT 0 COMMENT '重复提问次数',
    transfer_to_human_count INT DEFAULT 0 COMMENT '转人工次数',
    total_sessions INT DEFAULT 0 COMMENT '总会话数',
    avg_rounds DECIMAL(5,1) DEFAULT 0.0 COMMENT '平均对话轮数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_agent_date (agent_name, analysis_date),
    INDEX idx_analysis_date (analysis_date),
    INDEX idx_agent_name (agent_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent质量分析结果表（离线任务产出）';
```

#### 3.2.2 新建离线分析定时任务

新增 `AgentQualityAnalysisTask`，每天凌晨 2:00 执行：

```
执行流程：
1. 从 chat_feedback 中按 session_id 关联 session_quality_metrics
2. 通过 session_quality_metrics 中的 metric_type 或 round_intents 解析关联到具体 Agent
3. 按 agent_name 分组统计：
   - 总反馈数、点赞数、点踩数、满意度
   - 差评原因分布（feedback_reason 字段分词统计）
   - 突然结束数、重复提问数、转人工数
   - 总会话数、平均轮数
4. 写入 agent_quality_analysis 表（按 agent_name + analysis_date 去重，覆盖更新）
```

#### 3.2.3 新增后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/quality/agent-stats?date=2026-05-16` | 获取某日各 Agent 质量分析数据 |
| GET | `/api/v1/quality/agent-stats/{agentName}?days=30` | 获取某 Agent 近 N 天的质量趋势 |
| GET | `/api/v1/quality/overview?days=7` | 获取全局质量概览（满意度趋势、异常事件趋势） |

#### 3.2.4 前端指标看板页面

新增 `QualityDashboardPage.jsx`，路由 `/quality`，包含以下模块：

```
┌─────────────────────────────────────────────────────────────┐
│  智能会话质量看板                                            │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ 满意率    │ │ 总反馈数  │ │ 突然结束  │ │ 转人工率  │       │
│  │ 87.5%    │ │ 1,234    │ │ 45       │ │ 3.2%     │       │
│  │ ↑ 2.1%   │ │ ↑ 15%    │ │ ↓ 5      │ │ → 持平   │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│                                                             │
│  ┌──────────────────────────────┐ ┌──────────────────────┐  │
│  │ 各 Agent 满意度对比（柱状图） │ │ 差评原因分布（饼图）  │  │
│  │                              │ │                      │  │
│  │  recommend  ████████ 92%    │ │  inaccurate   35%   │  │
│  │  knowledge  ██████   85%    │ │  irrelevant   25%   │  │
│  │  compare    ████████ 90%    │ │  incomplete   20%   │  │
│  │  chitchat   █████████ 95%   │ │  too_generic  12%   │  │
│  │  product    █████    78%    │ │  outdated      8%   │  │
│  │                              │ │                      │  │
│  └──────────────────────────────┘ └──────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 满意度趋势（折线图，近30天）                          │   │
│  │                                                      │   │
│  │  100%                                                │   │
│  │   80%  ·──·──·──·──·──·──·──·──·                    │   │
│  │   60%                                                │   │
│  │       05-01  05-08  05-15  05-22  05-29              │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 异常会话明细列表（Tab切换：突然结束/重复提问/转人工）  │   │
│  │ session_id  │ user_id │ agent  │ 轮数 │ 时间        │   │
│  │ abc123...   │ u001    │ rec    │   8  │ 05-16 14:30 │   │
│  │ def456...   │ u002    │ knwl   │   3  │ 05-16 14:25 │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 实现步骤

| 阶段 | 内容 | 影响范围 |
|------|------|---------|
| P1 | 新建 `agent_quality_analysis` 表 | DB Schema |
| P2 | 新建 `AgentQualityAnalysis` Entity + Mapper | Java |
| P3 | 新建 `AgentQualityAnalysisTask` 离线分析定时任务 | Java Task |
| P4 | 新建 `QualityController` + `QualityService` API | Java Controller/Service |
| P5 | 前端新建 `QualityDashboardPage` + 路由 + 图表 | Frontend |
| P6 | 导航菜单追加"质量看板"入口 | Frontend App.jsx |

---

## 4. 改进优先级总结

| 优先级 | 改进项 | 复杂度 | 影响 |
|--------|--------|--------|------|
| **P0** | 会话恢复机制（问题2） | 低 | 核心逻辑修正，避免数据错误 |
| **P1** | 智能客服窗口展开模式（问题1） | 中 | 用户体验提升，前端改动为主 |
| **P2** | 离线分析结果存储（问题3-P1~P3） | 中 | 数据闭环基础 |
| **P3** | 质量看板页面（问题3-P4~P6） | 高 | 可视化呈现，前后端联动 |
