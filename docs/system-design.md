# 多 Agent 电商推荐系统设计文档

## 1. 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端层 (React + Ant Design)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   首页推荐    │  │   商品搜索    │  │   商品详情    │  │   个人中心    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API 网关层 (Java)                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  推荐接口    │  │  搜索接口    │  │  行为采集    │  │  用户接口    │     │
│  │ /recommend   │  │ /search      │  │ /behavior    │  │ /user        │     │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Supervisor 编排器 (Java)                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     多 Agent 并行调度                                │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │   │
│  │  │用户画像  │  │商品召回  │  │库存检查  │  │营销文案  │            │   │
│  │  │Agent     │  │Agent     │  │Agent     │  │Agent     │            │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
┌───────────────────────┐  ┌──────────────────┐  ┌──────────────────────┐
│    MySQL 8.0          │  │   Milvus v2.4    │  │     Redis 7          │
│  ┌─────────────────┐  │  │  ┌────────────┐  │  │  ┌────────────────┐  │
│  │   用户表         │  │  │  │商品向量    │  │  │  │ 实时特征缓存    │  │
│  │   商品表         │  │  │  │用户向量    │  │  │  │ 热门商品缓存    │  │
│  │   行为日志表      │  │  │  └────────────┘  │  │  │ 搜索结果缓存    │  │
│  │   用户画像表      │  │  └──────────────────┘  │  └────────────────┘  │
│  │   类目表         │  │                        │                      │
│  │   标签表         │  │                        │                      │
│  │   商品标签关系表   │  │                        │                      │
│  │   推荐缓存表       │  │                        │                      │
│  └─────────────────┘  │                        │                      │
└───────────────────────┘                        └──────────────────────┘
```

## 2. 数据库设计

### 2.1 MySQL 表结构

#### 用户表 (user)
```sql
CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    username VARCHAR(50) NOT NULL DEFAULT '' COMMENT '用户名',
    email VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT '' COMMENT '手机号',
    avatar_url VARCHAR(255) DEFAULT '' COMMENT '头像URL',
    register_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_id (user_id),
    INDEX idx_register_time (register_time)
) ENGINE=InnoDB COMMENT='用户基础信息表';
```

#### 商品表 (product)
```sql
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    product_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商品ID',
    product_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '商品名称',
    category_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '类目ID',
    category_name VARCHAR(50) DEFAULT '' COMMENT '类目名称',
    brand VARCHAR(50) DEFAULT '' COMMENT '品牌',
    price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '价格',
    original_price DECIMAL(10,2) DEFAULT 0.00 COMMENT '原价',
    product_description TEXT COMMENT '商品描述',
    main_image VARCHAR(255) DEFAULT '' COMMENT '主图URL',
    images TEXT COMMENT '图片列表JSON字符串',
    stock INT DEFAULT 0 COMMENT '库存数量',
    sales_count INT DEFAULT 0 COMMENT '销量',
    rating DECIMAL(2,1) DEFAULT 5.0 COMMENT '评分 0-5',
    product_status TINYINT DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_id (product_id),
    INDEX idx_category (category_id),
    INDEX idx_price (price),
    INDEX idx_sales (sales_count DESC),
    FULLTEXT INDEX ft_name (product_name) WITH PARSER ngram
) ENGINE=InnoDB COMMENT='商品信息表';
```

#### 类目表 (category)
```sql
CREATE TABLE category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '类目ID',
    category_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '类目名称',
    parent_id VARCHAR(32) DEFAULT '0' COMMENT '父类目ID',
    category_level TINYINT DEFAULT 1 COMMENT '层级: 1-一级, 2-二级, 3-三级',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_category_id (category_id),
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB COMMENT='商品类目表';
```

#### 用户行为日志表 (user_behavior)
```sql
CREATE TABLE user_behavior (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    product_id VARCHAR(32) DEFAULT '' COMMENT '商品ID',
    behavior_type VARCHAR(20) NOT NULL DEFAULT '' COMMENT '行为类型: view/click/cart/favorite/search',
    search_keyword VARCHAR(100) DEFAULT '' COMMENT '搜索关键词(仅search类型)',
    referrer VARCHAR(100) DEFAULT '' COMMENT '来源页面',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_product (product_id),
    INDEX idx_behavior_time (behavior_type, create_time)
) ENGINE=InnoDB COMMENT='用户行为日志表';
```

#### 用户画像表 (user_profile)
```sql
CREATE TABLE user_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    segments TEXT COMMENT '用户分群JSON字符串',
    preferred_categories TEXT COMMENT '偏好类目JSON字符串',
    preferred_brands TEXT COMMENT '偏好品牌JSON字符串',
    price_range_min DECIMAL(10,2) DEFAULT 0.00 COMMENT '价格偏好下限',
    price_range_max DECIMAL(10,2) DEFAULT 999999.00 COMMENT '价格偏好上限',
    rfm_recency DECIMAL(3,2) DEFAULT 0.00 COMMENT 'R-最近购买时间得分',
    rfm_frequency DECIMAL(3,2) DEFAULT 0.00 COMMENT 'F-购买频率得分',
    rfm_monetary DECIMAL(3,2) DEFAULT 0.00 COMMENT 'M-消费金额得分',
    real_time_tags TEXT COMMENT '实时标签JSON字符串',
    vector_id VARCHAR(64) DEFAULT '' COMMENT 'Milvus中对应的向量ID',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_id (user_id),
    INDEX idx_vector (vector_id)
) ENGINE=InnoDB COMMENT='用户画像表';
```

#### 用户实时特征表 (user_realtime_features)
```sql
CREATE TABLE user_realtime_features (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    view_count_1h INT DEFAULT 0 COMMENT '1小时浏览数',
    view_count_24h INT DEFAULT 0 COMMENT '24小时浏览数',
    click_count_24h INT DEFAULT 0 COMMENT '24小时点击数',
    cart_count_24h INT DEFAULT 0 COMMENT '24小时加购数',
    last_view_product_id VARCHAR(32) DEFAULT '' COMMENT '最后浏览商品',
    last_view_time DATETIME DEFAULT NULL COMMENT '最后浏览时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB COMMENT='用户实时特征表';
```

#### 推荐结果缓存表 (recommend_cache)
```sql
CREATE TABLE recommend_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    cache_key VARCHAR(128) NOT NULL DEFAULT '' COMMENT '缓存键: user_id:scene',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    scene VARCHAR(20) NOT NULL DEFAULT '' COMMENT '场景: home/search/cart',
    products TEXT NOT NULL COMMENT '推荐商品列表JSON字符串',
    expire_time DATETIME NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '过期时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_cache_key (cache_key),
    INDEX idx_user_scene (user_id, scene),
    INDEX idx_expire (expire_time)
) ENGINE=InnoDB COMMENT='推荐结果缓存表';
```

#### 标签表 (tag)
```sql
CREATE TABLE tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tag_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '标签ID',
    tag_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '标签名称',
    tag_type TINYINT DEFAULT 1 COMMENT '标签类型: 1-系统标签, 2-运营标签',
    tag_status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_tag_id (tag_id),
    INDEX idx_tag_type (tag_type)
) ENGINE=InnoDB COMMENT='标签表';
```

#### 商品标签关系表 (product_tag)
```sql
CREATE TABLE product_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    product_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商品ID',
    tag_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '标签ID',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_tag (product_id, tag_id),
    INDEX idx_product (product_id),
    INDEX idx_tag (tag_id)
) ENGINE=InnoDB COMMENT='商品标签关系表';
```

### 2.2 ER 图

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│      user       │       │   user_profile  │       │ user_realtime_  │
│                 │◄──────┤                 │       │    features     │
│  id (PK)        │  1:1  │  id (PK)        │       │                 │
│  user_id (UK)   │       │  user_id (UK)   │◄──────┤  id (PK)        │
│  username       │       │  segments       │  1:1  │  user_id (UK)   │
│  email          │       │  preferred_     │       │  view_count_1h  │
│  phone          │       │    categories   │       │  view_count_24h │
│  avatar_url     │       │  price_range_   │       │  ...            │
│  register_time  │       │    min/max      │       └─────────────────┘
│  status         │       │  rfm_recency    │
│  is_deleted     │       │  rfm_frequency  │
│  create_time    │       │  rfm_monetary   │
│  update_time    │       │  vector_id      │
└─────────────────┘       │  is_deleted     │
                          │  create_time    │
                          │  update_time    │
                          └─────────────────┘

┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│    category     │       │     product     │       │   product_tag   │
│                 │◄──────┤                 │◄──────┤                 │
│  id (PK)        │  1:n  │  id (PK)        │  1:n  │  id (PK)        │
│  category_id    │       │  product_id(UK) │       │  product_id     │◄─┐
│  category_name  │       │  product_name   │       │  tag_id         │  │
│  parent_id      │       │  category_id    │       │  is_deleted     │  │
│  category_level │       │  category_name  │       │  create_time    │  │
│  is_deleted     │       │  brand          │       │  update_time    │  │
│  create_time    │       │  price          │       └─────────────────┘  │
│  update_time    │       │  original_price │                            │
└─────────────────┘       │  description    │       ┌─────────────────┐  │
                          │  main_image     │       │       tag       │  │
                          │  images         │       │                 │◄─┘
                          │  seller_id      │       │  id (PK)        │
                          │  stock          │       │  tag_id (UK)    │
                          │  sales_count    │       │  tag_name       │
                          │  rating         │       │  tag_type       │
                          │  product_status │       │  tag_status     │
                          │  is_deleted     │       │  is_deleted     │
                          │  create_time    │       │  create_time    │
                          │  update_time    │       │  update_time    │
                          └─────────────────┘       └─────────────────┘

┌─────────────────┐
│  user_behavior  │
│                 │
│  id (PK)        │
│  user_id        │◄──────────┐
│  product_id     │◄──────┐   │
│  behavior_type  │       │   │
│  search_keyword │       │   │
│  referrer       │       │   │
│  is_deleted     │       │   │
│  create_time    │       │   │
│  update_time    │       │   │
└─────────────────┘       │   │
                          │   │
┌─────────────────┐       │   │
│ recommend_cache │       │   │
│                 │       │   │
│  id (PK)        │       │   │
│  cache_key      │       │   │
│  user_id        │◄──────┘   │
│  scene          │           │
│  products       │           │
│  expire_time    │           │
│  is_deleted     │           │
│  create_time    │           │
│  update_time    │           │
└─────────────────┘           │
                              │
                              ▼
                    ┌─────────────────┐
                    │  Milvus Vector  │
                    │    Database     │
                    │                 │
                    │  product_vectors│
                    │  - product_id   │
                    │  - embedding    │
                    │  - category_id  │
                    │                 │
                    │  user_vectors   │
                    │  - user_id      │
                    │  - embedding    │
                    │  - update_time  │
                    └─────────────────┘
```

