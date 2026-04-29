# 需求文档：对话满意度统计（chat-satisfaction）

## 简介

本功能为多 Agent 电商推荐系统新增"对话满意度统计"能力。通过分析同一 session 内的对话轮次、满意度信号组合以及对话后的用户购买/加购行为，量化智能对话的服务质量，并通过统计接口向运营人员暴露解决率、平均轮次、追问率、转化率等核心指标。

系统已有 `conversation_session` 表（含 `dialogue_history`、`extracted_info`、`status` 字段）和 `user_behavior` 表（含 `behavior_type`: view/cart/purchase/favorite/search/chat），本功能在此基础上扩展，不破坏现有对话流程。

---

## 词汇表

- **Satisfaction_Analyzer**：对话满意度分析服务，负责在每轮对话结束后计算并持久化满意度指标。
- **Statistics_API**：满意度统计查询接口，向前端/运营后台暴露聚合指标。
- **Session**：一次完整的用户对话会话，对应 `conversation_session` 表中的一条记录。
- **轮次（Turn）**：一次用户消息 + 一次助手回复，构成一个对话轮次。
- **意图（Intent）**：系统识别出的用户目的，取值为 recommend / product_query / knowledge_query / compare / chitchat。
- **否定信号（Negative_Signal）**：用户消息中包含明确否定词（如"没用"、"不对"、"不是这个意思"、"你没理解"），表示用户对上一轮回答明确不满意的高权重信号。
- **深入探索（Deep_Exploration）**：同一 Session 内同一意图连续出现，属于中性偏正向的低权重信号，表示用户在深入了解某类商品，不等同于不满意。
- **转化行为（Conversion_Behavior）**：用户在 Session 结束后 30 分钟内发生的 cart 或 purchase 类型行为，属于高权重正向信号。
- **单轮放弃（Single_Turn_Abandon）**：Session 内仅有 1 轮对话即结束，属于中权重的可能放弃信号。
- **解决率（Resolution_Rate）**：Session 内未出现否定信号的 Session 占比。
- **追问率（Follow-up_Rate）**：Session 内至少发生一次同意图连续出现的 Session 占比。
- **平均轮次（Avg_Turns）**：统计周期内所有 Session 的平均对话轮次。
- **转化率（Conversion_Rate）**：发生转化行为的 Session 占比。
- **chat_satisfaction_record**：新增数据库表，存储每个 Session 的满意度分析结果。
- **Memory_Manager**：三层记忆管理组件，负责短期记忆注入、会话记忆累积和长期记忆读取。
- **短期记忆（Short_Term_Memory）**：当前 Session 内的对话历史（`dialogue_history`），最多保留最近 10 轮。
- **会话记忆（Session_Memory）**：当前 Session 内跨轮次累积的实体信息（`extracted_info`），采用新值覆盖旧值、保留未覆盖字段的合并策略。
- **长期记忆（Long_Term_Memory）**：跨 Session 的用户历史偏好，存储于 `user_profile` 表，对话开始时读取作为背景上下文。

---

## 需求列表

### 需求 1：对话轮次记录

**用户故事：** 作为运营人员，我希望系统能准确记录每个 Session 的对话轮次数，以便后续统计分析。

#### 验收标准

1. WHEN 用户发送一条消息且助手完成回复，THE Satisfaction_Analyzer SHALL 将该 Session 的轮次计数加 1。
2. THE Satisfaction_Analyzer SHALL 将轮次数持久化到 `chat_satisfaction_record` 表的 `turn_count` 字段。
3. IF `conversation_session` 记录不存在，THEN THE Satisfaction_Analyzer SHALL 记录错误日志并跳过本次轮次更新，不影响主对话流程。

---

### 需求 2：满意度信号检测

**用户故事：** 作为运营人员，我希望系统能通过多维度信号组合评估用户满意度，而不是简单地将追问等同于不满意，以便更准确地衡量对话质量。

