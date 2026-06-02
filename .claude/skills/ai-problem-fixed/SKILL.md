---
name: ai-problem-fixed
description: Use when Spring AI ChatClient .entity() throws IllegalArgumentException("The template string is not valid") with message containing "'[' came as a complete surprise to me", or when LLM structured output parsing fails for complex nested types (Map<String, Object>, generic collections, polymorphic fields). Also use when adding new LLM structured output calls that involve nested or dynamic types.
---

# AI 结构化输出解析失败修复方案

## 概述

Spring AI `ChatClient.entity()` 方法在解析包含复杂嵌套类型（如 `Map<String, Object>`、泛型集合、多态字段）的 LLM 响应时，内部模板引擎无法处理 LLM 返回的 JSON 字符串，抛出异常。

**核心原则：涉及嵌套动态类型的 LLM 结构化输出，使用 `BeanOutputConverter<T>` + `content()` 替代 `.entity()`。**

## 问题症状

- 异常类型：`IllegalArgumentException`
- 异常消息：`"The template string is not valid"`
- 异常原因消息中包含：`'[' came as a complete surprise to me`
- 调用链中**看不到 `BeanOutputConverter`**（这是 `entity()` 内部模板解析失败，而非 Converter 阶段失败）
- 仅在实体包含 `Map<String, Object>`、`Object` 字段、或泛型嵌套时发生
- 简单的 POJO（所有字段都是具体类型）使用 `.entity()` 可能正常工作

### 完整异常示例

```
java.lang.IllegalArgumentException: The template string is not valid.
    ...
Caused by: ... '[' came as a complete surprise to me
```

**根因**：LLM 返回的 JSON 响应被 `.entity()` 内部模板引擎解析时，遇到 `[` 字符（可能是数组开头、嵌套 JSON 等）时无法正确匹配模板占位符，抛出 `IllegalArgumentException`。

## 修复方案

### Before（有问题）

```java
// ❌ .entity() 对含 Map<String, Object> 的嵌套类型会抛出 IllegalArgumentException
// 异常消息: "The template string is not valid" / "'[' came as a complete surprise to me"
MultiIntentRecognitionResult result = chatClient.prompt().user(prompt).call()
        .entity(MultiIntentRecognitionResult.class);
```

### After（正确）

```java
// ✅ 使用 BeanOutputConverter + content() + 日志记录
BeanOutputConverter<MultiIntentRecognitionResult> converter =
        new BeanOutputConverter<>(MultiIntentRecognitionResult.class);
String response = chatClient.prompt().user(prompt).call().content();
log.info("ClassName.methodName - LLM原始响应: {}", response);
MultiIntentRecognitionResult result = converter.convert(response);
```

### 为什么 BeanOutputConverter 能解决？

`BeanOutputConverter` 不依赖内部模板引擎，它直接对 LLM 原始字符串输出做 JSON 反序列化，且：
1. 自动处理 markdown 代码块（`\`\`\`json ... \`\`\``）的清理
2. 支持 `@JsonProperty` 注解的 snake_case ↔ camelCase 映射
3. 支持嵌套的 `Map<String, Object>`、泛型集合等复杂类型

## 编码规范

### 1. 必须使用显式类型，禁止 var

```java
// ❌ 错误
var converter = new BeanOutputConverter<>(SomeDTO.class);

// ✅ 正确
BeanOutputConverter<SomeDTO> converter = new BeanOutputConverter<>(SomeDTO.class);
```

### 2. 必须打印 LLM 原始响应日志

在 `convert()` 之前打印 LLM 原始响应，便于排查问题：

```java
log.info("ClassName.methodName - LLM原始响应: {}", response);
```

### 3. 必须 try-catch 并降级处理

```java
try {
    BeanOutputConverter<SomeDTO> converter = new BeanOutputConverter<>(SomeDTO.class);
    String response = chatClient.prompt().user(prompt).call().content();
    log.info("ClassName.methodName - LLM原始响应: {}", response);
    SomeDTO result = converter.convert(response);
    return result;
} catch (Exception e) {
    log.error("ClassName.methodName - LLM结构化输出解析失败", e);
    // 降级：返回默认值
    return SomeDTO.builder().build();
}
```

## 何时使用 entity() vs BeanOutputConverter

| 场景 | 方法 | 说明 |
|------|------|------|
| 简单 POJO（所有字段为 String/Integer/Boolean 等具体类型） | `entity(Class)` | 可用，简单高效 |
| DTO 含 `Map<String, Object>` | `BeanOutputConverter` | **必须**，entity() 内部模板解析会抛 IllegalArgumentException |
| DTO 含 `List<Map<String, Object>>` | `BeanOutputConverter` | **必须** |
| DTO 含 `Object` 或多态字段 | `BeanOutputConverter` | **必须** |
| DTO 字段带 `@JsonProperty` snake_case 映射 | `BeanOutputConverter` | **推荐**，自动注入 JSON Schema 约束 LLM 输出格式 |
| 需要日志记录 LLM 原始输出 | `BeanOutputConverter` | **推荐**，方便排查问题 |
| LLM 输出纯文本（闲聊回复、摘要） | `content()` | 无需结构化 |

## 快速决策流程

```
LLM 响应需要映射为 POJO？
  ├── 是 → POJO 是否包含 Map<String, Object> / Object / 泛型嵌套？
  │     ├── 是 → 使用 BeanOutputConverter + content()
  │     └── 否 → 可使用 entity()，但推荐统一使用 BeanOutputConverter
  └── 否 → 使用 content()
```

## 实际案例

### 案例：MultiIntentRecognitionResult 解析失败

**场景**：多意图识别，LLM 返回包含 `Map<String, Object> entities` 的嵌套 JSON。
`MultiIntentRecognitionResult` 内部包含 `List<IntentItem>`，而 `IntentItem` 的 `entities` 字段类型为 `Map<String, Object>`。

**异常**：
- 类型：`IllegalArgumentException`
- 消息：`"The template string is not valid"`
- 原因：`'[' came as a complete surprise to me`
- 堆栈中无 `BeanOutputConverter` 字样（说明是 `entity()` 内部模板阶段就失败了，而非 JSON 反序列化阶段）

**修复**：`ConversationAgent.recognizeIntents()` 从 `.entity()` 改为 `BeanOutputConverter` 模式，绕过 entity() 内部模板引擎。

**关联文件**：
- `ConversationAgent.java:204-210` — 多意图识别结构化输出（已修复）
- `KnowledgeClassifyServiceImpl.java:88-91` — 知识分类结构化输出（已有正确实现）

## 相关技能

- [[java-code-standards]] — 日志规范、注释规范、常量使用规范

## 常见错误

| 错误 | 正确做法 |
|------|---------|
| `.entity()` 抛 `IllegalArgumentException("The template string is not valid")` | 改用 `BeanOutputConverter` + `content()` 模式 |
| 遇到解析失败后手动 `ObjectMapper.readValue()` | 使用 `BeanOutputConverter`，它自动处理 markdown 代码块清理 |
| 用 `var` 声明 converter | 使用显式类型 `BeanOutputConverter<T>` |
| 不打印 LLM 原始响应日志 | convert 前必须 `log.info("...LLM原始响应: {}", response)` |
| 用 `.entity()` 处理含 `Map<String, Object>` 的 DTO | 统一使用 `BeanOutputConverter` 模式 |
| 解析失败不降级 | try-catch 并返回合理的默认值 |