### 2.3 数据同步到向量数据库

#### 商品向量同步流程

```
商品数据变更 (新增/修改)
    │
    ▼
┌─────────────────────────────────────────┐
│ 1. 监听 MySQL Binlog 或 应用层触发       │
│    - 商品名称、描述、类目、标签变化       │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 2. 生成 Embedding (Python 服务)          │
│    - 拼接商品文本: name + category +     │
│      brand + description + tags          │
│    - 调用 Embedding 模型 (BERT/文心等)   │
│    - 生成 768 维向量                     │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 3. 写入 Milvus                           │
│    - Collection: product_vectors         │
│    - 包含字段: product_id, embedding,    │
│      category_id, price, sales_count     │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 4. 同步状态记录                          │
│    - 记录同步时间、版本号                 │
│    - 失败重试机制                         │
└─────────────────────────────────────────┘
```

#### 用户向量同步流程

```
用户画像更新 (定时/触发)
    │
    ▼
┌─────────────────────────────────────────┐
│ 1. 触发条件                              │
│    - 累计行为数达到阈值 (如 10 次)       │
│    - 定时任务 (每小时)                   │
│    - 手动触发                            │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 2. 获取用户数据 (UserProfileAgent)       │
│    - getUserProfile(userId)              │
│    - getUserRecentBehaviors(userId, 50)  │
│    - getUserRealtimeFeatures(userId)     │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 3. 生成用户 Embedding                    │
│    - 拼接用户画像文本:                   │
│      偏好类目 + 偏好品牌 + 价格区间 +    │
│      最近浏览商品 + 实时标签             │
│    - 调用 Embedding 模型                 │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 4. 写入 Milvus                           │
│    - Collection: user_vectors            │
│    - 包含字段: user_id, embedding,       │
│      update_time                         │
│    - 同时更新 user_profile.vector_id     │
└─────────────────────────────────────────┘
```

#### 同步服务设计

```python
# embedding-service/app/services/sync_service.py

@Component
class VectorSyncService:
    
    @Scheduled(fixedRate = 3600000)  # 每小时执行
    def syncProductVectors():
        # 1. 查询最近变更的商品
        changedProducts = productRepository.findRecentChanges(1 hour)
        
        # 2. 批量生成 Embedding
        for batch in changedProducts.chunk(100):
            embeddings = embeddingModel.encodeBatch(
                [p.toEmbeddingText() for p in batch]
            )
            
            # 3. 批量写入 Milvus
            milvusClient.upsert("product_vectors", 
                ids=[p.productId for p in batch],
                vectors=embeddings,
                metadata=[p.toMetadata() for p in batch]
            )
    
    @EventListener(UserBehaviorEvent)
    def onUserBehavior(event):
        # 累计行为数检查
        behaviorCount = behaviorRepository.countSince(
            event.userId, 
            event.lastProfileUpdate
        )
        
        if behaviorCount >= 10:
            syncUserVector(event.userId)
    
    def syncUserVector(userId):
        # 1. 获取用户数据
        profile = userProfileRepository.findByUserId(userId)
        behaviors = behaviorRepository.findRecent(userId, 50)
        
        # 2. 生成文本
        text = buildUserEmbeddingText(profile, behaviors)
        
        # 3. 生成向量
        vector = embeddingModel.encode(text)
        
        # 4. 写入 Milvus
        vectorId = milvusClient.upsert("user_vectors", 
            ids=[userId],
            vectors=[vector]
        )
        
        # 5. 更新 MySQL
        userProfileRepository.updateVectorId(userId, vectorId)
```

### 2.4 Milvus Collection 设计

#### 商品向量 Collection (product_vectors)
```yaml
Collection: product_vectors
Fields:
  - name: product_id
    type: VARCHAR(32)
    is_primary: true
  - name: embedding
    type: FLOAT_VECTOR(768)  # 使用bert-base-chinese或类似模型
    dim: 768
  - name: category_id
    type: VARCHAR(32)
  - name: price
    type: FLOAT
  - name: sales_count
    type: INT32
  - name: status
    type: INT8
Index:
  - field: embedding
    type: IVF_FLAT  # 或 HNSW 用于更高性能
    metric_type: COSINE
```

#### 用户向量 Collection (user_vectors)
```yaml
Collection: user_vectors
Fields:
  - name: user_id
    type: VARCHAR(32)
    is_primary: true
  - name: embedding
    type: FLOAT_VECTOR(768)
    dim: 768
  - name: update_time
    type: INT64  # 时间戳，用于清理过期向量
Index:
  - field: embedding
    type: IVF_FLAT
    metric_type: COSINE
```

## 3. Agent 工具调用设计 (Function Calling)

### 3.1 工具定义

使用 Spring AI 的 Function Calling 能力，让 LLM 自主决定调用哪些工具获取数据：

