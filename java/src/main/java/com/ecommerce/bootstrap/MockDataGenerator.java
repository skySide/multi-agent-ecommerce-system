package com.ecommerce.bootstrap;

import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserProfile;
import com.ecommerce.entity.UserRealtimeFeatures;
import com.ecommerce.service.CategoryService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.UserProfileService;
import com.ecommerce.service.UserRealtimeFeaturesService;
import com.ecommerce.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 模拟数据生成器
 * 生成大量离线数据用于测试和演示
 */
@Slf4j
@Component
public class MockDataGenerator {

    @Resource
    private CategoryService categoryService;
    @Resource
    private ProductService productService;
    @Resource
    private UserService userService;
    @Resource
    private UserProfileService userProfileService;
    @Resource
    private UserRealtimeFeaturesService userRealtimeFeaturesService;

    private final Random random = new Random();

    /**
     * 生成所有模拟数据
     */
    public void generateAllMockData() {
        log.info("MockDataGenerator.generateAllMockData 开始生成模拟数据");

        // 1. 生成类目数据
        List<Category> categories = generateCategories();
        log.info("MockDataGenerator.generateAllMockData 生成 {} 个类目", categories.size());

        // 2. 生成商品数据（每个类目下10-15个商品）
        List<Product> products = generateProducts(categories);
        log.info("MockDataGenerator.generateAllMockData 生成 {} 个商品", products.size());

        // 3. 生成用户数据
        List<User> users = generateUsers(50);
        log.info("MockDataGenerator.generateAllMockData 生成 {} 个用户", users.size());

        log.info("MockDataGenerator.generateAllMockData 模拟数据生成完成");
    }

    /**
     * 生成类目数据
     */
    private List<Category> generateCategories() {
        List<Category> categories = new ArrayList<>();

        // 一级类目
        String[][] level1 = {
                {"1001", "手机数码"}, {"1002", "电脑办公"}, {"1003", "服饰鞋包"},
                {"1004", "家居生活"}, {"1005", "美妆护肤"}, {"1006", "食品饮料"},
                {"1007", "运动户外"}, {"1008", "图书音像"}, {"1009", "母婴用品"},
                {"1010", "汽车用品"}
        };

        for (String[] cat : level1) {
            categories.add(Category.builder()
                    .categoryId(cat[0])
                    .categoryName(cat[1])
                    .parentId("0")
                    .categoryLevel(1)
                    .build());
        }

        // 二级类目 - 手机数码
        categories.add(createCategory("1101", "手机", "1001", 2));
        categories.add(createCategory("1102", "耳机", "1001", 2));
        categories.add(createCategory("1103", "平板", "1001", 2));
        categories.add(createCategory("1104", "智能手表", "1001", 2));
        categories.add(createCategory("1105", "充电器/数据线", "1001", 2));

        // 二级类目 - 电脑办公
        categories.add(createCategory("1201", "笔记本", "1002", 2));
        categories.add(createCategory("1202", "显示器", "1002", 2));
        categories.add(createCategory("1203", "键盘鼠标", "1002", 2));
        categories.add(createCategory("1204", "打印机", "1002", 2));
        categories.add(createCategory("1205", "存储设备", "1002", 2));

        // 二级类目 - 服饰鞋包
        categories.add(createCategory("1301", "男装", "1003", 2));
        categories.add(createCategory("1302", "女装", "1003", 2));
        categories.add(createCategory("1303", "运动鞋", "1003", 2));
        categories.add(createCategory("1304", "箱包", "1003", 2));
        categories.add(createCategory("1305", "配饰", "1003", 2));

        // 二级类目 - 家居生活
        categories.add(createCategory("1401", "家具", "1004", 2));
        categories.add(createCategory("1402", "家纺", "1004", 2));
        categories.add(createCategory("1403", "厨具", "1004", 2));
        categories.add(createCategory("1404", "灯具", "1004", 2));
        categories.add(createCategory("1405", "收纳整理", "1004", 2));

        // 二级类目 - 美妆护肤
        categories.add(createCategory("1501", "面部护肤", "1005", 2));
        categories.add(createCategory("1502", "彩妆", "1005", 2));
        categories.add(createCategory("1503", "香水", "1005", 2));
        categories.add(createCategory("1504", "美发护发", "1005", 2));
        categories.add(createCategory("1505", "美容仪器", "1005", 2));

        // 二级类目 - 食品饮料
        categories.add(createCategory("1601", "休闲零食", "1006", 2));
        categories.add(createCategory("1602", "饮料冲调", "1006", 2));
        categories.add(createCategory("1603", "粮油调味", "1006", 2));
        categories.add(createCategory("1604", "生鲜水果", "1006", 2));
        categories.add(createCategory("1605", "进口食品", "1006", 2));

        // 二级类目 - 运动户外
        categories.add(createCategory("1701", "运动鞋服", "1007", 2));
        categories.add(createCategory("1702", "健身器材", "1007", 2));
        categories.add(createCategory("1703", "户外装备", "1007", 2));
        categories.add(createCategory("1704", "球类运动", "1007", 2));
        categories.add(createCategory("1705", "骑行装备", "1007", 2));

        // 二级类目 - 图书音像
        categories.add(createCategory("1801", "文学小说", "1008", 2));
        categories.add(createCategory("1802", "科技计算机", "1008", 2));
        categories.add(createCategory("1803", "教育考试", "1008", 2));
        categories.add(createCategory("1804", "经管励志", "1008", 2));
        categories.add(createCategory("1805", "儿童读物", "1008", 2));

        // 二级类目 - 母婴用品
        categories.add(createCategory("1901", "奶粉", "1009", 2));
        categories.add(createCategory("1902", "尿裤湿巾", "1009", 2));
        categories.add(createCategory("1903", "婴儿服饰", "1009", 2));
        categories.add(createCategory("1904", "玩具", "1009", 2));
        categories.add(createCategory("1905", "孕产用品", "1009", 2));

        // 二级类目 - 汽车用品
        categories.add(createCategory("2001", "车载电器", "1010", 2));
        categories.add(createCategory("2002", "汽车装饰", "1010", 2));
        categories.add(createCategory("2003", "维修保养", "1010", 2));
        categories.add(createCategory("2004", "轮胎轮毂", "1010", 2));
        categories.add(createCategory("2005", "美容清洗", "1010", 2));

        categoryService.saveBatch(categories);
        return categories;
    }

