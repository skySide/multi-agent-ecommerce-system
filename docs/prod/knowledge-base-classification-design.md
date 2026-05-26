# 知识库分类检索设计方案

## 1. 问题诊断

### 1.1 三类数据混在同一个 SimpleVectorStore

项目只有一个 `SimpleVectorStore` Bean，三类数据全往里写，没有任何隔离字段：

| 写入方 | 数据 | metadata |
|--------|------|----------|
| `VectorStoreServiceImpl.addProductDocuments` | 商品 | `productId`, `productName`, `categoryId`, `brand`, `price`... |
| `VectorStoreServiceImpl.addUserDocuments` | 用户画像 | `userId`... |
| `DocumentVectorServiceImpl.addTextToKnowledgeBase` | 知识库 | `source`, `chunkIndex`, `totalChunks`, `docType` |

`VectorStoreService` 接口虽然定义了 `PRODUCT_COLLECTION` / `USER_COLLECTION` 常量，但实际代码从未使用。所有 `similaritySearch` 调用均无过滤条件，三类数据在向量空间中互相污染。

### 1.2 知识检索不可控

用户提问"退货的相关政策是什么？"，`KnowledgeIntentAgent` 调用 `searchKnowledgeBase(query, 3)`：

- 全库无差别相似度搜索，返回 topK=3
- "配送说明"里也谈运费、"会员权益"里含"免费退换货"，向量距离并不远
- 商品数据中若有"售后""退换"等词，也会挤占 topK 位置
- 结果可能混入无关文档

### 1.3 跨类别问题无法处理

"退货后消费券还能退回吗？" 涉及两个知识大类，当前只能做单次无差别检索，无法按类别分别召回。

### 1.4 `docType` 字段已被占用

当前 `docType` 表示文档格式来源（`"text"`、`"faq"` 等），不能复用作知识分类，需要新增独立字段。

## 2. 解决方案

**核心思路**: 所有文档写入时打上 `data_type` 标签隔离三类数据，知识库文档额外打上 `knowledge_type`（大类）+ `sub_type`（子类）。检索前由 LLM 分类用户查询属于哪个知识类别，检索时通过 `filterExpression` 限定范围。

### 2.1 字段职责

| 字段 | 层级 | 取值 | 说明 |
|------|------|------|------|
| `data_type` | 顶层隔离 | `product` / `user` / `knowledge` | 区分三类数据，互不污染 |
| `knowledge_type` | 知识大类 | `after_sales` / `coupon` / `logistics` / `member` / `payment` / `product_guide` / `account` | 定位到具体知识领域 |
| `sub_type` | 知识子类 | `return_policy` / `coupon_refund` / `delivery_info`... | 精准到知识点 |
| `docType` | 保持原样 | `text` / `faq`... | 记录文档格式来源，不改动 |

### 2.2 检索流程

```
用户: "退货的相关政策是什么？"
  ↓ Step 1: Query改写（已有）
  ↓ Step 2: LLM知识分类 → knowledge_type=after_sales, sub_type=return_policy
  ↓ Step 3: 向量检索 + 过滤
      filterExpression = "data_type == 'knowledge' && knowledge_type == 'after_sales'"
  ↓ 精准召回退货相关文档
```

```
用户: "退货后消费券还能退回吗？"（跨类别）
  ↓ LLM知识分类 → [{knowledge_type: "after_sales"}, {knowledge_type: "coupon"}]
  ↓ 分别检索：
      filter = "data_type == 'knowledge' && knowledge_type == 'after_sales'" → topK=2
      filter = "data_type == 'knowledge' && knowledge_type == 'coupon'"      → topK=2
  ↓ 合并4条结果，按相似度重排
```

### 2.3 LLM 分类失败降级

分类步骤抛异常或返回空 → 退化为 `filterExpression = "data_type == 'knowledge'"` 的不过类别检索。

## 3. 知识分类体系

### 3.1 大类 (knowledge_type)

| 编码 | 名称 |
|------|------|
| `after_sales` | 售后服务 |
| `logistics` | 物流配送 |
| `member` | 会员权益 |
| `coupon` | 优惠券/消费券 |
| `payment` | 支付与退款 |
| `product_guide` | 商品选购指南 |
| `account` | 账户相关 |

### 3.2 子类 (sub_type)

```
after_sales   → return_policy, exchange_policy, refund_process, quality_issue
logistics     → delivery_info, shipping_fee
member        → member_benefits, points_rule
coupon        → coupon_use, coupon_refund, coupon_expire
payment       → pay_method, refund_flow
product_guide → phone_buying_guide
account       → (预留给后续扩展)
```

### 3.3 跨类别关联参考（内置于分类 Prompt）