```java
// 工具注册中心
@Component
public class AgentTools {
    
    // ========== 用户相关工具 ==========
    
    @Tool(description = "根据用户ID获取用户基础信息")
    public UserInfo getUserInfo(@ToolParam(description = "用户ID") String userId) {
        return userRepository.findByUserId(userId);
    }
    
    @Tool(description = "获取用户画像信息，包括偏好类目、价格区间、RFM得分等")
    public UserProfile getUserProfile(@ToolParam(description = "用户ID") String userId) {
        return userProfileRepository.findByUserId(userId);
    }
    
    @Tool(description = "获取用户实时行为特征，如最近1小时/24小时浏览数、点击数等")
    public UserRealtimeFeatures getUserRealtimeFeatures(
            @ToolParam(description = "用户ID") String userId) {
        return userRealtimeFeaturesRepository.findByUserId(userId);
    }
    
    @Tool(description = "获取用户最近的行为记录")
    public List<UserBehavior> getUserRecentBehaviors(
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "获取条数") int limit) {
        return userBehaviorRepository.findRecentByUserId(userId, limit);
    }
    
    // ========== 商品相关工具 ==========
    
    @Tool(description = "根据商品ID获取商品详细信息")
    public Product getProductInfo(@ToolParam(description = "商品ID") String productId) {
        return productRepository.findByProductId(productId);
    }
    
    @Tool(description = "批量获取商品信息")
    public List<Product> getProductBatch(
            @ToolParam(description = "商品ID列表") List<String> productIds) {
        return productRepository.findByProductIdIn(productIds);
    }
    
    @Tool(description = "检查商品库存状态")
    public InventoryInfo checkProductInventory(
            @ToolParam(description = "商品ID") String productId) {
        Product product = productRepository.findByProductId(productId);
        return InventoryInfo.builder()
                .productId(productId)
                .stock(product.getStock())
                .status(product.getStock() > 0 ? "available" : "out_of_stock")
                .purchaseLimit(calculatePurchaseLimit(product))
                .build();
    }
    
    @Tool(description = "批量检查多个商品的库存")
    public List<InventoryInfo> checkInventoryBatch(
            @ToolParam(description = "商品ID列表") List<String> productIds) {
        return productIds.stream()
                .map(this::checkProductInventory)
                .collect(Collectors.toList());
    }
    
    @Tool(description = "获取商品的标签信息")
    public List<Tag> getProductTags(@ToolParam(description = "商品ID") String productId) {
        return productTagRepository.findTagsByProductId(productId);
    }
    
    // ========== 推荐相关工具 ==========
    
    @Tool(description = "从向量数据库中搜索相似商品")
    public List<ProductSearchResult> vectorSearchProducts(
            @ToolParam(description = "查询向量") List<Float> queryVector,
            @ToolParam(description = "返回数量") int topK) {
        return milvusService.searchProducts(queryVector, topK);
    }
    
    @Tool(description = "基于用户画像进行向量召回")
    public List<ProductSearchResult> recallByUserVector(
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "返回数量") int topK) {
        UserProfile profile = userProfileRepository.findByUserId(userId);
        List<Float> userVector = milvusService.getUserVector(profile.getVectorId());
        return milvusService.searchProducts(userVector, topK);
    }
    
    @Tool(description = "获取热门商品列表")
    public List<Product> getHotProducts(
            @ToolParam(description = "类目ID，可为空表示全站") String categoryId,
            @ToolParam(description = "返回数量") int limit) {
        if (categoryId != null) {
            return productRepository.findHotByCategory(categoryId, limit);
        }
        return productRepository.findHotProducts(limit);
    }
    
    @Tool(description = "获取新品列表")
    public List<Product> getNewArrivals(
            @ToolParam(description = "类目ID，可为空") String categoryId,
            @ToolParam(description = "返回数量") int limit) {
        if (categoryId != null) {
            return productRepository.findNewByCategory(categoryId, limit);
        }
        return productRepository.findNewArrivals(limit);
    }
    
    @Tool(description = "基于类目偏好召回商品")
    public List<Product> recallByCategoryPreference(
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "返回数量") int limit) {
        UserProfile profile = userProfileRepository.findByUserId(userId);
        List<String> preferredCategories = parsePreferredCategories(profile);
        return productRepository.findByCategories(preferredCategories, limit);
    }
    
    // ========== 搜索相关工具 ==========
    
    @Tool(description = "基于关键词搜索商品")
    public List<Product> searchProductsByKeyword(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "返回数量") int limit) {
        return productRepository.searchByKeyword(keyword, limit);
    }
    
    @Tool(description = "获取搜索建议")
    public List<String> getSearchSuggestions(
            @ToolParam(description = "用户输入") String input,
            @ToolParam(description = "返回数量") int limit) {
        return searchSuggestionService.getSuggestions(input, limit);
    }
    
    // ========== 协同过滤工具 ==========
    
    @Tool(description = "获取相似用户购买的商品 (UserCF)")
    public List<Product> getSimilarUsersProducts(
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "返回数量") int limit) {
        return collaborativeFilterService.findSimilarUsersProducts(userId, limit);
    }
    
    @Tool(description = "获取相似商品 (ItemCF)")
    public List<Product> getSimilarProducts(
            @ToolParam(description = "商品ID") String productId,
            @ToolParam(description = "返回数量") int limit) {
        return collaborativeFilterService.findSimilarProducts(productId, limit);
    }
}
```

### 3.2 Agent 使用工具的示例

#### UserProfileAgent 重构

```java
@Component
public class UserProfileAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final AgentTools agentTools;

    private static final String SYSTEM_PROMPT = """
            你是一个电商用户画像分析专家。请使用提供的工具获取用户数据，分析用户特征并生成画像。
            
            分析步骤：
            1. 使用 getUserInfo 获取用户基础信息
            2. 使用 getUserRealtimeFeatures 获取实时行为特征
            3. 使用 getUserRecentBehaviors 获取最近行为记录
            4. 综合分析生成画像
            
            输出JSON格式:
            {
                "segments": ["high_value", "price_sensitive"],
                "preferred_categories": [{"id": "1001", "name": "手机", "weight": 0.8}],
                "preferred_brands": ["Apple", "华为"],
                "price_range": [2000, 8000],
                "rfm_score": {"recency": 0.8, "frequency": 0.5, "monetary": 0.6},
                "real_time_tags": {"活跃时段": "晚间", "偏好渠道": "移动端"}
            }
            只输出JSON，不要其他解释。
            """;

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        String userId = (String) params.get("userId");

        // LLM 自主决定调用哪些工具
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("请分析用户 " + userId + " 的画像")
                .tools(agentTools)  // 注册所有可用工具
                .call()
                .content();

        UserProfile profile = parseProfile(userId, response);

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(Map.of("profile", profile))
                .confidence(0.85)
                .build();
    }
}
```

#### ProductRecAgent 重构（召回阶段）

```java
@Component
public class ProductRecAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final AgentTools agentTools;

    private static final String RECALL_SYSTEM_PROMPT = """
            你是一个电商商品召回专家。请使用工具从多个渠道召回商品：
            
            可用召回渠道：
            1. recallByUserVector - 基于用户向量相似度召回
            2. recallByCategoryPreference - 基于用户类目偏好召回
            3. getHotProducts - 热门商品召回
            4. getNewArrivals - 新品召回
            5. getSimilarUsersProducts - 基于相似用户召回 (UserCF)
            
            请根据用户画像特征，智能选择合适的召回渠道组合。
            返回商品ID列表，最多200个候选商品。
            """;

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        String userId = (String) params.get("userId");
        UserProfile profile = (UserProfile) params.get("userProfile");

        // LLM 自主决定调用哪些召回工具
        String response = chatClient.prompt()
                .system(RECALL_SYSTEM_PROMPT)
                .user(String.format("为用户 %s 召回商品，用户画像: %s", userId, profile))
                .tools(agentTools)
                .call()
                .content();

        List<String> productIds = parseProductIds(response);
        
        // 使用工具批量获取商品详情
        List<Product> products = agentTools.getProductBatch(productIds);

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(Map.of("products", products, "recall_strategy", "llm_tool_based"))
                .confidence(0.8)
                .build();
    }
}
```

#### InventoryAgent 重构

```java
@Component
public class InventoryAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final AgentTools agentTools;

    private static final String INVENTORY_SYSTEM_PROMPT = """
            你是一个库存管理专家。请使用 checkInventoryBatch 工具批量检查商品库存，
            并根据库存情况决定：
            1. 哪些商品可以推荐（有库存）
            2. 哪些商品需要限购
            3. 哪些商品需要标记库存紧张
            
            库存阈值：
            - stock <= 0: 缺货，不可推荐
            - stock <= 50: 库存紧张，标记 urgent
            - stock <= 100: 库存较低，标记 warning
            - 热门商品(有"新品"/"旗舰"标签)且 stock <= 100: 限购2件
            """;

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        @SuppressWarnings("unchecked")
        List<Product> products = (List<Product>) params.get("products");
        
        List<String> productIds = products.stream()
                .map(Product::getProductId)
                .collect(Collectors.toList());

        // LLM 自主调用库存检查工具
        String response = chatClient.prompt()
                .system(INVENTORY_SYSTEM_PROMPT)
                .user("请检查以下商品的库存: " + productIds)
                .tools(agentTools)
                .call()
                .content();

        // 解析 LLM 的决策结果
        InventoryDecision decision = parseDecision(response);

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(Map.of(
                        "available_products", decision.getAvailableIds(),
                        "purchase_limits", decision.getPurchaseLimits(),
                        "stock_alerts", decision.getStockAlerts()
                ))
                .confidence(0.95)
                .build();
    }
}
```

