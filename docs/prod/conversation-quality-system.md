# 智能会话质量反馈系统设计文档

## 版本记录

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|---------|
| v1.0 | 2026-05-15 | 天边 | 初始版本 |

---

## 1. 概述

### 1.1 背景

当前智能会话系统已具备基础的点赞/拉踩功能，但存在以下不足：

1. 无法查看历史会话列表，用户只能看到当前会话
2. 点赞/拉踩后缺乏详细反馈收集（如：哪些方面不足）
3. 缺少会话质量衡量指标（重复提问、会话突然结束、转人工等）
4. 收集到的反馈数据未形成闭环，无法驱动回答质量提升
5. 会话没有"停止生成"按钮，用户无法中断生成中的回复

### 1.2 目标

- 支持用户查看和切换历史会话
- 点赞/拉踩后弹出反馈弹窗，收集预设原因（多选）+ 自由文本
- 发送消息时支持"停止生成"
- 建立会话质量指标体系：重复提问率、会话突然结束、转人工率
- 建立反馈数据闭环，将低质量 case 用于优化 Agent 回答

---

## 2. 系统架构

### 2.1 整体流程

```
用户发送消息 → 意图识别 → 路由到子Agent → 生成回复 → 返回给前端
                                    ↓
                          异步：重复提问检测
                          （基于 round_intents 字段）
                          异步：记录质量事件
                                    
用户操作反馈 → 弹出反馈弹窗 → 提交详细反馈 → 写入 chat_feedback
                                    
离线分析 → 按意图聚合差评 → 优化Prompt/召回策略
```

### 2.2 数据流向

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  ChatWidget  │────▶│  Controller   │────▶│   Service/Agent  │
│  (前端)      │     │  (API层)      │     │   (业务层)       │
└─────────────┘     └──────────────┘     └─────────────────┘
       │                                         │
       │ 反馈弹窗                                 │ 异步
       │ 停止生成                                 ├── 重复检测 (EmbeddingService + 余弦相似度)
       │ 会话列表                                 ├── 质量事件 (session_quality_metrics)
       │ 会话结束上报                              └── round_intents 更新
       ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  前端状态    │     │   MySQL       │     │   EmbeddingService│
│  messages[] │     │  chat_feedback│     │   (bge-large-zh)  │
│  sessions[] │     │  quality_     │     │   文本 → 向量     │
│  feedback   │     │  metrics      │     │                  │
└─────────────┘     │  conversation │     └─────────────────┘
                    │  _session     │
                    │  (+round_     │
                    │  intents)     │
                    └──────────────┘
```

---

## 3. 数据模型设计

### 3.1 chat_feedback 表变更

新增两个字段：

```sql
ALTER TABLE chat_feedback 
ADD COLUMN feedback_reason VARCHAR(200) DEFAULT '' COMMENT '反馈原因标签，多选用逗号分隔',
ADD COLUMN feedback_comment TEXT COMMENT '用户自由填写的反馈内容';
```

### 3.2 feedback_reason 枚举值

| 标签值 | 说明 | 适用场景 |
|--------|------|---------|
| `inaccurate` | 回答不准确 | 拉踩 |
| `irrelevant` | 答非所问 | 拉踩 |
| `incomplete` | 信息不完整 | 拉踩 |
| `too_generic` | 回答太笼统 | 拉踩 |
| `outdated` | 信息过时 | 拉踩 |
| `helpful` | 有帮助 | 点赞 |
| `saved_time` | 节省了时间 | 点赞 |
| `other` | 其他 | 点赞/拉踩 |

### 3.3 session_quality_metrics 表（新增）

```sql
CREATE TABLE session_quality_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话ID',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    metric_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT '指标类型: repeated_question/abrupt_end/transfer_to_human/low_engagement',
    metric_value TEXT COMMENT '指标详情JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_metric_type (metric_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话质量指标表';
