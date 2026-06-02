---
name: ai-problem-fixed
description: Use when Spring AI encounters (1) ChatClient .entity() throws IllegalArgumentException("The template string is not valid") with "'[' came as a complete surprise to me" for complex nested types, or (2) Embedding API returns 400 with error code 20015 "the parameter is invalid" when using non-OpenAI embedding models. Also use when adding new LLM structured output or embedding configuration.
---

# AI 集成常见问题修复方案

## 概述

Spring AI 框架在与非 OpenAI 原生 API（如硅基流动 SiliconFlow）集成时，存在两类典型问题：
1. **结构化输出**：`ChatClient.entity()` 对复杂嵌套类型解析失败
2. **Embedding API**：发送了模型不支持的参数导致 400 错误

**核心原则：使用 Spring AI 的 OpenAI 兼容接口时，需注意参数兼容性——不是所有模型都支持 OpenAI 的全部参数。**

---

## 案例一：entity() 解析复杂嵌套类型失败

### 问题症状

- 异常类型：`IllegalArgumentException`
- 异常消息：`"The template string is not valid"`
- 异常原因消息中包含：`'[' came as a complete surprise to me`
- 调用链中**看不到 `BeanOutputConverter`**（这是 `entity()` 内部模板解析失败）
- 仅在实体包含 `Map<String, Object>`、`Object` 字段、或泛型嵌套时发生

### 完整异常示例

```
java.lang.IllegalArgumentException: The template string is not valid.
    ...
Caused by: ... '[' came as a complete surprise to me
```

**根因**：LLM 返回的 JSON 响应被 `.entity()` 内部模板引擎解析时，遇到 `[` 字符时无法正确匹配模板占位符。

### 修复方案

```java
// ❌ .entity() 对含 Map<String, Object> 的嵌套类型会抛 IllegalArgumentException
MultiIntentRecognitionResult result = chatClient.prompt().user(prompt).call()
        .entity(MultiIntentRecognitionResult.class);

// ✅ 使用 BeanOutputConverter + content() + 日志记录
BeanOutputConverter<MultiIntentRecognitionResult> converter =
        new BeanOutputConverter<>(MultiIntentRecognitionResult.class);
String response = chatClient.prompt().user(prompt).call().content();
log.info("ClassName.methodName - LLM原始响应: {}", response);
MultiIntentRecognitionResult result = converter.convert(response);
```

### 编码规范

1. **禁止 `var`**：必须使用显式类型 `BeanOutputConverter<T>`
2. **必须打印 LLM 原始响应日志**：`log.info("ClassName.methodName - LLM原始响应: {}", response)`
3. **必须 try-catch 并降级处理**

### entity() vs BeanOutputConverter 选择标准

| 场景 | 方法 | 说明 |
|------|------|------|
| 简单 POJO（字段全为 String/Integer 等具体类型） | `entity(Class)` | 可用 |
| DTO 含 `Map<String, Object>` | `BeanOutputConverter` | **必须** |
| DTO 含 `List<Map<String, Object>>` | `BeanOutputConverter` | **必须** |
| DTO 含 `Object` 或多态字段 | `BeanOutputConverter` | **必须** |

### 关联文件

- `ConversationAgent.java:204-210` — 多意图识别结构化输出（已修复）
- `KnowledgeClassifyServiceImpl.java:88-91` — 知识分类结构化输出（已有正确实现）

---

## 案例二：Embedding API 发送不支持的 `dimensions` 参数

### 问题症状

- 异常类型：`ai.retry.NonTransientAiException`
- HTTP 状态码：`400`
- 响应体：`{code: 20015, message: "the parameter is invalid, please check again"}`
- 发生在：`vectorStore.add(documents)` 调用 embedding API 时
- 场景：使用非 OpenAI 原生 embedding 模型（如硅基流动的 BAAI/bge-large-zh-v1.5）

### 完整异常示例

```
ai.retry.NonTransientAiException: 400, {code: 20015, message: the parameter is invalid, please check again}
    at org.springframework.ai.retry.NonTransientAiException...
```

**根因**：`application.yml` 中配置了 `dimensions` 参数，但非 OpenAI 原生模型（如 BGE 系列）不支持此参数。

OpenAI 的 `dimensions` 参数是 `text-embedding-3-small` 和 `text-embedding-3-large` 模型特有的功能，允许用户自定义输出向量维度。BGE、M3E 等其他厂商的模型不支持此参数，API 收到后会返回 400。

### 修复方案

```yaml
# ❌ 错误：BGE 模型不支持 dimensions 参数
spring:
  ai:
    openai:
      embedding:
        options:
          model: BAAI/bge-large-zh-v1.5
          dimensions: 768       # ← 硅基流动 API 返回 20015 错误

# ✅ 正确：删除 dimensions 配置
spring:
  ai:
    openai:
      embedding:
        options:
          model: BAAI/bge-large-zh-v1.5
          # 不配置 dimensions，让模型输出原生维度
```

### 各模型是否支持 dimensions 参数

| 模型 | 支持 dimensions | 原生维度 | 说明 |
|------|:---:|:---:|------|
| OpenAI text-embedding-3-small | ✅ | 1536 | 可指定 512/1536 |
| OpenAI text-embedding-3-large | ✅ | 3072 | 可指定 256/1024/3072 |
| OpenAI text-embedding-ada-002 | ❌ | 1536 | 不支持自定义维度 |
| BAAI/bge-large-zh-v1.5 | ❌ | 1024 | 硅基流动等中转 API 不支持 |
| BAAI/bge-base-zh-v1.5 | ❌ | 768 | 同上 |
| BAAI/bge-small-zh-v1.5 | ❌ | 512 | 同上 |
| M3E 系列 | ❌ | 768 | 同上 |

### 关联文件

- `application.yml:18-21` — embedding 配置（已修复：移除 dimensions）

---

## 相关技能

- [[java-code-standards]] — 日志规范、注释规范、常量使用规范

## 常见错误汇总

| 错误现象 | 根因 | 修复 |
|---------|------|------|
| `IllegalArgumentException: The template string is not valid` | `.entity()` 不支持复杂嵌套类型 | 改用 `BeanOutputConverter` + `content()` 模式 |
| `NonTransientAiException: 400, code: 20015, "parameter is invalid"` | 模型不支持 `dimensions` 参数 | 删除 `application.yml` 中的 `dimensions` 配置 |
| 遇到解析失败后手动 `ObjectMapper.readValue()` | 重复造轮子 | 使用 `BeanOutputConverter`，它自动处理 markdown 代码块清理 |
| 用 `var` 声明 converter | 代码规范 | 使用显式类型 `BeanOutputConverter<T>` |
| 不打印 LLM 原始响应日志 | 排查困难 | convert 前必须 `log.info("...LLM原始响应: {}", response)` |
| 解析失败不降级 | 服务崩溃 | try-catch 并返回合理的默认值 |