### 3.3 工具调用流程图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Agent 执行流程 (使用工具调用)                      │
│                                                                         │
│  ┌─────────────┐                                                        │
│  │  接收请求    │                                                        │
│  │  userId等   │                                                        │
│  └──────┬──────┘                                                        │
│         │                                                               │
│         ▼                                                               │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  LLM 第一次调用 - 决策阶段                                        │   │
│  │                                                                  │   │
│  │  System: "你是一个电商专家，请分析需要获取哪些数据..."             │   │
│  │  User: "分析用户U123的画像"                                       │   │
│  │                                                                  │   │
│  │  LLM 决定调用:                                                    │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │ 1. getUserInfo("U123")                                   │    │   │
│  │  │ 2. getUserRealtimeFeatures("U123")                       │    │   │
│  │  │ 3. getUserRecentBehaviors("U123", 10)                    │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                             │                                           │
│                             ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  工具执行层 (Spring AI 自动调用)                                  │   │
│  │                                                                  │   │
│  │  getUserInfo ────────┐                                          │   │
│  │  getUserRealtimeFeatures ─├─► 查询 MySQL/Redis/Milvus           │   │
│  │  getUserRecentBehaviors ──┘                                     │   │
│  │                                                                  │   │
│  │  返回工具执行结果                                                 │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                             │                                           │
│                             ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  LLM 第二次调用 - 生成结果                                        │   │
│  │                                                                  │   │
│  │  System: "基于以下数据生成用户画像..."                            │   │
│  │  User: "{userInfo: ..., realtimeFeatures: ..., behaviors: ...}" │   │
│  │                                                                  │   │
│  │  LLM 生成最终画像 JSON                                            │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                             │                                           │
│                             ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  返回 AgentResult                                                │   │
│  │  {profile: ..., confidence: 0.85}                                │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.4 工具调用 vs 传统方式对比

| 维度 | 传统方式 (当前) | 工具调用方式 (新设计) |
|------|----------------|---------------------|
| **数据获取** | 代码硬编码调用顺序 | LLM 自主决策调用哪些工具 |
| **灵活性** | 低，改逻辑需改代码 | 高，调整提示词即可改变行为 |
| **可解释性** | 需看代码逻辑 | LLM 解释为什么调用这些工具 |
| **Spring AI 能力利用** | 仅使用基础 chat | 使用 Function Calling 完整能力 |
| **维护成本** | 高 | 低 |
| **调试难度** | 低 | 中等（需观察工具调用链） |

## 4. 核心流程设计

### 3.1 推荐流程 (基于工具调用)

```
用户请求推荐
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ Phase 1: 用户画像获取 (UserProfileAgent + 工具调用)           │
│                                                              │
│  LLM 决策调用:                                                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ getUserInfo(userId)                                   │   │
│  │ getUserRealtimeFeatures(userId)                       │   │
│  │ getUserRecentBehaviors(userId, 20)                    │   │
│  └──────────────────────────────────────────────────────┘   │
│                         │                                    │
│                         ▼                                    │
│  LLM 生成用户画像 JSON                                       │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ Phase 2: 多路召回 (ProductRecAgent + 工具调用)               │
│                                                              │
│  LLM 决策调用召回工具:                                        │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────┐ │
│  │recallByUserVector│ │recallByCategory  │ │getHotProducts│ │
│  │                  │ │  Preference      │ │              │ │
│  └──────────────────┘ └──────────────────┘ └──────────────┘ │
│  ┌──────────────────┐ ┌──────────────────┐                   │
│  │getNewArrivals    │ │getSimilarUsers   │                   │
│  │                  │ │  Products (UserCF)│                   │
│  └──────────────────┘ └──────────────────┘                   │
│                                                              │
│  合并结果 → 粗排 (MMR多样性控制) → Top 200候选               │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ Phase 3: 精排 (Learning to Rank)                             │
│  - 使用 getProductBatch 获取商品详情                         │
│  - 特征工程 + 排序模型                                        │
│  - 返回 Top 50                                               │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ Phase 4: 库存检查 (InventoryAgent + 工具调用)                │
│                                                              │
│  LLM 调用: checkInventoryBatch(productIds)                   │
│                                                              │
│  根据库存决策:                                                │
│  - 过滤缺货商品                                               │
│  - 标记库存紧张商品                                           │
│  - 设置限购数量                                               │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ Phase 5: 营销文案生成 (MarketingCopyAgent)                    │
│  - 基于用户画像选择模板                                       │
│  - LLM 生成个性化文案                                         │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
                    返回推荐结果
```

### 3.2 用户行为采集流程

```
用户行为发生 (点击/浏览/加购/搜索)
    │
    ▼
┌─────────────────┐
│ 1. 前端上报     │ ──► 直接调用 /api/v1/behavior API
│    埋点数据     │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ 2. 实时处理                              │
│  - 行为数据写入 user_behavior 表         │
│  - 同步更新 Redis 实时特征缓存            │
│  - 更新 user_realtime_features 表        │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 3. 触发画像更新 (异步/定时)              │
│  - 累计行为数达到阈值时触发              │
│  - 或定时任务每小时更新一次              │
│  - UserProfileAgent 使用工具获取数据:    │
│    * getUserRecentBehaviors              │
│    * getUserRealtimeFeatures             │
│  - 计算 RFM、偏好类目等                  │
│  - 更新 user_profile 表                  │
│  - 生成新的用户向量存入 Milvus           │
└─────────────────────────────────────────┘
```

### 3.3 搜索流程

```
用户输入搜索关键词
    │
    ▼
┌─────────────────────────────────────────┐
│ 1. 查询理解 (Query Understanding)       │
│  - 分词: IK/结巴分词                     │
│  - 实体识别: 品牌/类目/属性提取           │
│  - 纠错: 编辑距离/拼音纠错               │
│  - 扩展: 同义词/近义词扩展               │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 2. 多路召回 (使用工具调用)                │
│  - 文本召回: searchProductsByKeyword     │
│  - 向量召回: vectorSearchProducts        │
│  - 类目预测: 基于查询词预测类目           │
└────────────────────────┬────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────┐
│ 3. 精排 (使用工具获取特征)               │
│  - getUserProfile 获取用户画像           │
│  - getProductBatch 获取商品详情          │
│  - Learning to Rank 模型排序             │
└────────────────────────┬────────────────┘
                         │
                         ▼
                   返回搜索结果
```

## 4. API 接口设计

### 4.1 推荐接口

```http
POST /api/v1/recommend
Content-Type: application/json

Request:
{
    "user_id": "U123456",
    "scene": "home",           // 场景: home/search/cart/personal
    "num_items": 20,           // 请求数量
    "context": {               // 上下文信息
        "device_type": "mobile",
        "location": "上海",
        "current_product_id": null  // 当前浏览商品(详情页推荐用)
    }
}

Response:
{
    "request_id": "req_abc123",
    "user_id": "U123456",
    "scene": "home",
    "products": [
        {
            "product_id": "P001",
            "name": "iPhone 16 Pro",
            "price": 7999,
            "main_image": "https://...",
            "score": 0.95,
            "marketing_copy": "根据您的偏好推荐 | 新品上市",
            "rec_reason": "相似用户也在看",  // 推荐理由
            "stock_status": 1,              // 0-缺货, 1-有货, 2-紧张
            "tags": ["新品", "热销"]
        }
    ],
    "total": 20,
    "latency_ms": 150,
    "experiment_group": "treatment_llm"
}
```

### 4.2 搜索接口

```http
POST /api/v1/search
Content-Type: application/json

Request:
{
    "user_id": "U123456",
    "keyword": "苹果手机",
    "filters": {
        "category_id": "1001",
        "price_min": 5000,
        "price_max": 10000,
        "brand": ["Apple"]
    },
    "sort_by": "relevance",    // relevance/price_asc/price_desc/sales
    "page": 1,
    "page_size": 20
}

Response:
{
    "request_id": "req_def456",
    "keyword": "苹果手机",
    "corrected_keyword": null,  // 纠错后的关键词
    "suggestions": ["iphone 16", "苹果15"],  // 搜索建议
    "total": 156,
    "products": [...],
    "facets": {                 // 聚合信息
        "categories": [{"id": "1001", "name": "手机", "count": 120}],
        "brands": [{"name": "Apple", "count": 80}],
        "price_ranges": [{"min": 5000, "max": 8000, "count": 50}]
    }
}
```

### 4.3 行为采集接口

```http
POST /api/v1/behavior
Content-Type: application/json

Request:
{
    "user_id": "U123456",
    "session_id": "sess_xyz789",
    "behaviors": [
        {
            "behavior_type": "view",      // view/click/cart/favorite/search
            "product_id": "P001",
            "timestamp": 1716192000000,
            "stay_time": 15,              // 停留秒数
            "device_type": "mobile",
            "referrer": "home_recommend"  // 来源
        },
        {
            "behavior_type": "search",
            "search_keyword": "手机",
            "timestamp": 1716192005000
        }
    ]
}

Response:
{
    "success": true,
    "accepted_count": 2
}
```

### 4.4 商品详情接口