| 场景 | 涉及大类 |
|------|----------|
| 退货后优惠券是否退回 | `after_sales` + `coupon` |
| 退货后运费谁承担 | `after_sales` + `logistics` |
| 会员退货是否影响等级 | `after_sales` + `member` |
| 退款后优惠券如何处理 | `payment` + `coupon` |

## 4. 知识文档文件化

### 4.1 文件结构

当前知识内容硬编码在 `SystemBootstrap` 中，仅有 4 篇简短文档。改造为从文件加载：

```
resources/knowledge/
├── after_sales/
│   ├── return_policy.txt         # 退货政策（~800字）
│   ├── exchange_policy.txt       # 换货政策（~600字）
│   ├── refund_process.txt        # 退款流程（~700字）
│   └── quality_issue.txt         # 质量问题处理（~600字）
├── logistics/
│   ├── delivery_info.txt         # 配送说明（~700字）
│   └── shipping_fee.txt          # 运费标准（~500字）
├── member/
│   ├── member_benefits.txt       # 会员权益（~800字）
│   └── points_rule.txt           # 积分规则（~600字）
├── coupon/
│   ├── coupon_use.txt            # 优惠券使用规则（~700字）
│   ├── coupon_refund.txt         # 退货后优惠券退回（~500字）
│   └── coupon_expire.txt         # 优惠券过期处理（~500字）
├── product_guide/
│   └── phone_buying_guide.txt    # 手机选购指南（~1000字）
└── payment/
    ├── pay_method.txt            # 支付方式（~500字）
    └── refund_flow.txt           # 退款到账说明（~600字）
```

### 4.2 分块验证

`CHUNK_SIZE=500`、`CHUNK_OVERLAP=50`，每篇文档超 500 字将被自动拆分为多个 chunk：

- `phone_buying_guide.txt`（~1000 字）→ 约 2-3 个 chunk
- `member_benefits.txt`（~800 字）→ 约 2 个 chunk
- `return_policy.txt`（~800 字）→ 约 2 个 chunk
- 其余 ~500-700 字 → 可能 1-2 个 chunk

每个 chunk 都会带上相同的 `knowledge_type` 和 `sub_type`，检索时能全部命中。

### 4.3 SystemBootstrap 改造

从硬编码字符串 → 遍历 `resources/knowledge/` 目录，读取文件内容，根据目录名推断 `knowledge_type`，根据文件名推断 `sub_type`，调用 `addTextToKnowledgeBase`。

## 5. 代码变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `enums/KnowledgeTypeEnum.java` | 知识大类 + 子类枚举定义 |
| `service/KnowledgeClassifyService.java` | LLM 知识分类接口 |
| `service/impl/KnowledgeClassifyServiceImpl.java` | LLM 知识分类实现 |
| `resources/knowledge/` 下 14 个 .txt 文件 | 知识文档内容 |

### 修改文件

| 文件 | 改动 | 改动量 |
|------|------|--------|
| `DocumentVectorService.java` | 接口新增 `addTextToKnowledgeBase(content, source, knowledgeType, subType)` 和带分类过滤的 `searchKnowledgeBase` | ~15行 |
| `DocumentVectorServiceImpl.java` | 写入加 `data_type`/`knowledge_type`/`sub_type`；检索加 `filterExpression` | ~50行 |
| `KnowledgeIntentAgent.java` | 检索前先调分类服务，再调过滤检索 | ~35行 |
| `SystemBootstrap.java` | 扫描 knowledge 目录加载文件，传入分类标签 | ~40行 |
| `VectorStoreServiceImpl.java` | 商品写入加 `data_type="product"`；商品检索加 `data_type=="product"` 过滤 | ~15行 |
| `VectorSyncServiceImpl.java` | 调用 `convertProductToDocument` 时传入 `data_type` 方法更新 | ~5行 |

### 不改的文件

- `KnowledgeController.java` —— API 层不改，可通过 `docType` 参数后续迭代
- `application.yml` / `VectorStoreConfiguration.java` —— 仍用 SimpleVectorStore
- 前端 —— 无变化

## 6. 实施步骤

1. 新增 `KnowledgeTypeEnum` 枚举
2. 创建 `resources/knowledge/` 下的知识文档文件
3. 修改 `VectorStoreServiceImpl` / `VectorSyncServiceImpl` —— 商品数据打 `data_type` 标签，检索加类型过滤
4. 修改 `DocumentVectorService` 接口 + 实现 —— 知识文档打三个标签 + 过滤检索
5. 新增 `KnowledgeClassifyService` + 实现 —— LLM 知识分类
6. 修改 `KnowledgeIntentAgent` —— 串联分类 + 过滤检索
7. 修改 `SystemBootstrap` —— 从文件加载知识库
