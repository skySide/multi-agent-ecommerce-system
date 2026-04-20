-- 初始数据导入脚本
-- 包含: 类目、标签、商品、测试用户

-- ----------------------------
-- 初始化类目数据
-- ----------------------------
INSERT INTO `category` (`category_id`, `category_name`, `parent_id`, `category_level`) VALUES
('1001', '手机数码', '0', 1),
('1002', '电脑办公', '0', 1),
('1003', '服饰鞋包', '0', 1),
('1004', '家居生活', '0', 1),
('1005', '美妆护肤', '0', 1),
('1101', '手机', '1001', 2),
('1102', '耳机', '1001', 2),
('1103', '平板', '1001', 2),
('1104', '配件', '1001', 2),
('1201', '笔记本', '1002', 2),
('1202', '显示器', '1002', 2),
('1203', '配件', '1002', 2);

-- ----------------------------
-- 初始化标签数据
-- ----------------------------
INSERT INTO `tag` (`tag_id`, `tag_name`, `tag_type`, `tag_status`) VALUES
('T001', '新品', 1, 1),
('T002', '热销', 1, 1),
('T003', '旗舰', 1, 1),
('T004', '性价比', 1, 1),
('T005', '降噪', 1, 1),
('T006', '快充', 1, 1),
('T007', '游戏', 1, 1),
('T008', '办公', 1, 1),
('T009', '国产', 1, 1),
('T010', '进口', 1, 1),
('T011', '24期免息', 2, 1),
('T012', '限时特惠', 2, 1);

-- ----------------------------
-- 初始化商品数据
-- ----------------------------
INSERT INTO `product` (`product_id`, `product_name`, `category_id`, `category_name`, `brand`, `price`, `original_price`, `product_description`, `main_image`, `images`, `stock`, `sales_count`, `rating`, `product_status`) VALUES
('P001', 'iPhone 16 Pro', '1101', '手机', 'Apple', 7999.00, 8999.00, '苹果最新旗舰手机，A18芯片，4800万像素', 'https://example.com/iphone16.jpg', '["https://example.com/iphone16_1.jpg","https://example.com/iphone16_2.jpg"]', 500, 10000, 4.9, 1),
('P002', '华为 Mate 70', '1101', '手机', '华为', 5999.00, 6999.00, '华为旗舰手机，麒麟芯片，徕卡影像', 'https://example.com/mate70.jpg', '["https://example.com/mate70_1.jpg"]', 300, 8000, 4.8, 1),
('P003', '小米 15 Pro', '1101', '手机', '小米', 4999.00, 5499.00, '骁龙8 Gen3，2K屏幕，120W快充', 'https://example.com/mi15.jpg', '["https://example.com/mi15_1.jpg"]', 800, 12000, 4.7, 1),
('P004', 'AirPods Pro 3', '1102', '耳机', 'Apple', 1899.00, 2299.00, '主动降噪，空间音频，30小时续航', 'https://example.com/airpods3.jpg', '["https://example.com/airpods3_1.jpg"]', 1000, 15000, 4.8, 1),
('P005', 'Sony WH-1000XM6', '1102', '耳机', 'Sony', 2499.00, 2999.00, '业界领先降噪，30小时续航', 'https://example.com/sony_xm6.jpg', '["https://example.com/sony_xm6_1.jpg"]', 200, 5000, 4.9, 1),
('P006', 'iPad Air M3', '1103', '平板', 'Apple', 4799.00, 5299.00, 'M3芯片，10.9英寸，支持Apple Pencil', 'https://example.com/ipad_air.jpg', '["https://example.com/ipad_air_1.jpg"]', 400, 6000, 4.8, 1),
('P007', '小米平板7 Pro', '1103', '平板', '小米', 2499.00, 2999.00, '骁龙8+，11英寸2.8K屏，67W快充', 'https://example.com/mipad7.jpg', '["https://example.com/mipad7_1.jpg"]', 600, 4000, 4.6, 1),
('P008', 'Anker 140W充电器', '1104', '配件', 'Anker', 399.00, 499.00, '氮化镓技术，三口输出，兼容多协议', 'https://example.com/anker140w.jpg', '["https://example.com/anker140w_1.jpg"]', 2000, 20000, 4.7, 1),
('P009', '机械革命极光X', '1201', '笔记本', '机械革命', 6999.00, 7999.00, 'i7-13650HX，RTX4060，2.5K 165Hz', 'https://example.com/jixie.jpg', '["https://example.com/jixie_1.jpg"]', 150, 3000, 4.5, 1),
('P010', '戴尔U2724D显示器', '1202', '显示器', 'Dell', 3299.00, 3999.00, '27英寸4K，IPS面板，Type-C 90W', 'https://example.com/dell_u27.jpg', '["https://example.com/dell_u27_1.jpg"]', 80, 2000, 4.8, 1);

-- ----------------------------
-- 初始化商品标签关系
-- ----------------------------
INSERT INTO `product_tag` (`product_id`, `tag_id`) VALUES
('P001', 'T001'), ('P001', 'T003'), ('P001', 'T010'), ('P001', 'T011'),
('P002', 'T001'), ('P002', 'T003'), ('P002', 'T009'),
('P003', 'T004'),
('P004', 'T001'), ('P004', 'T003'), ('P004', 'T005'), ('P004', 'T010'),
('P005', 'T003'), ('P005', 'T005'),
('P006', 'T008'),
('P007', 'T004'), ('P007', 'T006'),
('P008', 'T006'),
('P009', 'T007'),
('P010', 'T008');

-- ----------------------------
-- 初始化测试用户
-- ----------------------------
INSERT INTO `user` (`user_id`, `username`, `email`, `phone`) VALUES
('U001', '测试用户1', 'test1@example.com', '13800138001'),
('U002', '测试用户2', 'test2@example.com', '13800138002'),
('U003', '测试用户3', 'test3@example.com', '13800138003');

-- ----------------------------
-- 初始化用户画像（空）
-- ----------------------------
INSERT INTO `user_profile` (`user_id`) VALUES
('U001'), ('U002'), ('U003');

-- ----------------------------
-- 初始化用户实时特征（空）
-- ----------------------------
INSERT INTO `user_realtime_features` (`user_id`) VALUES
('U001'), ('U002'), ('U003');