```http
GET /api/v1/product/{product_id}?user_id=U123456

Response:
{
    "product_id": "P001",
    "name": "iPhone 16 Pro",
    "description": "...",
    "price": 7999,
    "original_price": 8999,
    "main_image": "https://...",
    "images": [...],
    "stock": 500,
    "sales_count": 10000,
    "rating": 4.8,
    "category": {"id": "1001", "name": "手机"},
    "brand": "Apple",
    "seller": {"id": "S01", "name": "Apple官方旗舰店"},
    "tags": ["新品", "热销", "24期免息"],
    "specs": {...},
    "is_favorite": false,       // 当前用户是否收藏
    "similar_products": [...],  // 相似商品推荐
    "recommend_reason": "基于您的浏览历史推荐"
}
```

## 5. 前端页面设计

### 5.1 页面清单

| 页面 | 路径 | 功能描述 |
|------|------|---------|
| 首页 | `/` | 个性化推荐瀑布流、类目入口、热门商品 |
| 搜索结果页 | `/search?q=xxx` | 搜索结果列表、筛选器、排序 |
| 商品详情页 | `/product/:id` | 商品信息、相似推荐、用户评价 |
| 个人中心 | `/profile` | 用户画像展示、浏览历史、收藏列表 |

### 5.2 首页布局

```
┌─────────────────────────────────────────────────────────────┐
│  Logo    搜索框                                    用户头像   │
├─────────────────────────────────────────────────────────────┤
│  首页  |  手机数码  |  电脑办公  |  服饰鞋包  |  家居生活...   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │              轮播 Banner (运营位)                   │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │   手机   │ │   电脑   │ │   服饰   │ │   家居   │       │
│  │   数码   │ │   办公   │ │   鞋包   │ │   生活   │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  🔥 猜你喜欢                    换一批 ▶            │   │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐       │   │
│  │  │ 商品图  │ │ 商品图  │ │ 商品图  │ │ 商品图  │       │   │
│  │  │        │ │        │ │        │ │        │       │   │
│  │  │ 商品名  │ │ 商品名  │ │ 商品名  │ │ 商品名  │       │   │
│  │  │ ¥xxx   │ │ ¥xxx   │ │ ¥xxx   │ │ ¥xxx   │       │   │
│  │  │个性化文案│ │个性化文案│ │个性化文案│ │个性化文案│       │   │
│  │  └────────┘ └────────┘ └────────┘ └────────┘       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  📈 实时热销                                        │   │
│  │  ...                                               │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 商品卡片组件

```
┌─────────────────┐
│                 │
│    商品图片      │
│   (hover放大)   │
│                 │
├─────────────────┤
│ 商品名称...      │
│                 │
│ ¥7,999          │
│ ~~¥8,999~~      │
│                 │
│ 🏷️ 新品  热销    │
│                 │
│ "根据您的偏好推荐"│ ◄── 个性化营销文案
│                 │
│ 月销 1万+        │
│ ⭐ 4.8          │
└─────────────────┘
```

## 6. 技术实现要点

### 6.1 Embedding 生成策略

**商品 Embedding** (离线预计算):
```python
# 使用预训练模型 + 商品信息
商品文本 = f"{name} {category} {brand} {description} {' '.join(tags)}"
embedding = model.encode(商品文本)  # 768维
# 存入 Milvus product_vectors
```

**用户 Embedding** (实时更新):
```python
# 基于用户画像 + 实时行为
用户文本 = f"偏好类目: {categories} 偏好品牌: {brands} 价格区间: {price_range} 最近浏览: {recent_views}"
embedding = model.encode(用户文本)
# 更新 Milvus user_vectors
```

### 6.2 多路召回权重配置

```yaml
recall_channels:
  vector_search:
    weight: 0.35
    top_k: 100
  collaborative_filtering:
    weight: 0.25
    top_k: 50
  hot_items:
    weight: 0.20
    top_k: 30
  category_preference:
    weight: 0.15
    top_k: 30
  new_arrivals:
    weight: 0.05
    top_k: 20
```

### 6.3 排序模型特征

**用户特征**:
- 用户ID、用户分群、RFM得分
- 偏好类目/品牌/价格区间
- 实时特征（1h/24h浏览数、点击数）

**商品特征**:
- 商品ID、类目、品牌、价格
- 销量、评分、库存、上架时间
- 点击率、转化率历史统计

**交叉特征**:
- 用户偏好类目与商品类目匹配度
- 用户价格区间与商品价格匹配度
- 用户品牌偏好与商品品牌匹配度

## 7. 项目目录结构

```
multi-agent-ecommerce-system/
├── frontend/                          # React + Ant Design 前端
│   ├── public/
│   ├── src/
│   │   ├── components/               # 公共组件
│   │   │   ├── ProductCard/          # 商品卡片
│   │   │   ├── ProductList/          # 商品列表
│   │   │   ├── SearchBar/            # 搜索框
│   │   │   └── CategoryNav/          # 类目导航
│   │   ├── pages/                    # 页面
│   │   │   ├── Home/                 # 首页
│   │   │   ├── Search/               # 搜索结果页
│   │   │   ├── ProductDetail/        # 商品详情页
│   │   │   └── Profile/              # 个人中心
│   │   ├── services/                 # API 服务
│   │   ├── utils/                    # 工具函数
│   │   ├── hooks/                    # 自定义 Hooks
│   │   ├── store/                    # 状态管理 (Redux/Zustand)
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
│
├── backend/                          # Java 后端
│   └── java/
│       ├── src/main/java/com/ecommerce/
│       │   ├── controller/           # API 控制器
│       │   ├── service/              # 业务逻辑
│       │   ├── agent/                # Agent 实现
│       │   ├── orchestrator/         # 编排器
│       │   ├── repository/           # 数据访问层
│       │   ├── model/                # 实体类
│       │   ├── config/               # 配置类
│       │   └── utils/                # 工具类
│       └── pom.xml
│
├── embedding-service/                # Python Embedding 服务
│   ├── app/
│   │   ├── models/                   # 模型定义
│   │   ├── services/                 # 业务逻辑
│   │   └── main.py
│   ├── requirements.txt
│   └── Dockerfile
│
├── database/
│   ├── mysql/
│   │   ├── schema.sql               # 表结构
│   │   └── init_data.sql            # 初始数据
│   └── milvus/
│       └── collections.yaml         # Collection 定义
│
├── docker-compose.yml               # 完整环境编排
└── docs/
    └── system-design.md             # 本文档
```

## 8. 对话式推荐系统

### 8.1 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端对话界面                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  👤 用户                                                            │   │
│  │  "我想买一部手机，预算5000左右，用来拍照"                              │   │
│  │                                                                     │   │
│  │  🤖 智能助手                                                         │   │
│  │  "好的，我为您推荐几款拍照效果好、5000元左右的手机："                   │   │
│  │  [商品卡片1] [商品卡片2] [商品卡片3]                                  │   │
│  │                                                                     │   │
│  │  👤 用户                                                            │   │
│  │  "华为的和苹果的有啥区别？"                                           │   │
│  │                                                                     │   │
│  │  🤖 智能助手                                                         │   │
│  │  "华为Mate 70和iPhone 16的主要区别是..."                              │   │
│  │  "根据您的需求，我更推荐华为Mate 70，因为..."                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         对话编排器 (ConversationOrchestrator)                │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │ 意图识别    │  │ 记忆管理    │  │ 对话Agent   │  │ 推荐Agent   │       │
│  │ Agent       │  │ Service     │  │             │  │             │       │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘       │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                        │
│  │ 商品问答    │  │ 对比分析    │  │ 用户画像    │                        │
│  │ Agent       │  │ Agent       │  │ 更新        │                        │
│  └─────────────┘  └─────────────┘  └─────────────┘                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 对话 Agent 设计

#### 对话状态机

```
                    ┌─────────────┐
                    │   开始对话   │
                    └──────┬──────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│ 1. 意图识别 (Intent Recognition)                        │
│    - 购买意图: "我想买手机"                              │
│    - 咨询意图: "华为手机怎么样"                          │
│    - 对比意图: "华为和苹果哪个好"                        │
│    - 闲聊意图: "你好"                                   │
└────────────────────────┬────────────────────────────────┘
                         │
           ┌─────────────┼─────────────┐
           ▼             ▼             ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │ 购买意图  │  │ 咨询意图  │  │ 对比意图  │
    └────┬─────┘  └────┬─────┘  └────┬─────┘
         │             │             │
         ▼             ▼             ▼