#### 验收标准

1. WHEN 用户消息包含否定词（"没用"、"不对"、"不是这个意思"、"你没理解"或语义等价表达），THE Satisfaction_Analyzer SHALL 将该轮次标记为高权重不满意信号，并将 `negative_signal_count` 加 1。
2. WHEN 同一 Session 内当前轮次的意图与上一轮次的意图相同，THE Satisfaction_Analyzer SHALL 将该 Session 的 `follow_up_count` 加 1，并将该信号标记为低权重中性偏正向（深入探索）。
3. WHEN `conversation_session` 的 `status` 变更为结束且 `turn_count` 等于 1，THE Satisfaction_Analyzer SHALL 将该 Session 的 `single_turn_abandon` 标记为 `true`，作为中权重可能放弃信号。
4. WHEN `user_behavior` 表中出现 `behavior_type` 为 cart 或 purchase 的记录，且该记录的 `create_time` 在对应用户最近一次 Session 结束后 30 分钟以内，THE Satisfaction_Analyzer SHALL 将该 Session 的 `has_conversion` 标记为 `true`，作为高权重正向转化信号。
5. THE Satisfaction_Analyzer SHALL 仅对 recommend、product_query、knowledge_query、compare 四种意图执行深入探索检测，chitchat 意图不计入 `follow_up_count`。
6. THE Satisfaction_Analyzer SHALL 将 `negative_signal_count` 大于 0 的 Session 标记为 `has_negative_signal = true`，用于解决率计算。
7. THE `chat_satisfaction_record` 表 SHALL 新增 `negative_signal_count`、`single_turn_abandon`、`has_negative_signal` 字段以支持多维度信号存储。

---

### 需求 3：转化行为关联

**用户故事：** 作为运营人员，我希望系统能将对话 Session 与后续购买/加购行为关联，以便衡量对话对成交的贡献。

#### 验收标准

1. WHEN `conversation_session` 的 `status` 变更为 0（结束），THE Satisfaction_Analyzer SHALL 记录 Session 结束时间戳到 `chat_satisfaction_record` 表的 `session_end_time` 字段。
2. WHEN `user_behavior` 表中出现 `behavior_type` 为 cart 或 purchase 的记录，且该记录的 `create_time` 在对应用户最近一次 Session 结束后 30 分钟以内，THE Satisfaction_Analyzer SHALL 将该 Session 的 `has_conversion` 标记为 `true`。
3. IF 同一 Session 关联到多条转化行为，THEN THE Satisfaction_Analyzer SHALL 仅将 `has_conversion` 置为 `true` 一次，不重复计数。
4. THE Satisfaction_Analyzer SHALL 在用户行为写入后的异步任务中执行转化关联，不阻塞行为记录的主流程。

---

### 需求 4：满意度数据持久化

**用户故事：** 作为系统，我希望每个 Session 的满意度分析结果被完整存储，以便支持多维度查询。

#### 验收标准

1. THE Satisfaction_Analyzer SHALL 为每个新建的 `conversation_session` 在 `chat_satisfaction_record` 表中创建一条对应记录。
2. THE `chat_satisfaction_record` 表 SHALL 包含以下字段：`session_id`、`user_id`、`turn_count`、`follow_up_count`、`has_follow_up`、`has_conversion`、`dominant_intent`、`session_end_time`、`negative_signal_count`、`has_negative_signal`、`single_turn_abandon`、`create_time`、`update_time`。
3. THE Satisfaction_Analyzer SHALL 在每轮对话结束后以 upsert 方式更新 `chat_satisfaction_record`，保证数据最终一致。
4. IF 数据库写入失败，THEN THE Satisfaction_Analyzer SHALL 记录错误日志并重试最多 3 次，重试间隔为 1 秒。

---

### 需求 5：统计指标计算

**用户故事：** 作为运营人员，我希望系统能按时间范围聚合计算解决率、平均轮次、追问率、转化率，以便评估对话服务质量。