```

**metric_type 枚举**：

| 值 | 说明 | metric_value 示例 |
|----|------|-------------------|
| `repeated_question` | 重复提问 | `{"current_round":5,"similar_round":2,"intent":"recommend","current_entities":{"category":"手机"},"similar_entities":{"category":"手机","brand":"华为"},"similarity":0.92}` |
| `abrupt_end` | 会话突然结束 | `{"last_round":8,"session_duration_sec":320}` |
| `transfer_to_human` | 转人工 | `{"trigger":"user_request","round":6}` |
| `low_engagement` | 低参与度 | `{"message_count":1}` |

### 3.4 conversation_session 表变更

新增字段：

```sql
ALTER TABLE conversation_session 
ADD COLUMN round_intents TEXT COMMENT '每轮意图+实体JSON数组，最近10轮。[{"round":0,"intent":"recommend","entities":{...}}]';
```

**round_intents 格式**：

```json
[
  {"round": 0, "intent": "recommend", "entities": {"category": "手机", "brand": "华为"}},
  {"round": 2, "intent": "compare", "entities": {"product_names": ["魅族", "apple"]}},
  {"round": 4, "intent": "recommend", "entities": {"category": "手机"}}
]
```

**与 extracted_info 的关系**：

| 字段 | 存储方式 | 用途 |
|------|---------|------|
| `extracted_info` | **累加合并**（putAll），跨轮累积 | 供 Agent 使用，获取用户偏好全貌 |
| `round_intents` | **逐轮追加**，保留最近10轮 | 供重复检测使用，逐轮比对 |

两者互不干扰：
- `extracted_info` 保持原有逻辑不变，Agent 路由时使用
- `round_intents` 新增，仅用于重复提问检测

**为什么 round_intents 独立于 extracted_info**：
- `extracted_info` 的累加特性使得无法区分"本轮实体"和"前几轮的实体"
- 重复检测需要逐轮比对"意图是否相同 + 实体是否相同"
- 例如：第1轮推荐手机(category=手机), 第2轮比较魅族和apple(product_names=[魅族,apple]), 第3轮推荐手机(category=手机)
  - extracted_info = {category:"手机", product_names:["魅族","apple"]} — 无法判断第3轮和第1轮实体相同
  - round_intents 逐轮存储 — 可精确判断第3轮与第1轮意图相同、实体重叠

---

## 4. 重复提问检测方案

### 4.1 判定逻辑（三层判断）

```
输入：当前轮 intent + entities + 用户消息文本

Step 1: 从 round_intents 中选择最近5轮的用户消息记录
Step 2: 逐轮比对：
    if (intent 相同):
        if (entities 关键字段有交集):
            → 调用 EmbeddingService 计算文本相似度
            → 相似度 > 0.85 → 重复提问
            → 相似度 ≤ 0.85 → 可能是同主题不同角度，不算重复
        else:
            → 渐进追问（同意图但不同实体），不算重复
    else:
        → 话题切换，不算重复