┌─────────────────────────────────────────────────────────┐
│ 2. 信息收集 (Slot Filling)                              │
│    - 商品类目: 手机/电脑/耳机...                         │
│    - 价格区间: 5000左右                                 │
│    - 核心需求: 拍照/游戏/续航...                         │
│    - 品牌偏好: 华为/苹果/小米...                         │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│ 3. 推荐生成 (Recommendation)                            │
│    - 调用推荐 Agent 生成候选商品                         │
│    - 生成个性化推荐理由                                  │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│ 4. 对话回复 (Response Generation)                       │
│    - 自然语言回复                                       │
│    - 商品卡片展示                                       │
│    - 追问引导                                           │
└─────────────────────────────────────────────────────────┘
```

#### 对话 Agent 实现

```java
@Component
public class ConversationAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final MemoryService memoryService;
    private final IntentRecognizer intentRecognizer;
    private final RecommendationAgent recommendationAgent;

    private static final String CONVERSATION_SYSTEM_PROMPT = """
            你是一个专业的电商购物助手，通过对话帮助用户找到合适的商品。
            
            你的工作流程：
            1. 理解用户意图（购买/咨询/对比/闲聊）
            2. 提取关键信息（类目、预算、需求、品牌偏好）
            3. 调用推荐工具获取商品
            4. 生成自然、专业的回复
            
            规则：
            - 主动询问缺失的关键信息
            - 根据用户历史偏好个性化推荐
            - 可以对比多个商品帮助决策
            - 保持友好、专业的语气
            
            可用工具：
            - getUserProfile: 获取用户画像
            - getConversationHistory: 获取对话历史
            - searchProducts: 搜索商品
            - getProductDetail: 获取商品详情
            - compareProducts: 对比商品
            - generateRecommendation: 生成推荐
            """;

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        String userId = (String) params.get("userId");
        String sessionId = (String) params.get("sessionId");
        String userMessage = (String) params.get("message");

        // 1. 获取对话记忆
        ConversationMemory memory = memoryService.getMemory(sessionId);
        
        // 2. 识别意图
        Intent intent = intentRecognizer.recognize(userMessage, memory);
        
        // 3. 根据意图执行不同流程
        return switch (intent.getType()) {
            case BUY -> handleBuyIntent(userId, sessionId, userMessage, memory);
            case INQUIRE -> handleInquireIntent(userId, userMessage, memory);
            case COMPARE -> handleCompareIntent(userId, userMessage, memory);
            case CHAT -> handleChatIntent(userMessage, memory);
        };
    }

    private AgentResult handleBuyIntent(String userId, String sessionId, 
                                        String message, ConversationMemory memory) {
        // 调用 LLM 进行信息收集和推荐
        String response = chatClient.prompt()
                .system(CONVERSATION_SYSTEM_PROMPT)
                .user(buildPrompt(userId, message, memory))
                .tools(
                        getUserProfileTool,
                        searchProductsTool,
                        generateRecommendationTool
                )
                .call()
                .content();

        // 解析回复中的商品推荐
        ConversationResult result = parseConversationResult(response);
        
        // 更新记忆
        memory.addTurn(message, result.getReply());
        memory.updateExtractedInfo(result.getExtractedInfo());
        memoryService.saveMemory(sessionId, memory);

        // 更新用户画像（从对话中提取的偏好）
        updateUserProfileFromConversation(userId, result.getExtractedInfo());

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(Map.of(
                        "reply", result.getReply(),
                        "products", result.getRecommendedProducts(),
                        "extracted_info", result.getExtractedInfo()
                ))
                .confidence(result.getConfidence())
                .build();
    }

    private void updateUserProfileFromConversation(String userId, 
                                                   ExtractedInfo info) {
        // 从对话中提取的偏好更新到用户画像
        UserProfile profile = userProfileRepository.findByUserId(userId);
        
        if (info.getCategory() != null) {
            profile.addPreferredCategory(info.getCategory(), 0.8);
        }
        if (info.getPriceRange() != null) {
            profile.updatePriceRange(info.getPriceRange());
        }
        if (info.getBrands() != null) {
            profile.addPreferredBrands(info.getBrands());
        }
        if (info.getTags() != null) {
            profile.addTags(info.getTags());
        }
        
        userProfileRepository.save(profile);
    }
}
```

### 8.3 记忆机制设计

#### 记忆分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                        记忆层级                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Level 1: 对话上下文记忆 (短期)                       │   │
│  │  - 当前对话历史 (最近 10 轮)                          │   │
│  │  - 已提取的关键信息 (槽位)                            │   │
│  │  - 存储: Redis, TTL: 30 分钟                          │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Level 2: 会话级记忆 (中期)                           │   │
│  │  - 本次会话的完整对话记录                             │   │
│  │  - 用户确认的偏好和拒绝的商品                          │   │
│  │  - 存储: MySQL conversation_session 表                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Level 3: 用户长期记忆 (长期)                         │   │
│  │  - 跨会话的偏好积累                                   │   │
│  │  - 对话中提取的画像特征                               │   │
│  │  - 存储: user_profile 表                              │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 记忆服务实现

```java
@Service
public class MemoryService {

    @Autowired
    private RedisTemplate<String, ConversationMemory> redisTemplate;
    
    @Autowired
    private ConversationSessionRepository sessionRepository;

    // 获取或创建对话记忆
    public ConversationMemory getMemory(String sessionId) {
        // 1. 先从 Redis 获取短期记忆
        ConversationMemory memory = redisTemplate.opsForValue().get(
                "conv:memory:" + sessionId
        );
        
        if (memory == null) {
            // 2. 从数据库加载会话级记忆
            ConversationSession session = sessionRepository.findBySessionId(sessionId);
            if (session != null) {
                memory = ConversationMemory.fromSession(session);
            } else {
                memory = new ConversationMemory(sessionId);
            }
        }
        
        return memory;
    }

    // 保存记忆
    public void saveMemory(String sessionId, ConversationMemory memory) {
        // 1. 更新 Redis (短期记忆)
        redisTemplate.opsForValue().set(
                "conv:memory:" + sessionId,
                memory,
                Duration.ofMinutes(30)
        );
        
        // 2. 异步保存到 MySQL (会话级记忆)
        sessionRepository.save(memory.toSession());
    }

    // 获取用户跨会话记忆
    public UserConversationMemory getUserMemory(String userId) {
        // 查询用户最近的所有会话
        List<ConversationSession> sessions = sessionRepository
                .findRecentByUserId(userId, 30); // 最近30天
        
        // 聚合偏好
        UserConversationMemory userMemory = new UserConversationMemory();
        for (ConversationSession session : sessions) {
            userMemory.merge(session.getExtractedInfo());
        }
        
        return userMemory;
    }
}

// 记忆实体
@Data
public class ConversationMemory {
    private String sessionId;
    private String userId;
    private List<DialogueTurn> dialogueHistory;  // 对话历史
    private ExtractedInfo extractedInfo;          // 已提取的信息
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    
    public void addTurn(String userMessage, String assistantReply) {
        dialogueHistory.add(new DialogueTurn(userMessage, assistantReply));
        // 只保留最近 10 轮
        if (dialogueHistory.size() > 10) {
            dialogueHistory.remove(0);
        }
        lastUpdateTime = LocalDateTime.now();
    }
}

@Data
public class ExtractedInfo {
    private String category;           // 类目
    private PriceRange priceRange;     // 价格区间
    private List<String> brands;       // 品牌偏好
    private List<String> tags;         // 标签需求 (拍照/游戏等)
    private List<String> excludedProducts; // 明确拒绝的商品
}
```

#### 记忆表结构

```sql
-- 对话会话表
CREATE TABLE conversation_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话ID',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    dialogue_history TEXT COMMENT '对话历史JSON',
    extracted_info TEXT COMMENT '提取的信息JSON',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-结束, 1-进行中',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='对话会话表';

