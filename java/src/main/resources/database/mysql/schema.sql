-- 多 Agent 电商推荐系统数据库初始化脚本
-- 数据库: ecommerce
-- 字符集: utf8mb4

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 用户表
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '用户名',
    `email` VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '手机号',
    `avatar_url` VARCHAR(255) DEFAULT '' COMMENT '头像URL',
    `register_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`),
    INDEX `idx_register_time` (`register_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基础信息表';

-- ----------------------------
-- 类目表
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `category_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '类目ID',
    `category_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '类目名称',
    `parent_id` VARCHAR(32) DEFAULT '0' COMMENT '父类目ID',
    `category_level` TINYINT DEFAULT 1 COMMENT '层级: 1-一级, 2-二级, 3-三级',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_category_id` (`category_id`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品类目表';

-- ----------------------------
-- 商品表
-- ----------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `product_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商品ID',
    `product_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '商品名称',
    `category_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '类目ID',
    `category_name` VARCHAR(50) DEFAULT '' COMMENT '类目名称',
    `brand` VARCHAR(50) DEFAULT '' COMMENT '品牌',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '价格',
    `original_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '原价',
    `product_description` TEXT COMMENT '商品描述',
    `main_image` VARCHAR(255) DEFAULT '' COMMENT '主图URL',
    `images` TEXT COMMENT '图片列表JSON字符串',
    `stock` INT DEFAULT 0 COMMENT '库存数量',
    `sales_count` INT DEFAULT 0 COMMENT '销量',
    `rating` DECIMAL(2,1) DEFAULT 5.0 COMMENT '评分 0-5',
    `product_status` TINYINT DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_product_id` (`product_id`),
    INDEX `idx_category` (`category_id`),
    INDEX `idx_price` (`price`),
    INDEX `idx_sales` (`sales_count` DESC),
    FULLTEXT INDEX `ft_name` (`product_name`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品信息表';

-- ----------------------------
-- 标签表
-- ----------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `tag_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '标签ID',
    `tag_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '标签名称',
    `tag_type` TINYINT DEFAULT 1 COMMENT '标签类型: 1-系统标签, 2-运营标签',
    `tag_status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_tag_id` (`tag_id`),
    INDEX `idx_tag_type` (`tag_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- ----------------------------
-- 商品标签关系表
-- ----------------------------
DROP TABLE IF EXISTS `product_tag`;
CREATE TABLE `product_tag` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `product_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商品ID',
    `tag_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '标签ID',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_product_tag` (`product_id`, `tag_id`),
    INDEX `idx_product` (`product_id`),
    INDEX `idx_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品标签关系表';

-- ----------------------------
-- 用户行为日志表
-- ----------------------------
DROP TABLE IF EXISTS `user_behavior`;
CREATE TABLE `user_behavior` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `product_id` VARCHAR(32) DEFAULT '' COMMENT '商品ID',
    `behavior_type` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '行为类型: view/click/cart/favorite/search',
    `search_keyword` VARCHAR(100) DEFAULT '' COMMENT '搜索关键词(仅search类型)',
    `referrer` VARCHAR(100) DEFAULT '' COMMENT '来源页面',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_time` (`user_id`, `create_time`),
    INDEX `idx_product` (`product_id`),
    INDEX `idx_behavior_time` (`behavior_type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为日志表';

-- ----------------------------
-- 用户画像表
-- ----------------------------
DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `segments` TEXT COMMENT '用户分群JSON字符串',
    `preferred_categories` TEXT COMMENT '偏好类目JSON字符串',
    `preferred_brands` TEXT COMMENT '偏好品牌JSON字符串',
    `price_range_min` DECIMAL(10,2) DEFAULT 0.00 COMMENT '价格偏好下限',
    `price_range_max` DECIMAL(10,2) DEFAULT 999999.00 COMMENT '价格偏好上限',
    `rfm_recency` DECIMAL(3,2) DEFAULT 0.00 COMMENT 'R-最近购买时间得分',
    `rfm_frequency` DECIMAL(3,2) DEFAULT 0.00 COMMENT 'F-购买频率得分',
    `rfm_monetary` DECIMAL(3,2) DEFAULT 0.00 COMMENT 'M-消费金额得分',
    `real_time_tags` TEXT COMMENT '实时标签JSON字符串',
    `vector_id` VARCHAR(64) DEFAULT '' COMMENT 'Milvus中对应的向量ID',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`),
    INDEX `idx_vector` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

-- ----------------------------
-- 用户实时特征表
-- ----------------------------
DROP TABLE IF EXISTS `user_realtime_features`;
CREATE TABLE `user_realtime_features` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `view_count_1h` INT DEFAULT 0 COMMENT '1小时浏览数',
    `view_count_24h` INT DEFAULT 0 COMMENT '24小时浏览数',
    `click_count_24h` INT DEFAULT 0 COMMENT '24小时点击数',
    `cart_count_24h` INT DEFAULT 0 COMMENT '24小时加购数',
    `last_view_product_id` VARCHAR(32) DEFAULT '' COMMENT '最后浏览商品',
    `last_view_time` DATETIME DEFAULT NULL COMMENT '最后浏览时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户实时特征表';

-- ----------------------------
-- 推荐结果缓存表
-- ----------------------------
DROP TABLE IF EXISTS `recommend_cache`;
CREATE TABLE `recommend_cache` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `cache_key` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '缓存键: user_id:scene',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `scene` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '场景: home/search/cart',
    `products` TEXT NOT NULL COMMENT '推荐商品列表JSON字符串',
    `expire_time` DATETIME NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '过期时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_cache_key` (`cache_key`),
    INDEX `idx_user_scene` (`user_id`, `scene`),
    INDEX `idx_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐结果缓存表';

-- ----------------------------
-- 对话会话表
-- ----------------------------
DROP TABLE IF EXISTS `conversation_session`;
CREATE TABLE `conversation_session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `session_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `dialogue_history` TEXT COMMENT '对话历史JSON',
    `summary` TEXT COMMENT '对话摘要（LLM生成）',
    `extracted_info` TEXT COMMENT '提取的信息JSON',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-结束, 1-进行中',
    `round_intents` TEXT COMMENT '每轮意图+实体JSON数组，最近10轮。[{"round":0,"intent":"recommend","entities":{}}]',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_session_id` (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- ----------------------------
-- AI回复反馈表
-- ----------------------------
DROP TABLE IF EXISTS `chat_feedback`;
CREATE TABLE `chat_feedback` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `session_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话ID',
    `message_index` INT DEFAULT 0 COMMENT '消息索引位置',
    `user_message` TEXT COMMENT '用户消息',
    `ai_message` TEXT COMMENT 'AI回复内容',
    `rating` TINYINT DEFAULT 0 COMMENT '评分: 1-赞, -1-踩, 0-未评价',
    `feedback_reason` VARCHAR(200) DEFAULT '' COMMENT '反馈原因标签，多选用逗号分隔',
    `feedback_comment` TEXT COMMENT '用户自由填写的反馈内容',
    `feedback_time` DATETIME DEFAULT NULL COMMENT '反馈时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_rating` (`rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI回复反馈表';

-- ----------------------------
-- 对话画像更新记录表
-- ----------------------------
DROP TABLE IF EXISTS `conversation_profile_update`;
CREATE TABLE `conversation_profile_update` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `session_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话ID',
    `update_type` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '更新类型: category/price/brands/tags',
    `old_value` TEXT COMMENT '原值',
    `new_value` TEXT COMMENT '新值',
    `confidence` DECIMAL(3,2) DEFAULT 0.00 COMMENT '置信度',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话画像更新记录表';

-- ----------------------------
-- 购物车表
-- ----------------------------
DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `product_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商品ID',
    `quantity` INT DEFAULT 1 COMMENT '数量',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- ----------------------------
-- 用户收藏表
-- ----------------------------
DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `product_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商品ID',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

-- ----------------------------
-- 会话质量指标表
-- ----------------------------
DROP TABLE IF EXISTS `session_quality_metrics`;
CREATE TABLE `session_quality_metrics` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `session_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话ID',
    `user_id` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户ID',
    `metric_type` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '指标类型: repeated_question/abrupt_end/transfer_to_human/low_engagement',
    `metric_value` TEXT COMMENT '指标详情JSON',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_metric_type` (`metric_type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话质量指标表';

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Agent质量分析结果表（离线任务产出）
-- ----------------------------
DROP TABLE IF EXISTS `agent_quality_analysis`;
CREATE TABLE `agent_quality_analysis` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `agent_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'Agent名称: recommend/product_query/knowledge_query/compare/chitchat',
    `analysis_date` DATE NOT NULL COMMENT '分析日期',
    `total_feedback` INT DEFAULT 0 COMMENT '总反馈数',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `dislike_count` INT DEFAULT 0 COMMENT '点踩数',
    `satisfaction_rate` DECIMAL(5,2) DEFAULT 0.00 COMMENT '满意度(%)',
    `top_dislike_reasons` TEXT COMMENT '差评原因Top5 JSON',
    `abrupt_end_count` INT DEFAULT 0 COMMENT '突然结束会话数',
    `repeated_question_count` INT DEFAULT 0 COMMENT '重复提问次数',
    `transfer_to_human_count` INT DEFAULT 0 COMMENT '转人工次数',
    `total_sessions` INT DEFAULT 0 COMMENT '总会话数',
    `avg_rounds` DECIMAL(5,1) DEFAULT 0.0 COMMENT '平均对话轮数',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_agent_date` (`agent_name`, `analysis_date`),
    INDEX `idx_analysis_date` (`analysis_date`),
    INDEX `idx_agent_name` (`agent_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent质量分析结果表';