```

### 4.2 渐进追问 vs 重复提问示例

| 轮次 | 用户消息 | intent | entities | 判定 |
|------|---------|--------|----------|------|
| 1 | "推荐几个手机" | recommend | {category:"手机"} | - |
| 2 | "比较魅族和apple手机" | compare | {product_names:["魅族","apple"]} | intent不同 → 话题切换 |
| 3 | "比较魅族mate13和apple15" | compare | {product_names:["魅族mate13","apple15"]} | intent相同但entities不同 → 渐进追问 |
| 4 | "推荐几个手机" | recommend | {category:"手机"} | intent相同 + entities重叠 + 文本相似 → **重复提问** |

### 4.3 执行时机

**异步执行**，不阻塞对话回复。在 `ConversationAgent.execute()` 返回后，由线程池异步执行。

### 4.4 Embedding 计算

- 使用已有的 `EmbeddingService`（bge-large-zh-v1.5）生成文本向量
- 使用余弦相似度计算两段文本的相似度
- 相似度阈值：**0.85**（可调整）

### 4.5 关于 Redis 缓存的说明

> **设计预留**：未来可引入 Redis 缓存最近5条消息的 embedding，避免每次检测都重新调用 Embedding API。
> Key: `session:{sessionId}:embeddings`, TTL: 30分钟。
> 当前版本直接调用 EmbeddingService 实时计算。

---

## 5. 停止生成方案

### 5.1 前端

发送按钮在等待回复期间切换为"停止"按钮：
- 图标从 `SendOutlined` 切换为暂停/停止图标
- 按钮添加旋转动画表明生成中
- 点击停止后发送取消请求

### 5.2 后端

- 每个 session 的 `CompletableFuture` 存储在 `ConcurrentHashMap<String, CompletableFuture<AgentResult>>` 中
- 新增 `POST /api/v1/conversation/{sessionId}/cancel` 接口
- 调用 `future.cancel(true)` 中断执行
- 对话回复返回"已停止生成"
- 取消后的 future 从 Map 中移除，用户可继续发送新消息

---

## 6. 会话突然结束检测

### 6.1 触发方式

**前端主动上报**（主要方式）：
- ChatWidget 组件卸载时（用户关闭窗口/切换到其他页面），如果 session 状态为"进行中"且有消息交互，发送 `POST /api/v1/conversation/{sessionId}/abandon`

**后端定时任务**（兜底）：
- 超过30分钟无新消息且 status=1 的会话，自动标记为 abrupt_end

### 6.2 数据记录

写入 `session_quality_metrics` 表，metric_type = `abrupt_end`。

---

## 7. 转人工检测

### 7.1 触发条件

1. **用户主动请求**：意图识别新增 `transfer_to_human`，用户明确要求转人工
2. **连续差评触发**：同一 session 内连续2次拉踩后，系统主动提示可转人工（暂不实现自动转接逻辑，仅提示）

### 7.2 数据记录

写入 `session_quality_metrics` 表，metric_type = `transfer_to_human`。

---

## 8. 反馈数据闭环

### 8.1 实时闭环

同一 session 内，当检测到差评 + 重复提问时，在下一轮 prompt 中注入提示：

```
【系统提示】用户对上次回答不满意（原因：{feedback_reason}），并重复了类似问题。
请换一种方式回答，直接承认之前回答的不足，并给出更准确的信息。
```

### 8.2 离线闭环

| 分析任务 | 周期 | 内容 |
|---------|------|------|
| 按意图统计差评率 | 每日 | 发现哪个 Agent 差评率高，针对性优化 Prompt |
| 差评原因分布 | 每周 | 了解用户最不满意的方面 |
| 低质量 Case 汇总 | 每周 | 抽取差评样例，人工审核后优化召回/排序策略 |
| 满意率趋势 | 实时看板 | 通过 `/api/v1/chat/feedback/stats` 监控 |

### 8.3 高质量回复沉淀

将**高质量好评回复**（点赞 + "有帮助"标签）存入 SimpleVectorStore：
- 用户问题 → embedding → 作为 query
- AI 回复 → 作为 document metadata
- 遇到相似问题时，检索优秀回复作为参考
- 质量阈值：同时获得点赞 + "有帮助"/"节省了时间"标签

---

## 9. API 接口设计

### 9.1 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/conversation/sessions?userId=X` | 获取用户历史会话列表 |
| POST | `/api/v1/conversation/{sessionId}/cancel` | 停止当前会话的生成 |
| POST | `/api/v1/conversation/{sessionId}/abandon` | 上报会话突然结束 |
| GET | `/api/v1/chat/feedback/reasons` | 获取反馈原因选项列表（拉踩/点赞） |

### 9.2 修改接口

| 方法 | 路径 | 变更说明 |
|------|------|---------|
| POST | `/api/v1/chat/feedback` | FeedbackRequestDTO 新增 `feedbackReason` 和 `feedbackComment` 字段 |

---