-- 对话中提取的画像更新记录表
CREATE TABLE conversation_profile_update (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    session_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话ID',
    update_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT '更新类型: category/price/brands/tags',
    old_value TEXT COMMENT '原值',
    new_value TEXT COMMENT '新值',
    confidence DECIMAL(3,2) DEFAULT 0.00 COMMENT '置信度',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB COMMENT='对话画像更新记录表';
```

### 8.4 Spring AI 能力充分利用

#### 完整工具调用链

```java
@Component
public class ConversationTools {

    // ========== 记忆相关工具 ==========
    
    @Tool(description = "获取当前对话的上下文记忆，包括最近对话历史和已提取的信息")
    public ConversationMemory getConversationMemory(
            @ToolParam(description = "会话ID") String sessionId) {
        return memoryService.getMemory(sessionId);
    }

    @Tool(description = "获取用户跨会话的长期记忆，包括历史偏好")
    public UserConversationMemory getUserConversationMemory(
            @ToolParam(description = "用户ID") String userId) {
        return memoryService.getUserMemory(userId);
    }

    @Tool(description = "保存对话信息到记忆")
    public void saveToMemory(
            @ToolParam(description = "会话ID") String sessionId,
            @ToolParam(description = "用户消息") String userMessage,
            @ToolParam(description = "助手回复") String assistantReply,
            @ToolParam(description = "提取的信息") ExtractedInfo extractedInfo) {
        ConversationMemory memory = memoryService.getMemory(sessionId);
        memory.addTurn(userMessage, assistantReply);
        memory.updateExtractedInfo(extractedInfo);
        memoryService.saveMemory(sessionId, memory);
    }

    // ========== 意图识别工具 ==========
    
    @Tool(description = "识别用户消息的意图类型")
    public Intent recognizeIntent(
            @ToolParam(description = "用户消息") String message,
            @ToolParam(description = "对话历史") List<String> history) {
        return intentRecognizer.recognize(message, history);
    }

    // ========== 推荐相关工具 ==========
    
    @Tool(description = "基于对话中提取的信息生成商品推荐")
    public List<Product> generateRecommendationFromConversation(
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "提取的信息") ExtractedInfo info,
            @ToolParam(description = "推荐数量") int limit) {
        
        // 构建推荐请求
        RecommendationRequest request = new RecommendationRequest();
        request.setUserId(userId);
        request.setScene("conversation");
        request.setNumItems(limit);
        
        // 将提取的信息作为上下文
        request.setContext(Map.of(
                "category", info.getCategory(),
                "price_min", info.getPriceRange().getMin(),
                "price_max", info.getPriceRange().getMax(),
                "brands", info.getBrands(),
                "tags", info.getTags()
        ));
        
        return recommendationAgent.recommend(request).getProducts();
    }

    @Tool(description = "对比多个商品，生成对比分析")
    public ProductComparison compareProducts(
            @ToolParam(description = "商品ID列表") List<String> productIds,
            @ToolParam(description = "对比维度") List<String> dimensions) {
        
        List<Product> products = productRepository.findByProductIdIn(productIds);
        
        // 调用 LLM 生成对比分析
        String comparisonText = chatClient.prompt()
                .system("你是一个商品对比专家，请对比以下商品：")
                .user(products.toString() + "\n对比维度：" + dimensions)
                .call()
                .content();
        
        return new ProductComparison(products, dimensions, comparisonText);
    }

    @Tool(description = "生成个性化推荐理由")
    public String generateRecommendationReason(
            @ToolParam(description = "商品ID") String productId,
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "提取的需求") ExtractedInfo info) {
        
        Product product = productRepository.findByProductId(productId);
        UserProfile profile = userProfileRepository.findByUserId(userId);
        
        return chatClient.prompt()
                .system("根据用户需求生成个性化推荐理由，突出匹配点")
                .user(String.format(
                        "商品：%s\n用户需求：%s\n用户画像：%s",
                        product, info, profile
                ))
                .call()
                .content();
    }
}
```

#### 对话流程中的 Spring AI 应用

```java
@Service
public class ConversationService {

    private final ChatClient chatClient;
    private final ConversationTools conversationTools;

    public ConversationResponse processMessage(ConversationRequest request) {
        String userId = request.getUserId();
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        // Spring AI 完整调用链
        String response = chatClient.prompt()
                .system("""
                        你是电商智能购物助手，正在与用户进行对话。
                        请使用提供的工具来完成任务：
                        1. 先获取对话记忆了解上下文
                        2. 识别用户意图
                        3. 根据意图调用相应工具
                        4. 生成自然回复
                        """)
                .user(message)
                .tools(
                        // 记忆工具
                        conversationTools.getConversationMemory,
                        conversationTools.getUserConversationMemory,
                        conversationTools.saveToMemory,
                        
                        // 意图工具
                        conversationTools.recognizeIntent,
                        
                        // 推荐工具
                        conversationTools.generateRecommendationFromConversation,
                        conversationTools.compareProducts,
                        conversationTools.generateRecommendationReason,
                        
                        // 基础工具
                        agentTools.getUserProfile,
                        agentTools.searchProductsByKeyword,
                        agentTools.getProductDetail
                )
                .call()
                .content();

        // 解析 LLM 的完整决策过程
        ConversationDecision decision = parseDecision(response);
        
        return ConversationResponse.builder()
                .reply(decision.getReply())
                .recommendedProducts(decision.getProducts())
                .suggestedQuestions(decision.getFollowUpQuestions())
                .build();
    }
}
```

### 8.5 面试亮点总结

**对话式推荐的技术亮点：**

| 亮点 | 说明 |
|------|------|
| **多轮对话管理** | 基于状态机的对话流程，支持意图切换和槽位填充 |
| **三层记忆架构** | 短期上下文 + 中期会话 + 长期画像，实现真正的个性化 |
| **Spring AI 深度应用** | 完整的工具调用链，LLM 自主决策调用哪些工具 |
| **实时画像更新** | 对话中提取的偏好实时更新到用户画像 |
| **业务价值** | 提升转化率，降低用户决策成本，增强用户体验 |

**可以说的业务场景：**
1. 淘宝 "淘宝问问" - 对话式购物助手
2. 京东 "京小智" - 智能客服 + 推荐
3. 拼多多 "多多参谋" - 对话式商品发现

## 9. Skill 系统设计

### 8.1 Skill 架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Skill Registry                          │
│                    (技能注册中心)                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  skill-config.yaml                                    │  │
│  │  - 技能名称、描述、参数定义                             │  │
│  │  - 适用场景、触发条件                                   │  │
│  │  - 权限控制                                            │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
┌─────────────────┐ ┌──────────────┐ ┌──────────────┐
│   推荐 Skill    │ │  搜索 Skill  │ │  营销 Skill  │
│                 │ │              │ │              │
│ - 多路召回       │ │ - 查询理解   │ │ - 文案生成   │
│ - 个性化排序     │ │ - 智能纠错   │ │ - 敏感词过滤 │
│ - 多样性控制     │ │ - 搜索建议   │ │ - A/B 测试   │
└─────────────────┘ └──────────────┘ └──────────────┘
```

### 8.2 Skill 定义示例

```yaml
# skills/recommendation-skill.yaml
skill:
  name: "smart_recommendation"
  displayName: "智能推荐"
  description: "基于用户画像的多路召回推荐"
  version: "1.0.0"
  
  # 触发条件
  triggers:
    - event: "page_view"
      page: "home"
    - event: "api_call"
      endpoint: "/api/v1/recommend"
  
  # 输入参数
  parameters:
    - name: "user_id"
      type: "string"
      required: true
      description: "用户ID"
    - name: "scene"
      type: "string"
      required: true
      default: "home"
      enum: ["home", "search", "cart", "detail"]
    - name: "num_items"
      type: "integer"
      required: false
      default: 20
      min: 1
      max: 100
  
  # 执行流程
  workflow:
    steps:
      - name: "profile_enrichment"
        agent: "UserProfileAgent"
        tools: ["getUserInfo", "getUserRealtimeFeatures"]
      
      - name: "multi_channel_recall"
        agent: "ProductRecAgent"
        parallel: true
        tools: 
          - "recallByUserVector"
          - "recallByCategoryPreference"
          - "getHotProducts"
          - "getNewArrivals"
      
      - name: "ranking"
        agent: "RankingAgent"
        model: "lightgbm"
      
      - name: "inventory_check"
        agent: "InventoryAgent"
        tools: ["checkInventoryBatch"]
      
      - name: "marketing_copy"
        agent: "MarketingCopyAgent"
        condition: "scene == 'home'"
  
  # 降级策略
  fallback:
    strategy: "hot_items"
    cache_ttl: 300
```

### 8.3 Skill 实现代码

```java
// backend/java/src/main/java/com/ecommerce/skill/RecommendationSkill.java

@Component
@Skill(name = "smart_recommendation")
public class RecommendationSkill implements SkillExecutor {
    
    @Autowired
    private SupervisorOrchestrator orchestrator;
    
    @Autowired
    private SkillMetrics metrics;
    
    @Override
    public SkillResult execute(SkillContext context) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 构建请求
            RecommendationRequest request = buildRequest(context);
            
            // 2. 执行推荐流程
            RecommendationResponse response = orchestrator.recommend(request);
            
            // 3. 记录指标
            metrics.recordSuccess("recommendation", 
                System.currentTimeMillis() - startTime);
            
            return SkillResult.success(response);
            
        } catch (Exception e) {
            // 4. 降级处理
            metrics.recordFailure("recommendation", e);
            return fallback(context);
        }
    }
    
    private SkillResult fallback(SkillContext context) {
        // 返回热门商品作为降级
        List<Product> hotProducts = getHotProducts(20);
        return SkillResult.success(Map.of("products", hotProducts, 
                                          "fallback", true));
    }
}
```

### 8.4 Skill 动态加载