    private Category createCategory(String id, String name, String parentId, int level) {
        return Category.builder()
                .categoryId(id)
                .categoryName(name)
                .parentId(parentId)
                .categoryLevel(level)
                .build();
    }

    /**
     * 生成商品数据
     */
    private List<Product> generateProducts(List<Category> categories) {
        List<Product> products = new ArrayList<>();

        // 获取二级类目
        List<Category> level2Categories = categories.stream()
                .filter(c -> c.getCategoryLevel() == 2)
                .toList();

        int productIndex = 1;
        for (Category category : level2Categories) {
            // 每个类目生成 4-8 个商品
            int count = 4 + random.nextInt(5);
            List<Product> categoryProducts = generateProductsForCategory(
                    category, productIndex, count);
            products.addAll(categoryProducts);
            productIndex += count;
        }

        productService.saveBatch(products);
        return products;
    }

    /**
     * 为指定类目生成商品
     */
    private List<Product> generateProductsForCategory(Category category, int startIndex, int count) {
        List<Product> products = new ArrayList<>();

        // 预定义各类目的商品模板
        List<ProductTemplate> templates = getTemplatesForCategory(category.getCategoryId());

        for (int i = 0; i < count && i < templates.size(); i++) {
            ProductTemplate template = templates.get(i);
            String productId = String.format("P%03d", startIndex + i);

            BigDecimal rating = new BigDecimal("3.5").add(
                    new BigDecimal(random.nextDouble()).multiply(new BigDecimal("1.5")));
            Product product = Product.builder()
                    .productId(productId)
                    .productName(template.name)
                    .categoryId(category.getCategoryId())
                    .categoryName(category.getCategoryName())
                    .brand(template.brand)
                    .price(template.price)
                    .originalPrice(template.originalPrice)
                    .productDescription(template.description)
                    .mainImage("https://example.com/" + productId.toLowerCase() + ".jpg")
                    .images("[\"https://example.com/" + productId.toLowerCase() + "_1.jpg\"]")
                    .stock(100 + random.nextInt(900))
                    .salesCount(random.nextInt(5000))
                    .rating(rating)
                    .productStatus(1)
                    .build();

            products.add(product);
        }

        return products;
    }