#### 验收标准

1. WHEN Statistics_API 收到包含 `startTime` 和 `endTime` 参数的查询请求，THE Statistics_API SHALL 基于 `chat_satisfaction_record.create_time` 在指定时间范围内的记录计算以下指标：
   - 解决率 = (has_negative_signal 为 false 的 Session 数) / (总 Session 数)
   - 追问率 = (follow_up_count 大于 0 的 Session 数) / (总 Session 数)
   - 平均轮次 = SUM(turn_count) / COUNT(session_id)
   - 转化率 = (has_conversion 为 true 的 Session 数) / (总 Session 数)
2. THE Statistics_API SHALL 支持按 `intent` 参数过滤，仅统计 `dominant_intent` 与指定意图匹配的 Session。
3. THE Statistics_API SHALL 在响应中包含 `total_sessions`（总 Session 数）字段，用于校验分母。
4. WHEN 查询时间范围内无 Session 记录，THE Statistics_API SHALL 返回所有指标为 0 的合法响应，不返回错误。
5. THE Statistics_API SHALL 在 500ms 内返回响应（基于 MySQL 索引查询，数据量 ≤ 100 万条）。

---

### 需求 6：按意图分组统计

**用户故事：** 作为运营人员，我希望能按意图类型查看各项指标，以便定位哪类问题回答质量最差。

#### 验收标准

1. WHEN Statistics_API 收到 `groupByIntent=true` 参数，THE Statistics_API SHALL 按 `dominant_intent` 分组返回每种意图的解决率、追问率、平均轮次、转化率。
2. THE Statistics_API SHALL 在分组结果中包含每组的 `session_count` 字段。
3. THE Statistics_API SHALL 仅返回在指定时间范围内存在 Session 记录的意图分组，不返回空分组。

---

### 需求 7：统计数据查询接口

**用户故事：** 作为前端开发者，我希望有标准化的 REST 接口获取满意度统计数据，以便在运营看板中展示。

#### 验收标准

1. THE Statistics_API SHALL 提供 `GET /api/chat/satisfaction/stats` 接口，接受 `startTime`（ISO 8601）、`endTime`（ISO 8601）、`intent`（可选）、`groupByIntent`（可选，布尔值）参数。
2. THE Statistics_API SHALL 以 JSON 格式返回响应，结构遵循系统现有 `Result<T>` 包装规范。
3. IF `startTime` 或 `endTime` 格式非法，THEN THE Statistics_API SHALL 返回 HTTP 400 及描述性错误信息。
4. IF `startTime` 晚于 `endTime`，THEN THE Statistics_API SHALL 返回 HTTP 400 及错误信息 "startTime 不能晚于 endTime"。
5. THE Statistics_API SHALL 要求请求携带有效 JWT Token，无效 Token 返回 HTTP 401。

---

### 需求 8：前端满意度看板

**用户故事：** 作为运营人员，我希望在用户中心页面看到对话满意度的可视化统计，以便快速了解服务质量。

#### 验收标准

1. THE 前端看板 SHALL 在现有 `UserCenterPage` 中新增"对话满意度"Tab，展示解决率、追问率、平均轮次、转化率四项核心指标。
2. THE 前端看板 SHALL 提供时间范围选择器（默认最近 7 天），用户修改时间范围后 SHALL 自动重新请求统计数据。
3. WHEN 统计数据加载中，THE 前端看板 SHALL 显示 Ant Design Skeleton 占位组件。
4. IF 接口返回错误，THEN THE 前端看板 SHALL 显示 Ant Design Alert 错误提示，不崩溃页面。
5. WHERE `groupByIntent=true` 数据可用，THE 前端看板 SHALL 以 Ant Design Table 展示按意图分组的统计明细。

---

### 需求 9：三层记忆系统

**用户故事：** 作为用户，我希望对话助手能记住当前对话的上下文、本次会话中我提过的偏好，以及我历史上的购物偏好，以便获得连贯、个性化的回答。