```java
@Service
public class SkillRegistry {
    
    private Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadSkills() {
        // 从配置文件加载所有 Skill
        Resource[] resources = resourceLoader.getResources("classpath:skills/*.yaml");
        for (Resource resource : resources) {
            SkillDefinition skill = yamlMapper.readValue(resource.getInputStream(), 
                                                         SkillDefinition.class);
            skills.put(skill.getName(), skill);
        }
    }
    
    public SkillExecutor getExecutor(String skillName) {
        SkillDefinition definition = skills.get(skillName);
        return applicationContext.getBean(definition.getExecutorClass());
    }
}
```

## 9. RAG 幻觉率监控与评估

### 9.1 幻觉率定义与分类

| 幻觉类型 | 定义 | 示例 |
|---------|------|------|
| **事实性幻觉** | 生成的内容与事实不符 | 商品价格错误、库存状态错误 |
| **忠实性幻觉** | 生成内容与输入/上下文不符 | 推荐商品与用户偏好无关 |
| **来源幻觉** | 引用不存在的来源 | 声称某商品是"热销"但无数据支持 |

### 9.2 幻觉率评估指标

```java
@Component
public class HallucinationMetrics {
    
    // 1. 事实准确性 (Fact Accuracy)
    public double calculateFactAccuracy(RecommendationResponse response) {
        int total = response.getProducts().size();
        int correct = 0;
        
        for (Product product : response.getProducts()) {
            // 验证价格、库存等事实
            Product dbProduct = productRepository.findById(product.getProductId());
            if (dbProduct.getPrice() == product.getPrice() &&
                dbProduct.getStock() == product.getStock()) {
                correct++;
            }
        }
        return (double) correct / total;
    }
    
    // 2. 相关性评分 (Relevance Score)
    public double calculateRelevanceScore(String userId, 
                                          List<Product> recommendedProducts) {
        UserProfile profile = userProfileService.getProfile(userId);
        
        double totalScore = 0;
        for (Product product : recommendedProducts) {
            double score = calculateProductRelevance(product, profile);
            totalScore += score;
        }
        return totalScore / recommendedProducts.size();
    }
    
    private double calculateProductRelevance(Product product, UserProfile profile) {
        double score = 0;
        
        // 类目匹配
        if (profile.getPreferredCategories().contains(product.getCategoryId())) {
            score += 0.4;
        }
        
        // 价格区间匹配
        if (product.getPrice() >= profile.getPriceRangeMin() &&
            product.getPrice() <= profile.getPriceRangeMax()) {
            score += 0.3;
        }
        
        // 品牌匹配
        if (profile.getPreferredBrands().contains(product.getBrand())) {
            score += 0.3;
        }
        
        return score;
    }
    
    // 3. 一致性检查 (Consistency Check)
    public double calculateConsistencyScore(AgentResult result) {
        // 检查 Agent 输出与工具返回数据的一致性
        Map<String, Object> toolData = result.getToolResults();
        Map<String, Object> llmOutput = result.getLlmOutput();
        
        int consistentFields = 0;
        int totalFields = 0;
        
        for (String key : toolData.keySet()) {
            totalFields++;
            if (toolData.get(key).equals(llmOutput.get(key))) {
                consistentFields++;
            }
        }
        
        return (double) consistentFields / totalFields;
    }
}
```

### 9.3 幻觉率监控 Dashboard

```yaml
# 幻觉率监控指标
metrics:
  # 实时指标
  real_time:
    - name: "fact_accuracy_rate"
      type: "gauge"
      description: "事实准确率"
      threshold: "> 0.95"
    
    - name: "relevance_score_avg"
      type: "gauge"
      description: "平均相关性评分"
      threshold: "> 0.7"
    
    - name: "consistency_score"
      type: "gauge"
      description: "一致性评分"
      threshold: "> 0.9"
  
  # 离线评估
  offline:
    - name: "human_evaluation_score"
      type: "histogram"
      description: "人工评估评分"
      schedule: "weekly"
    
    - name: "ab_test_improvement"
      type: "counter"
      description: "A/B 测试改进幅度"
```

### 9.4 幻觉率降低方案

```java
@Service
public class HallucinationReductionService {
    
    // 方案 1: 检索增强验证 (Retrieval-Augmented Verification)
    public AgentResult verifyWithRetrieval(AgentResult result) {
        for (Product product : result.getProducts()) {
            // 从数据库重新检索验证
            Product verified = productRepository.findById(product.getProductId());
            
            // 修正不一致的字段
            if (product.getPrice() != verified.getPrice()) {
                product.setPrice(verified.getPrice());
            }
            if (product.getStock() != verified.getStock()) {
                product.setStockStatus(verified.getStock() > 0 ? "available" : "out_of_stock");
            }
        }
        return result;
    }
    
    // 方案 2: 多模型交叉验证
    public AgentResult crossValidation(AgentResult result) {
        // 使用多个 LLM 分别生成结果
        AgentResult result1 = llm1.generate(result.getPrompt());
        AgentResult result2 = llm2.generate(result.getPrompt());
        
        // 取交集或投票
        return mergeResults(result1, result2);
    }
    
    // 方案 3: 置信度阈值过滤
    public List<Product> filterByConfidence(AgentResult result, double threshold) {
        return result.getProducts().stream()
                .filter(p -> p.getConfidence() >= threshold)
                .collect(Collectors.toList());
    }
    
    // 方案 4: 人工反馈闭环
    public void collectHumanFeedback(String requestId, HumanFeedback feedback) {
        // 存储人工反馈
        feedbackRepository.save(requestId, feedback);
        
        // 触发模型微调
        if (feedback.isHallucination()) {
            modelTrainingService.addNegativeExample(requestId, feedback);
        }
    }
}
```

### 9.5 幻觉率报告示例

```json
{
    "report_id": "RPT_20240115",
    "period": "2024-01-08 ~ 2024-01-14",
    "overall_hallucination_rate": 0.032,
    "metrics": {
        "fact_accuracy": {
            "value": 0.968,
            "target": 0.95,
            "status": "pass"
        },
        "relevance_score": {
            "value": 0.823,
            "target": 0.70,
            "status": "pass"
        },
        "consistency_score": {
            "value": 0.945,
            "target": 0.90,
            "status": "pass"
        }
    },
    "breakdown_by_agent": {
        "UserProfileAgent": {
            "hallucination_rate": 0.015,
            "main_issue": "价格区间估算偏差"
        },
        "ProductRecAgent": {
            "hallucination_rate": 0.028,
            "main_issue": "召回商品类目不匹配"
        },
        "MarketingCopyAgent": {
            "hallucination_rate": 0.045,
            "main_issue": "促销信息过期"
        }
    },
    "improvement_actions": [
        "更新价格校验规则",
        "优化类目匹配算法",
        "增加促销信息实时同步"
    ]
}
```

### 9.6 企业级 RAG 幻觉率实践

**真实企业项目中的做法：**

| 实践 | 说明 | 适用场景 |
|------|------|---------|
| **在线验证** | 每个 LLM 输出都经过数据库验证 | 金融、电商交易 |
| **采样评估** | 每日抽取 1% 请求进行人工评估 | 内容推荐 |
| **A/B 测试** | 对比不同模型的幻觉率 | 模型迭代 |
| **用户反馈** | 用户可举报错误推荐 | 社区驱动 |
| **自动回滚** | 幻觉率超标自动切换模型 | 高可用场景 |

**面试可说的亮点：**
1. 设计了完整的幻觉率指标体系（事实准确、相关性、一致性）
2. 实现了实时监控和离线评估双轨制
3. 建立了人工反馈闭环，持续优化模型
4. 设计了多层次的幻觉降低方案（验证、交叉验证、置信度过滤）

## 10. 实施步骤

### Phase 1: 基础环境搭建 (1-2 天)
1. 搭建 Docker Compose 环境 (MySQL + Redis + Milvus)
2. 初始化数据库表结构和 Milvus Collection
3. 导入商品初始数据

### Phase 2: 后端核心开发 (5-7 天)
1. 实现数据访问层 (Repository)
2. 实现用户画像服务
3. 实现多路召回服务
4. 重构 Agent 和编排器
5. 实现推荐/搜索/行为采集 API

### Phase 3: Embedding 服务 (2-3 天)
1. 搭建 Python Embedding 服务
2. 实现商品批量 Embedding 和导入
3. 实现用户实时 Embedding 更新

### Phase 4: 前端开发 (4-5 天)
1. 搭建 React + Ant Design 项目
2. 实现首页、搜索页、详情页
3. 集成埋点上报

### Phase 5: 联调优化 (2-3 天)
1. 前后端联调
2. 性能优化
3. 推荐效果验证

---

**预估总工期**: 2-3 周

如需开始实施，请告诉我从哪个 Phase 开始，我可以帮您逐步生成代码。