## 10. 前端交互设计

### 10.1 反馈弹窗

```
┌─────────────────────────────────────┐
│  感谢您的反馈！                     │
│                                     │
│  您认为回答存在哪些问题？（可多选）   │
│  ☐ 回答不准确                      │
│  ☐ 答非所问                        │
│  ☐ 信息不完整                      │
│  ☐ 回答太笼统                      │
│  ☐ 信息过时                        │
│  ☐ 其他                            │
│                                     │
│  补充说明（选填）：                 │
│  ┌─────────────────────────────┐    │
│  │                             │    │
│  └─────────────────────────────┘    │
│                                     │
│              [跳过]  [提交]         │
└─────────────────────────────────────┘
```

交互规则：
- 拉踩后弹出弹窗，原因选项使用多选框
- 点赞后也可弹出（可选填写），但原因选项不同（有帮助/节省了时间/其他）
- 点击×或"跳过"直接关闭，不上报详细反馈（基础 rating 已写入）
- 点击"提交"将原因和备注写入 chat_feedback

### 10.2 停止生成按钮

- 发送消息后，发送按钮 icon 切换为停止图标，spin 动画表示生成中
- 点击停止后发送取消请求，前端显示"已停止生成"
- 用户可继续发送新消息

### 10.3 历史会话入口

- 聊天窗口标题栏增加"历史"按钮（HistoryOutlined 图标）
- 点击展开会话列表面板（Drawer 或 Dropdown）
- 每条显示：摘要（summary）、时间、状态
- 点击可加载历史会话继续对话
- 当前会话高亮标识

---

## 11. 常量定义

```java
package com.ecommerce.common.constants;

/**
 * 会话质量相关常量
 */
public class QualityConstants {

    /** 重复检测滑动窗口大小 */
    public static final int REPEATED_DETECTION_WINDOW = 5;

    /** 重复提问文本相似度阈值 */
    public static final double REPEATED_SIMILARITY_THRESHOLD = 0.85;

    /** round_intents 保留轮数 */
    public static final int ROUND_INTENTS_MAX_SIZE = 10;

    /** 会话超时时间（分钟），用于兜底检测 abrupt_end */
    public static final long SESSION_TIMEOUT_MINUTES = 30;

    // ===== 质量指标类型 =====
    public static final String METRIC_REPEATED_QUESTION = "repeated_question";
    public static final String METRIC_ABRUPT_END = "abrupt_end";
    public static final String METRIC_TRANSFER_TO_HUMAN = "transfer_to_human";
    public static final String METRIC_LOW_ENGAGEMENT = "low_engagement";

    // ===== 反馈原因 =====
    public static final String REASON_INACCURATE = "inaccurate";
    public static final String REASON_IRRELEVANT = "irrelevant";
    public static final String REASON_INCOMPLETE = "incomplete";
    public static final String REASON_TOO_GENERIC = "too_generic";
    public static final String REASON_OUTDATED = "outdated";
    public static final String REASON_HELPFUL = "helpful";
    public static final String REASON_SAVED_TIME = "saved_time";
    public static final String REASON_OTHER = "other";

    private QualityConstants() {
        // 工具类，禁止实例化
    }
}
```

---

## 12. 实现计划

| 阶段 | 内容 | 影响范围 |
|------|------|---------|
| P1 | Schema变更（chat_feedback + session_quality_metrics + round_intents） | DB, Entity |
| P2 | chat_feedback 增强（reason + comment） | DTO, Service, Controller |
| P3 | 会话列表API | Controller, Service, Mapper |
| P4 | 停止生成（前后端） | Agent, Controller, ChatWidget |
| P5 | 重复提问检测（异步，基于 round_intents + EmbeddingService） | ConversationAgent |
| P6 | 质量指标记录服务 | Entity, Service, Mapper |
| P7 | 前端：反馈弹窗 + 历史会话 + 停止按钮 + abandon上报 | ChatWidget, api.js |