#### 背景说明

当前 `ConversationServiceImpl` 存在三类记忆缺失问题：
- `handleKnowledgeQuery`、`handleRecommend`、`handleProductQuery` 接收了 `history` 参数但未传给 LLM，导致知识问答和推荐场景无上下文；意图识别时也未传入 `history`，导致"那换货呢"等指代无法理解。
- `extracted_info` 存储了上一轮提取的实体，但下一轮 `handleRecommend` 未读取，导致跨轮次实体（如预算）丢失。
- `user_profile` 表存有用户历史偏好，但 `ConversationServiceImpl` 未注入 `UserProfileService`，对话时完全不感知用户历史偏好。

#### 验收标准

1. WHEN 任意意图处理方法（handleRecommend、handleProductQuery、handleKnowledgeQuery、handleCompare、handleChitchat）被调用，THE Memory_Manager SHALL 将当前 Session 的 `dialogue_history` 注入对应 LLM prompt 的对话历史部分。
2. WHEN 意图识别（recognizeIntent）被调用，THE Memory_Manager SHALL 将最近 3 轮对话历史注入意图识别 prompt，以支持指代消解（如"那换货呢"）。
3. WHEN 每轮对话结束后保存会话状态，THE Memory_Manager SHALL 将本轮新提取的实体与 `conversation_session.extracted_info` 中已有实体合并：新值覆盖同名旧值，未被本轮覆盖的字段保留不变。
4. WHEN handleRecommend 被调用，THE Memory_Manager SHALL 读取当前 Session 的 `extracted_info` 并将其中的实体（如 category、brand、price_min、price_max）合并到本轮实体中，使推荐引擎能感知跨轮次累积的用户偏好。
5. WHEN 对话开始（chat 方法入口），THE Memory_Manager SHALL 读取 `user_profile` 表中该用户的历史偏好，并将其作为背景上下文注入 handleRecommend 的 LLM prompt。
6. IF `user_profile` 读取失败或记录不存在，THEN THE Memory_Manager SHALL 降级为不使用长期记忆继续处理，不抛出异常，不影响对话主流程。
7. IF `extracted_info` 解析失败，THEN THE Memory_Manager SHALL 将会话记忆视为空 Map 继续处理，不抛出异常。

---

### 需求 10：记忆容量管理

**用户故事：** 作为系统，我希望三层记忆的容量和合并策略有明确规范，以便控制 LLM prompt 长度、避免存储膨胀，并在记忆读取失败时保证对话主流程不受影响。

#### 验收标准

1. THE Memory_Manager SHALL 将 `dialogue_history` 中保留的最大轮次数限制为 10 轮（即最多 20 条消息），超出时保留最新的 10 轮，丢弃最早的轮次。
2. WHEN 短期记忆注入 LLM prompt 时，THE Memory_Manager SHALL 仅取最近 N 轮历史（N 由各意图处理方法按需指定，最大不超过 10），避免 prompt 过长。
3. WHEN 会话记忆（extracted_info）执行合并时，THE Memory_Manager SHALL 采用以下策略：新值覆盖同名旧值；本轮未提取的字段保留旧值；合并后的 Map 键数量不超过 20 个，超出时按字段最后更新时间丢弃最旧的字段。
4. WHEN 长期记忆（user_profile）读取成功，THE Memory_Manager SHALL 仅将 `preferred_categories`、`preferred_brands`、`price_range` 三个字段注入 prompt，不注入完整 user_profile 对象，以控制 prompt 长度。
5. IF 长期记忆读取超时（超过 200ms），THEN THE Memory_Manager SHALL 降级跳过长期记忆注入，记录 warn 日志，不影响对话主流程。
6. IF 短期记忆或会话记忆的序列化/反序列化失败，THEN THE Memory_Manager SHALL 记录 warn 日志并以空值降级，不向上层抛出异常。