    /**
     * 获取各类目的商品模板
     */
    private List<ProductTemplate> getTemplatesForCategory(String categoryId) {
        return switch (categoryId) {
            // 手机数码-手机
            case "1101" -> Arrays.asList(
                    new ProductTemplate("iPhone 16 Pro Max", "Apple", new BigDecimal("9999"), new BigDecimal("10999"), "苹果最新旗舰手机，A18 Pro芯片，6.9英寸OLED屏幕，4800万像素主摄，钛金属机身，支持卫星通信"),
                    new ProductTemplate("华为 Mate 70 Pro", "华为", new BigDecimal("6999"), new BigDecimal("7999"), "华为旗舰手机，麒麟9100芯片，鸿蒙OS 5.0，徕卡影像系统，100W快充，卫星通话"),
                    new ProductTemplate("小米 15 Ultra", "小米", new BigDecimal("5999"), new BigDecimal("6999"), "骁龙8 Gen4，2K OLED屏幕，1英寸大底主摄，120W快充，IP68防水"),
                    new ProductTemplate("OPPO Find X8 Pro", "OPPO", new BigDecimal("5499"), new BigDecimal("6499"), "天玑9400，哈苏影像，2K LTPO屏幕，100W快充，超薄指纹"),
                    new ProductTemplate("vivo X200 Pro", "vivo", new BigDecimal("5299"), new BigDecimal("6299"), "蔡司影像，天玑9400，2K E7屏幕，120W快充，超声波指纹"),
                    new ProductTemplate("三星 Galaxy S25 Ultra", "Samsung", new BigDecimal("8999"), new BigDecimal("9999"), "骁龙8 Gen4，2亿像素主摄，S Pen手写笔，钛金属边框，AI功能全面升级"),
                    new ProductTemplate("荣耀 Magic7 Pro", "荣耀", new BigDecimal("4999"), new BigDecimal("5999"), "青海湖电池，鹰眼相机，MagicOS 9.0，100W快充，3D人脸解锁"),
                    new ProductTemplate("一加 13", "一加", new BigDecimal("4299"), new BigDecimal("5299"), "哈苏影像，骁龙8 Gen4，2K东方屏，100W快充，三段式按键")
            );
            // 手机数码-耳机
            case "1102" -> Arrays.asList(
                    new ProductTemplate("AirPods Pro 3", "Apple", new BigDecimal("1999"), new BigDecimal("2499"), "主动降噪，空间音频，USB-C充电，30小时续航，自适应音频"),
                    new ProductTemplate("Sony WH-1000XM6", "Sony", new BigDecimal("2499"), new BigDecimal("2999"), "业界领先降噪，LDAC高清传输，30小时续航，多点连接"),
                    new ProductTemplate("华为 FreeBuds Pro 4", "华为", new BigDecimal("1299"), new BigDecimal("1599"), "智慧动态降噪，星闪连接，超CD级音质，40小时续航"),
                    new ProductTemplate("Bose QuietComfort Ultra", "Bose", new BigDecimal("2299"), new BigDecimal("2799"), "沉浸空间音频，CustomTune智能调音，24小时续航"),
                    new ProductTemplate("森海塞尔 Momentum 4", "Sennheiser", new BigDecimal("2199"), new BigDecimal("2699"), "自适应降噪，42mm动圈，60小时续航，aptX Adaptive"),
                    new ProductTemplate("Beats Studio Pro", "Beats", new BigDecimal("1799"), new BigDecimal("2299"), "主动降噪，空间音频，USB-C无损音频，40小时续航")
            );
            // 手机数码-平板
            case "1103" -> Arrays.asList(
                    new ProductTemplate("iPad Pro M4 12.9英寸", "Apple", new BigDecimal("8999"), new BigDecimal("9999"), "M4芯片，Mini-LED屏幕，120Hz ProMotion，支持Apple Pencil Pro"),
                    new ProductTemplate("iPad Air M3", "Apple", new BigDecimal("4799"), new BigDecimal("5499"), "M3芯片，10.9英寸Liquid屏，支持Apple Pencil USB-C"),
                    new ProductTemplate("华为 MatePad Pro 13.2", "华为", new BigDecimal("4999"), new BigDecimal("5999"), "麒麟9000S，OLED屏幕，星闪手写笔，鸿蒙OS"),
                    new ProductTemplate("小米平板6S Pro", "小米", new BigDecimal("3299"), new BigDecimal("3999"), "骁龙8 Gen2，12.4英寸3K屏，120W快充，PC级WPS"),
                    new ProductTemplate("三星 Galaxy Tab S10+", "Samsung", new BigDecimal("6999"), new BigDecimal("7999"), "骁龙8 Gen3，12.4英寸AMOLED，S Pen内置")
            );
            // 手机数码-智能手表
            case "1104" -> Arrays.asList(
                    new ProductTemplate("Apple Watch Ultra 3", "Apple", new BigDecimal("6299"), new BigDecimal("6999"), "钛金属表壳，双频GPS，深度计，100米防水，36小时续航"),
                    new ProductTemplate("华为 Watch GT5 Pro", "华为", new BigDecimal("2488"), new BigDecimal("2988"), "钛金属表壳，ECG心电分析，100+运动模式，14天续航"),
                    new ProductTemplate("小米 Watch S4", "小米", new BigDecimal("999"), new BigDecimal("1299"), "eSIM独立通话，澎湃OS，150+运动模式，15天续航"),
                    new ProductTemplate("Garmin Fenix 8", "Garmin", new BigDecimal("7480"), new BigDecimal("8480"), "太阳能充电，多频多星定位，潜水电脑，47天续航")
            );
            // 手机数码-充电器
            case "1105" -> Arrays.asList(
                    new ProductTemplate("Anker 140W氮化镓充电器", "Anker", new BigDecimal("399"), new BigDecimal("499"), "三口输出，兼容PD3.1，智能温控，可折叠插脚"),
                    new ProductTemplate("倍思 100W氮化镓", "Baseus", new BigDecimal("199"), new BigDecimal("299"), "四口输出，兼容多种协议，数字显示屏"),
                    new ProductTemplate("绿联 200W桌面充电器", "UGREEN", new BigDecimal("499"), new BigDecimal("599"), "六口输出，支持笔记本充电，桌面充电站"),
                    new ProductTemplate("小米 120W充电器", "小米", new BigDecimal("249"), new BigDecimal("299"), "官方原装，支持小米全系快充，智能温控")
            );
            // 电脑办公-笔记本
            case "1201" -> Arrays.asList(
                    new ProductTemplate("MacBook Pro 16 M3 Max", "Apple", new BigDecimal("19999"), new BigDecimal("22999"), "M3 Max芯片，16英寸Liquid Retina XDR，36GB内存，1TB SSD"),
                    new ProductTemplate("ThinkPad X1 Carbon Gen12", "联想", new BigDecimal("12999"), new BigDecimal("14999"), "Ultra 7处理器，2.8K OLED，1.1kg轻薄，军工品质"),
                    new ProductTemplate("戴尔 XPS 15", "Dell", new BigDecimal("15999"), new BigDecimal("18999"), "Ultra 9处理器，3.5K OLED触控屏，RTX 4060，CNC机身"),
                    new ProductTemplate("华硕灵耀14", "华硕", new BigDecimal("6999"), new BigDecimal("7999"), "Ultra 7，2.8K OLED，1.2kg，75Wh电池"),
                    new ProductTemplate("机械革命极光Pro", "机械革命", new BigDecimal("7999"), new BigDecimal("8999"), "i7-14650HX，RTX4060，2.5K 165Hz，独显直连"),
                    new ProductTemplate("华为 MateBook X Pro", "华为", new BigDecimal("10999"), new BigDecimal("12999"), "Ultra 9，3.1K OLED触控，微绒机身，980g超轻")
            );
            // 电脑办公-显示器
            case "1202" -> Arrays.asList(
                    new ProductTemplate("戴尔U2724DE 27英寸", "Dell", new BigDecimal("3499"), new BigDecimal("4299"), "27英寸4K，IPS Black，100%sRGB，Type-C 90W供电"),
                    new ProductTemplate("LG 32GS95UE 32英寸", "LG", new BigDecimal("7999"), new BigDecimal("9999"), "32英寸4K OLED，240Hz，0.03ms，HDR400"),
                    new ProductTemplate("华硕ProArt PA279CRV", "华硕", new BigDecimal("3999"), new BigDecimal("4999"), "27英寸4K，Calman认证，100%sRGB，Type-C 96W"),
                    new ProductTemplate("小米Redmi 27寸4K", "小米", new BigDecimal("1499"), new BigDecimal("1999"), "27英寸4K，IPS，100%sRGB，Type-C 65W")
            );
            // 电脑办公-键盘鼠标
            case "1203" -> Arrays.asList(
                    new ProductTemplate("罗技MX Master 3S", "罗技", new BigDecimal("699"), new BigDecimal("899"), "8000DPI静音，MagSpeed电磁滚轮，多设备切换，70天续航"),
                    new ProductTemplate("Keychron Q1 Pro", "Keychron", new BigDecimal("998"), new BigDecimal("1298"), "全铝机身， gasket结构，热插拔，蓝牙双模"),
                    new ProductTemplate("雷蛇毒蝰V3 Pro", "雷蛇", new BigDecimal("1299"), new BigDecimal("1599"), "54g轻量化，Focus Pro 35K传感器，8KHz轮询率"),
                    new ProductTemplate("ROG夜魔", "华硕", new BigDecimal("1799"), new BigDecimal("2199"), "75%配列，OLED显示屏，三模连接， gasket结构")
            );
            // 电脑办公-打印机
            case "1204" -> Arrays.asList(
                    new ProductTemplate("惠普 LaserJet Pro M404", "惠普", new BigDecimal("1899"), new BigDecimal("2299"), "黑白激光，40ppm，自动双面，无线网络"),
                    new ProductTemplate("佳能 PIXMA G680", "佳能", new BigDecimal("1599"), new BigDecimal("1999"), "六色墨仓，照片打印，无线打印，低成本"),
                    new ProductTemplate("爱普生 L8168", "爱普生", new BigDecimal("2999"), new BigDecimal("3499"), "六色专业照片打印，自动双面，4.3寸触屏")
            );
            // 电脑办公-存储设备
            case "1205" -> Arrays.asList(
                    new ProductTemplate("三星T9移动固态硬盘 2TB", "三星", new BigDecimal("1299"), new BigDecimal("1599"), "2000MB/s，USB 3.2 Gen2x2，IP65防护"),
                    new ProductTemplate("闪迪E81至尊超极速 4TB", "闪迪", new BigDecimal("2499"), new BigDecimal("2999"), "2000MB/s，密码保护，IP55防护"),
                    new ProductTemplate("致态TiPlus7100 2TB", "致态", new BigDecimal("899"), new BigDecimal("1099"), "PCIe 4.0，7000MB/s，长江存储原厂颗粒")
            );
            // 服饰鞋包-男装
            case "1301" -> Arrays.asList(
                    new ProductTemplate("优衣库轻薄羽绒服", "优衣库", new BigDecimal("499"), new BigDecimal("599"), "90%白鸭绒，超轻便携，防钻绒工艺，多色可选"),
                    new ProductTemplate("海澜之家羊毛大衣", "海澜之家", new BigDecimal("899"), new BigDecimal("1299"), "100%羊毛，经典翻领，修身版型，秋冬必备"),
                    new ProductTemplate("李宁运动卫衣", "李宁", new BigDecimal("299"), new BigDecimal("399"), "纯棉面料，宽松版型，国潮印花，舒适透气"),
                    new ProductTemplate("波司登极寒羽绒服", "波司登", new BigDecimal("1999"), new BigDecimal("2599"), "鹅绒填充，GORE-TEX面料，-30°C防寒")
            );
            // 服饰鞋包-女装
            case "1302" -> Arrays.asList(
                    new ProductTemplate("ZARA针织连衣裙", "ZARA", new BigDecimal("399"), new BigDecimal("499"), "柔软针织，修身剪裁，优雅气质，秋冬新款"),
                    new ProductTemplate("优衣库HEATTECH保暖内衣", "优衣库", new BigDecimal("99"), new BigDecimal("149"), "吸湿发热，轻薄保暖，多色可选，打底必备"),
                    new ProductTemplate("太平鸟毛呢外套", "太平鸟", new BigDecimal("799"), new BigDecimal("1099"), "双面呢面料，宽松版型，腰带设计，通勤优雅")
            );
            // 服饰鞋包-运动鞋
            case "1303" -> Arrays.asList(
                    new ProductTemplate("Nike Air Max Dn", "Nike", new BigDecimal("1099"), new BigDecimal("1299"), "Dynamic Air气垫，透气网面，缓震回弹，潮流配色"),
                    new ProductTemplate("Adidas Ultraboost 5", "Adidas", new BigDecimal("1199"), new BigDecimal("1399"), "Boost中底，Primeknit鞋面，Continental橡胶底"),
                    new ProductTemplate("李宁绝影3", "李宁", new BigDecimal("899"), new BigDecimal("1099"), "䨻科技中底，弜结构，碳板支撑，专业跑鞋"),
                    new ProductTemplate("安踏C202 5代", "安踏", new BigDecimal("599"), new BigDecimal("799"), "氮科技中底，碳板推进，轻量竞速")
            );
            // 服饰鞋包-箱包
            case "1304" -> Arrays.asList(
                    new ProductTemplate("新秀丽拉杆箱 20寸", "新秀丽", new BigDecimal("1299"), new BigDecimal("1699"), "PC材质，TSA海关锁，静音万向轮，轻量化设计"),
                    new ProductTemplate("TUMI Alpha Bravo背包", "TUMI", new BigDecimal("2999"), new BigDecimal("3999"), "弹道尼龙，多隔层设计，15寸电脑仓，商务差旅"),
                    new ProductTemplate("Coach Tabby单肩包", "Coach", new BigDecimal("3200"), new BigDecimal("4200"), "经典鹅卵石纹，金属扣饰，多色可选，时尚百搭")
            );
            // 服饰鞋包-配饰
            case "1305" -> Arrays.asList(
                    new ProductTemplate("Ray-Ban RB3025飞行员墨镜", "Ray-Ban", new BigDecimal("1080"), new BigDecimal("1380"), "经典飞行员款，G-15镜片，UV400防护"),
                    new ProductTemplate("卡西欧G-SHOCK GA-2100", "卡西欧", new BigDecimal("890"), new BigDecimal("1090"), "碳纤维核心防护，超薄设计，200米防水，农家橡树"),
                    new ProductTemplate("周大福黄金项链", "周大福", new BigDecimal("2999"), new BigDecimal("3999"), "足金999，经典设计，精致工艺，送礼佳品")
            );
            // 家居生活-家具
            case "1401" -> Arrays.asList(
                    new ProductTemplate("宜家POÄNG波昂扶手椅", "宜家", new BigDecimal("499"), new BigDecimal("599"), "桦木贴面，弯曲胶合板，舒适坐垫，经典设计"),
                    new ProductTemplate("源氏木语实木床1.8m", "源氏木语", new BigDecimal("2999"), new BigDecimal("3999"), "橡木全实木，环保漆料，加粗床腿，稳固耐用"),
                    new ProductTemplate("顾家家居真皮沙发", "顾家家居", new BigDecimal("5999"), new BigDecimal("7999"), "头层牛皮，电动功能位，USB充电，舒适承托")
            );
            // 家居生活-家纺
            case "1402" -> Arrays.asList(
                    new ProductTemplate("富安娜四件套", "富安娜", new BigDecimal("599"), new BigDecimal("899"), "100%长绒棉，60支贡缎，活性印染，亲肤舒适"),
                    new ProductTemplate("罗莱家纺鹅绒被", "罗莱", new BigDecimal("1999"), new BigDecimal("2999"), "95%白鹅绒，蓬松度800+，防钻绒面料，轻盈保暖"),
                    new ProductTemplate("网易严选乳胶枕", "网易严选", new BigDecimal("199"), new BigDecimal("299"), "93%天然乳胶，波浪曲线，抑菌防螨，护颈设计")
            );
            // 家居生活-厨具
            case "1403" -> Arrays.asList(
                    new ProductTemplate("双立人刀具套装", "双立人", new BigDecimal("1299"), new BigDecimal("1699"), "德国进口钢材，冰锻工艺，人体工学手柄，锋利耐用"),
                    new ProductTemplate("苏泊尔IH电饭煲", "苏泊尔", new BigDecimal("599"), new BigDecimal("799"), "IH电磁加热，球釜内胆，智能预约，5L大容量"),
                    new ProductTemplate("美的空气炸锅", "美的", new BigDecimal("299"), new BigDecimal("399"), "6L大容量，无油烹饪，智能触控，360°热风循环")
            );
            // 家居生活-灯具
            case "1404" -> Arrays.asList(
                    new ProductTemplate("飞利浦智睿吸顶灯", "飞利浦", new BigDecimal("399"), new BigDecimal("599"), "智能调光调色，APP控制，小爱同学联动，Ra95高显色"),
                    new ProductTemplate("Yeelight智能台灯", "Yeelight", new BigDecimal("299"), new BigDecimal("399"), "国AA级照度，无蓝光危害，智能调光，APP控制"),
                    new ProductTemplate("欧普照明LED筒灯", "欧普", new BigDecimal("29"), new BigDecimal("49"), "3W高亮，3000K暖白光，开孔75mm，家装必备")
            );
            // 家居生活-收纳
            case "1405" -> Arrays.asList(
                    new ProductTemplate("茶花塑料收纳箱", "茶花", new BigDecimal("59"), new BigDecimal("89"), "PP材质，透明可视，带盖防尘，多规格可选"),
                    new ProductTemplate("宜家SAMLA收纳盒", "宜家", new BigDecimal("29"), new BigDecimal("39"), "透明塑料，叠放设计，多尺寸，杂物收纳"),
                    new ProductTemplate("太力真空压缩袋", "太力", new BigDecimal("49"), new BigDecimal("69"), "免抽气设计，立体收纳，防潮防霉，节省空间")
            );
            // 美妆护肤-面部护肤
            case "1501" -> Arrays.asList(
                    new ProductTemplate("SK-II神仙水 230ml", "SK-II", new BigDecimal("1540"), new BigDecimal("1890"), "Pitera精华，调节水油平衡，改善肤质，晶莹剔透"),
                    new ProductTemplate("雅诗兰黛小棕瓶 50ml", "雅诗兰黛", new BigDecimal("935"), new BigDecimal("1180"), "第七代小棕瓶，Chronolux CB科技，修护肌底"),
                    new ProductTemplate("兰蔻小黑瓶 50ml", "兰蔻", new BigDecimal("1080"), new BigDecimal("1350"), "酵母精华，强维稳快修护，改善细纹"),
                    new ProductTemplate("珀莱雅双抗精华", "珀莱雅", new BigDecimal("299"), new BigDecimal("399"), "抗氧化抗糖化，虾青素+肌肽，提亮肤色")
            );
            // 美妆护肤-彩妆
            case "1502" -> Arrays.asList(
                    new ProductTemplate("YSL小金条口红", "YSL", new BigDecimal("390"), new BigDecimal("450"), "哑光质地，高饱和显色，皮革哑光，持久不脱色"),
                    new ProductTemplate("Dior迪奥999口红", "Dior", new BigDecimal("380"), new BigDecimal("450"), "经典正红色，丝绒质地，显白百搭，高级妆感"),
                    new ProductTemplate("完美日记动物眼影盘", "完美日记", new BigDecimal("129"), new BigDecimal("169"), "12色眼影，珠光哑光混搭，显色度高，新手友好")
            );
            // 美妆护肤-香水
            case "1503" -> Arrays.asList(
                    new ProductTemplate("香奈儿五号之水", "Chanel", new BigDecimal("1180"), new BigDecimal("1420"), "经典花香调，清新现代，淡雅迷人，女性魅力"),
                    new ProductTemplate("Dior Sauvage旷野", "Dior", new BigDecimal("860"), new BigDecimal("1020"), "东方木质调，清新辛辣，成熟魅力，持久留香"),
                    new ProductTemplate("Jo Malone英国梨", "Jo Malone", new BigDecimal("630"), new BigDecimal("780"), "果香调，清新甜美，英伦优雅，百搭日常")
            );
            // 美妆护肤-美发护发
            case "1504" -> Arrays.asList(
                    new ProductTemplate("卡诗菁纯护发精油", "卡诗", new BigDecimal("420"), new BigDecimal("520"), "山茶花精粹，深层滋养，修复干枯，光泽柔顺"),
                    new ProductTemplate("戴森Supersonic吹风机", "Dyson", new BigDecimal("2999"), new BigDecimal("3399"), "V9数码马达，智能温控，快速干发，呵护秀发"),
                    new ProductTemplate("施华蔻BC修护洗发水", "施华蔻", new BigDecimal("168"), new BigDecimal("218"), "无硅油配方，深层清洁，修复受损发质")
            );
            // 美妆护肤-美容仪器
            case "1505" -> Arrays.asList(
                    new ProductTemplate("雅萌ACE五代美容仪", "雅萌", new BigDecimal("3999"), new BigDecimal("4999"), "射频+微电流+红光，紧致提拉，淡化细纹"),
                    new ProductTemplate("AMIRO觅光射频仪", "AMIRO", new BigDecimal("2199"), new BigDecimal("2799"), "六极射频，智能温控，促进胶原再生"),
                    new ProductTemplate("Ulike蓝宝石脱毛仪", "Ulike", new BigDecimal("1899"), new BigDecimal("2299"), "冰点无痛，IPL强脉冲光，全身可用")
            );
            // 食品饮料-休闲零食
            case "1601" -> Arrays.asList(
                    new ProductTemplate("三只松鼠坚果礼盒", "三只松鼠", new BigDecimal("128"), new BigDecimal("168"), "每日坚果混合装，7种坚果果干，独立包装，营养均衡"),
                    new ProductTemplate("良品铺子猪肉脯", "良品铺子", new BigDecimal("35"), new BigDecimal("49"), "靖江风味，精选猪后腿肉，蜜汁烤制，嚼劲十足"),
                    new ProductTemplate("乐事薯片大礼包", "乐事", new BigDecimal("39"), new BigDecimal("59"), "多种口味混合装，薄脆可口，聚会分享"),
                    new ProductTemplate("费列罗巧克力", "费列罗", new BigDecimal("88"), new BigDecimal("118"), "榛果威化巧克力，金箔包装，意大利进口")
            );
            // 食品饮料-饮料冲调
            case "1602" -> Arrays.asList(
                    new ProductTemplate("星巴克咖啡豆 250g", "星巴克", new BigDecimal("85"), new BigDecimal("108"), "阿拉比卡豆，深度烘焙，浓郁香醇，居家享受"),
                    new ProductTemplate("立顿红茶包", "立顿", new BigDecimal("29"), new BigDecimal("39"), "精选红茶，茶包设计，方便快捷，100包大包装"),
                    new ProductTemplate("元气森林气泡水", "元气森林", new BigDecimal("65"), new BigDecimal("89"), "0糖0脂0卡，多种口味，清爽解腻，整箱15瓶")
            );
            // 食品饮料-粮油调味
            case "1603" -> Arrays.asList(
                    new ProductTemplate("金龙鱼花生油 5L", "金龙鱼", new BigDecimal("89"), new BigDecimal("119"), "物理压榨，一级品质，浓香纯正，家庭必备"),
                    new ProductTemplate("海天酱油生抽", "海天", new BigDecimal("15"), new BigDecimal("22"), "非转基因大豆酿造，鲜味十足，烹饪必备"),
                    new ProductTemplate("十月稻田五常大米", "十月稻田", new BigDecimal("68"), new BigDecimal("88"), "五常核心产区，稻花香2号，软糯香甜，10斤装")
            );
            // 食品饮料-生鲜水果
            case "1604" -> Arrays.asList(
                    new ProductTemplate("智利车厘子JJJ级", "生鲜", new BigDecimal("199"), new BigDecimal("299"), "果径30-32mm，脆甜多汁，新鲜空运，2斤装"),
                    new ProductTemplate("丹东99草莓", "生鲜", new BigDecimal("89"), new BigDecimal("129"), "红颜草莓，个大饱满，香甜可口，3斤装"),
                    new ProductTemplate("新西兰阳光金果", "生鲜", new BigDecimal("128"), new BigDecimal("168"), "Zespri金果，维C之王，香甜细腻，12个装")
            );
            // 食品饮料-进口食品
            case "1605" -> Arrays.asList(
                    new ProductTemplate("泰国金枕榴莲", "进口", new BigDecimal("299"), new BigDecimal("399"), "树熟金枕，果肉饱满，绵密香甜，约4-5斤"),
                    new ProductTemplate("澳洲安格斯牛排", "进口", new BigDecimal("188"), new BigDecimal("258"), "M3等级，原切牛排，雪花均匀，1kg装"),
                    new ProductTemplate("日本北海道上奶粉", "进口", new BigDecimal("168"), new BigDecimal("218"), "北海道奶源，全脂奶粉，高钙高蛋白，1kg")
            );
            // 运动户外-运动鞋服
            case "1701" -> Arrays.asList(
                    new ProductTemplate("Lululemon Align瑜伽裤", "Lululemon", new BigDecimal("850"), new BigDecimal("1080"), "Nulu面料，裸感体验，高腰设计，多色可选"),
                    new ProductTemplate("始祖鸟Beta LT冲锋衣", "始祖鸟", new BigDecimal("4500"), new BigDecimal("5400"), "GORE-TEX面料，轻量防水，专业户外，透气耐磨"),
                    new ProductTemplate("迪卡侬速干T恤", "迪卡侬", new BigDecimal("69"), new BigDecimal("99"), "速干面料，透气排汗，轻量舒适，多色可选")
            );
            // 运动户外-健身器材
            case "1702" -> Arrays.asList(
                    new ProductTemplate("Keep智能跑步机", "Keep", new BigDecimal("1999"), new BigDecimal("2499"), "可折叠设计，智能调速，静音减震，APP课程联动"),
                    new ProductTemplate("麦瑞克动感单车", "麦瑞克", new BigDecimal("1299"), new BigDecimal("1699"), "磁控阻力，静音飞轮，智能APP，燃脂课程"),
                    new ProductTemplate("迪卡侬椭圆机", "迪卡侬", new BigDecimal("2499"), new BigDecimal("2999"), "前置飞轮，16档阻力，心率监测，全家适用")
            );
            // 运动户外-户外装备
            case "1703" -> Arrays.asList(
                    new ProductTemplate("北面1996羽绒服", "The North Face", new BigDecimal("2998"), new BigDecimal("3998"), "700蓬鹅绒，DWR防泼水，经典版型，保暖时尚"),
                    new ProductTemplate("牧高笛帐篷", "牧高笛", new BigDecimal("599"), new BigDecimal("799"), "3-4人帐篷，双层防雨，速开设计，户外露营"),
                    new ProductTemplate("Osprey小鹰背包 38L", "Osprey", new BigDecimal("1299"), new BigDecimal("1599"), "AirScape背板，登山徒步，水袋兼容，轻量耐用")
            );
            // 运动户外-球类运动
            case "1704" -> Arrays.asList(
                    new ProductTemplate("威尔胜NBA篮球", "Wilson", new BigDecimal("399"), new BigDecimal("499"), "官方比赛用球，超纤皮料，手感极佳，室内外用"),
                    new ProductTemplate("尤尼克斯羽毛球拍", "YONEX", new BigDecimal("899"), new BigDecimal("1099"), "全碳素超轻，攻守兼备，专业级，穿线服务"),
                    new ProductTemplate("红双喜乒乓球拍", "红双喜", new BigDecimal("199"), new BigDecimal("269"), "七层纯木，狂飙胶皮，专业弧圈，进攻型")
            );
            // 运动户外-骑行装备
            case "1705" -> Arrays.asList(
                    new ProductTemplate("捷安特ATX 860山地车", "捷安特", new BigDecimal("2998"), new BigDecimal("3598"), "ALUXX铝合金车架，27.5寸轮径，30速变速，油压碟刹"),
                    new ProductTemplate("PMT MIPS头盔", "PMT", new BigDecimal("299"), new BigDecimal("399"), "MIPS防护系统，EPS缓冲层，通风散热，轻量化"),
                    new ProductTemplate("迈金C406码表", "迈金", new BigDecimal("299"), new BigDecimal("399"), "GPS定位，彩色屏幕，ANT+协议，轨迹记录")
            );
            // 图书音像-文学小说
            case "1801" -> Arrays.asList(
                    new ProductTemplate("活着（余华著）", "新经典", new BigDecimal("45"), new BigDecimal("68"), "余华代表作，人生苦难与坚韧，精装典藏版，豆瓣9.4分"),
                    new ProductTemplate("三体全集", "重庆出版社", new BigDecimal("98"), new BigDecimal("168"), "刘慈欣科幻巨作，雨果奖获奖作品，硬科幻巅峰"),
                    new ProductTemplate("百年孤独", "南海出版", new BigDecimal("55"), new BigDecimal("78"), "马尔克斯代表作，魔幻现实主义经典，范晔译本")
            );
            // 图书音像-科技计算机
            case "1802" -> Arrays.asList(
                    new ProductTemplate("深入理解计算机系统", "机械工业", new BigDecimal("139"), new BigDecimal("189"), "CSAPP经典教材，程序员必读，深入底层原理"),
                    new ProductTemplate("算法导论", "机械工业", new BigDecimal("128"), new BigDecimal("168"), "算法圣经，MIT经典教材，全面系统，程序员必备"),
                    new ProductTemplate("Java核心技术卷I", "机械工业", new BigDecimal("99"), new BigDecimal("139"), "Java学习经典，全面深入，最新版涵盖Java 17")
            );
            // 图书音像-教育考试
            case "1803" -> Arrays.asList(
                    new ProductTemplate("考研英语红宝书", "西北大学", new BigDecimal("59"), new BigDecimal("89"), "考研英语词汇，5500词，乱序版，配套APP"),
                    new ProductTemplate("肖秀荣考研政治", "国家开放大学", new BigDecimal("78"), new BigDecimal("108"), "考研政治必备，肖四肖八，精讲精练，押题神器"),
                    new ProductTemplate("五年高考三年模拟", "首都师大", new BigDecimal("68"), new BigDecimal("98"), "高考复习经典，历年真题，模拟训练，全科覆盖")
            );
            // 图书音像-经管励志
            case "1804" -> Arrays.asList(
                    new ProductTemplate("置身事内", "上海人民", new BigDecimal("65"), new BigDecimal("88"), "兰小欢著，中国政府与经济发展，经济学通俗读物"),
                    new ProductTemplate("原则", "中信出版", new BigDecimal("98"), new BigDecimal("128"), "瑞·达利欧著，生活与工作原则，桥水基金创始人"),
                    new ProductTemplate("被讨厌的勇气", "机械工业", new BigDecimal("45"), new BigDecimal("68"), "岸见一郎著，阿德勒心理学，自我启发，畅销百万")
            );
            // 图书音像-儿童读物
            case "1805" -> Arrays.asList(
                    new ProductTemplate("神奇校车全套", "贵州人民", new BigDecimal("299"), new BigDecimal("399"), "科普绘本经典，12册套装，趣味科学，3-10岁"),
                    new ProductTemplate("小王子", "天津人民", new BigDecimal("35"), new BigDecimal("48"), "圣埃克苏佩里著，经典童话，全彩插图，温暖治愈"),
                    new ProductTemplate("DK博物大百科", "科学普及", new BigDecimal("358"), new BigDecimal("458"), "自然视觉盛宴，5000+物种，高清图片，家庭藏书")
            );
            // 母婴用品-奶粉
            case "1901" -> Arrays.asList(
                    new ProductTemplate("爱他美卓萃3段", "爱他美", new BigDecimal("320"), new BigDecimal("398"), "欧洲奶源，模拟母乳低聚糖，益生元组合，900g"),
                    new ProductTemplate("飞鹤星飞帆3段", "飞鹤", new BigDecimal("268"), new BigDecimal("338"), "新鲜生牛乳，OPO结构脂，更适合中国宝宝"),
                    new ProductTemplate("惠氏启赋3段", "惠氏", new BigDecimal("358"), new BigDecimal("428"), "爱尔兰奶源，OPO结构脂，gsMO活性因子")
            );
            // 母婴用品-尿裤
            case "1902" -> Arrays.asList(
                    new ProductTemplate("帮宝适一级帮", "帮宝适", new BigDecimal("139"), new BigDecimal("189"), "日本进口，空气通道，超薄透气，L码48片"),
                    new ProductTemplate("花王妙而舒", "花王", new BigDecimal("89"), new BigDecimal("129"), "日本进口，3层透气，柔软亲肤，L码54片"),
                    new ProductTemplate("babycare皇室弱酸", "babycare", new BigDecimal("118"), new BigDecimal("158"), "弱酸亲肤，3D丝柔，大吸量，L码40片")
            );
            // 母婴用品-婴儿服饰
            case "1903" -> Arrays.asList(
                    new ProductTemplate("英氏婴儿连体衣", "英氏", new BigDecimal("129"), new BigDecimal("169"), "A类纯棉，无骨缝制，按扣设计，0-12月"),
                    new ProductTemplate("巴拉巴拉羽绒服", "巴拉巴拉", new BigDecimal("299"), new BigDecimal("399"), "90白鸭绒，A类标准，连帽设计，1-6岁"),
                    new ProductTemplate("全棉时代纱布浴巾", "全棉时代", new BigDecimal("89"), new BigDecimal("119"), "6层纱布，100%棉，柔软吸水，95*95cm")
            );
            // 母婴用品-玩具
            case "1904" -> Arrays.asList(
                    new ProductTemplate("乐高得宝系列", "乐高", new BigDecimal("299"), new BigDecimal("399"), "大颗粒积木，防吞咽设计，1.5-5岁，益智启蒙"),
                    new ProductTemplate("费雪安抚海马", "费雪", new BigDecimal("89"), new BigDecimal("129"), "安抚哄睡，柔和灯光，8首摇篮曲， plush材质"),
                    new ProductTemplate("伟易达学习桌", "伟易达", new BigDecimal("399"), new BigDecimal("499"), "双语学习，多点触控，4合1功能，1-3岁")
            );
            // 母婴用品-孕产
            case "1905" -> Arrays.asList(
                    new ProductTemplate("美德乐电动吸奶器", "美德乐", new BigDecimal("1299"), new BigDecimal("1699"), "双韵律技术，静音设计，9档吸力，便携充电"),
                    new ProductTemplate("婧麒防辐射服", "婧麒", new BigDecimal("299"), new BigDecimal("399"), "银纤维面料，360°防护，透气舒适，孕期必备"),
                    new ProductTemplate("贝亲奶瓶套装", "贝亲", new BigDecimal("189"), new BigDecimal("249"), "玻璃材质，自然实感奶嘴，防胀气，新生儿套装")
            );
            // 汽车用品-车载电器
            case "2001" -> Arrays.asList(
                    new ProductTemplate("70迈行车记录仪", "70迈", new BigDecimal("399"), new BigDecimal("499"), "4K超清，ADAS辅助，语音控制，停车监控"),
                    new ProductTemplate("小米车载充电器", "小米", new BigDecimal("69"), new BigDecimal("99"), "100W快充，双口输出，智能温控，LED显示"),
                    new ProductTemplate("纽曼车载空气净化器", "纽曼", new BigDecimal("299"), new BigDecimal("399"), "HEPA滤网，负离子净化，除醛除味，静音运行")
            );
            // 汽车用品-装饰
            case "2002" -> Arrays.asList(
                    new ProductTemplate("五福金牛汽车脚垫", "五福金牛", new BigDecimal("399"), new BigDecimal("499"), "全包围设计，环保材质，专车定制，耐磨防水"),
                    new ProductTemplate("牧宝汽车坐垫", "牧宝", new BigDecimal("299"), new BigDecimal("399"), "四季通用，透气面料，记忆海绵，舒适减压"),
                    new ProductTemplate("3M汽车贴膜", "3M", new BigDecimal("1280"), new BigDecimal("1680"), "隔热防晒，防爆安全，高透光率，全国包施工")
            );
            // 汽车用品-维修保养
            case "2003" -> Arrays.asList(
                    new ProductTemplate("美孚1号全合成机油", "美孚", new BigDecimal("399"), new BigDecimal("499"), "0W-40，金装，4L装，长效保护，全合成配方"),
                    new ProductTemplate("博世雨刮器", "博世", new BigDecimal("89"), new BigDecimal("129"), "无骨设计，天然橡胶，静音刮拭，专车适配"),
                    new ProductTemplate("3M燃油添加剂", "3M", new BigDecimal("49"), new BigDecimal("69"), "清洗积碳，提升动力，节省燃油，全车型适用")
            );
            // 汽车用品-轮胎
            case "2004" -> Arrays.asList(
                    new ProductTemplate("米其林浩悦4轮胎", "米其林", new BigDecimal("599"), new BigDecimal("799"), "静音舒适，湿地抓地，耐磨配方，205/55R16"),
                    new ProductTemplate("马牌UC7轮胎", "马牌", new BigDecimal("549"), new BigDecimal("699"), "静音技术，钻石切割花纹，湿滑路面安全"),
                    new ProductTemplate("倍耐力P7轮胎", "倍耐力", new BigDecimal("529"), new BigDecimal("669"), "运动操控，低滚阻，防爆技术，高性能轿车")
            );
            // 汽车用品-美容清洗
            case "2005" -> Arrays.asList(
                    new ProductTemplate("龟牌樱桃爽洗车液", "龟牌", new BigDecimal("39"), new BigDecimal("59"), "高泡沫，上光保护，中性配方，2L大容量"),
                    new ProductTemplate("3M车蜡", "3M", new BigDecimal("89"), new BigDecimal("129"), "棕榈蜡配方，深度上光，持久保护，易施工"),
                    new ProductTemplate("小米无线洗车机", "小米", new BigDecimal("399"), new BigDecimal("499"), "锂电池供电，2.4MPa水压，多种喷头，便携设计")
            );

            default -> new ArrayList<>();
        };
    }

    /**
     * 生成用户数据
     */
    private List<User> generateUsers(int count) {
        List<User> users = new ArrayList<>();
        String[] userNames = {"小明", "小红", "张三", "李四", "王五", "赵六", "刘七", "陈八", "杨九", "周十"};
        String[] domains = {"qq.com", "163.com", "gmail.com", "outlook.com", "126.com"};

        for (int i = 1; i <= count; i++) {
            String userId = String.format("U%03d", i);
            String username = userNames[random.nextInt(userNames.length)] + i;
            String email = username + "@" + domains[random.nextInt(domains.length)];
            String phone = "138" + String.format("%08d", random.nextInt(100000000));

            User user = User.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .phone(phone)
                    .registerTime(LocalDateTime.now().minusDays(random.nextInt(365)))
                    .build();
            users.add(user);
        }

        userService.saveBatch(users);

        // 生成用户画像和实时特征
        for (User user : users) {
            userProfileService.save(UserProfile.builder()
                    .userId(user.getUserId())
                    .build());
            userRealtimeFeaturesService.save(UserRealtimeFeatures.builder()
                    .userId(user.getUserId())
                    .build());
        }

        return users;
    }

    /**
     * 商品模板
     */
    private record ProductTemplate(String name, String brand, BigDecimal price,
                                   BigDecimal originalPrice, String description) {
    }
}
