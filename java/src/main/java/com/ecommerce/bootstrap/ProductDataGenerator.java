package com.ecommerce.bootstrap;

import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.service.CategoryService;
import com.ecommerce.service.ProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品数据生成器
 * 用于初始化时生成丰富的商品模拟数据
 */
@Slf4j
@Component
public class ProductDataGenerator {

    @Resource
    private ProductService productService;

    @Resource
    private CategoryService categoryService;

    @Value("${app.crawler.image-path:src/main/resources/img}")
    private String imagePath;

    /**
     * 商品类别配置：类目 -> 品牌列表
     */
    private static final Map<String, List<String>> CATEGORY_CONFIG = Map.of(
            "手机", List.of("Apple", "华为", "小米", "OPPO", "vivo", "荣耀", "三星", "realme", "一加", "魅族"),
            "笔记本", List.of("联想", "戴尔", "华硕", "惠普", "Apple", "华为", "小米", "机械革命", "神舟", "宏碁"),
            "耳机", List.of("Apple", "Sony", "Bose", "森海塞尔", "华为", "小米", "OPPO", "漫步者", "JBL", "Beats"),
            "平板", List.of("Apple", "华为", "小米", "三星", "联想", "OPPO", "vivo", "荣耀", "微软", "亚马逊"),
            "显示器", List.of("戴尔", "华硕", "LG", "三星", "AOC", "飞利浦", "明基", "惠普", "宏碁", "小米"),
            "配件", List.of("Anker", "贝尔金", "绿联", "倍思", "小米", "华为", "OPPO", "vivo", "品胜", "罗马仕")
    );

    /**
     * 商品名称模板
     */
    private static final Map<String, List<String>> PRODUCT_TEMPLATES = Map.of(
            "手机", List.of(
                    "%s %s %dGB+%dGB版",
                    "%s %s 旗舰芯片 %d万像素",
                    "%s %s %dHz高刷屏",
                    "%s %s 5G智能手机"
            ),
            "笔记本", List.of(
                    "%s %s i%d代 RTX%d显卡",
                    "%s %s %dGB内存 %dGB固态",
                    "%s %s %.1f英寸高性能本",
                    "%s %s 轻薄商务本"
            ),
            "耳机", List.of(
                    "%s %s 主动降噪耳机",
                    "%s %s 真无线蓝牙耳机",
                    "%s %s Hi-Fi耳机",
                    "%s %s 运动防水耳机"
            ),
            "平板", List.of(
                    "%s %s %.1f英寸平板 %dGB",
                    "%s %s 学习办公平板",
                    "%s %s %dK高清屏幕",
                    "%s %s 高性能平板电脑"
            ),
            "显示器", List.of(
                    "%s %d英寸 %dK专业显示器",
                    "%s %s %.1f英寸 Type-C",
                    "%s 电竞显示器 %dHz高刷",
                    "%s 专业设计显示器"
            ),
            "配件", List.of(
                    "%s %dW氮化镓快充",
                    "%s 快充数据线 %.1f米",
                    "%s 无线充电器 %dW",
                    "%s 移动电源 %dmAh"
            )
    );

    /**
     * 生成商品数据并保存
     *
     * @param countPerCategory 每个类目生成的商品数量
     * @return 生成的商品总数
     */
    public int generateProducts(int countPerCategory) {
        log.info("ProductDataGenerator.generateProducts 开始生成商品数据, 每类{}件", countPerCategory);

        int totalGenerated = 0;
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // 确保图片目录存在
        ensureImageDirectory();

        for (Map.Entry<String, List<String>> entry : CATEGORY_CONFIG.entrySet()) {
            String categoryName = entry.getKey();
            List<String> brands = entry.getValue();

            // 获取或创建类目
            Category category = getOrCreateCategory(categoryName);
            if (category == null) {
                log.warn("ProductDataGenerator.generateProducts 类目创建失败: {}", categoryName);
                continue;
            }

            // 为每个品牌生成商品
            for (String brand : brands) {
                int productCount = ThreadLocalRandom.current().nextInt(countPerCategory / 2, countPerCategory + 1);
                for (int i = 0; i < productCount; i++) {
                    try {
                        Product product = generateProduct(category, brand, timestamp, i);
                        if (product != null) {
                            boolean saved = productService.save(product);
                            if (saved) {
                                totalGenerated++;
                            }
                        }
                    } catch (Exception e) {
                        log.error("ProductDataGenerator.generateProducts 生成商品失败", e);
                    }
                }
            }
        }

        log.info("ProductDataGenerator.generateProducts 完成, 共生成{}件商品", totalGenerated);
        return totalGenerated;
    }

    /**
     * 生成单个商品
     */
    private Product generateProduct(Category category, String brand, String timestamp, int index) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String categoryName = category.getCategoryName();
        List<String> templates = PRODUCT_TEMPLATES.getOrDefault(categoryName, List.of("%s %s 优质产品"));

        // 随机选择模板并生成商品名称
        String template = templates.get(random.nextInt(templates.size()));
        String modelName = generateModelName(brand, categoryName);
        String productName = formatProductName(template, brand, modelName, categoryName, random);

        // 生成商品ID
        String productId = "P" + timestamp + String.format("%04d", random.nextInt(10000));

        // 生成价格
        BigDecimal price = generatePrice(categoryName, brand, random);
        BigDecimal originalPrice = price.multiply(BigDecimal.valueOf(1.1 + random.nextDouble(0.2)));

        // 生成库存和销量
        int stock = random.nextInt(50, 1000);
        int salesCount = random.nextInt(100, 50000);

        // 生成评分
        BigDecimal rating = BigDecimal.valueOf(4.0 + random.nextDouble(1.0));

        // 生成图片URL
        String mainImage = generateProductImage(productId, brand, categoryName);

        // 生成商品描述
        String description = generateDescription(brand, categoryName, modelName);

        return Product.builder()
                .productId(productId)
                .productName(productName)
                .categoryId(category.getCategoryId())
                .categoryName(categoryName)
                .brand(brand)
                .price(price)
                .originalPrice(originalPrice)
                .productDescription(description)
                .mainImage(mainImage)
                .images(mainImage)
                .stock(stock)
                .salesCount(salesCount)
                .rating(rating)
                .productStatus(1)
                .isDeleted(0)
                .build();
    }

    /**
     * 格式化商品名称
     * 根据不同类目的模板特点进行格式化
     */
    private String formatProductName(String template, String brand, String modelName, String categoryName, ThreadLocalRandom random) {
        try {
            // 根据模板中的格式符数量动态生成参数
            int formatCount = countFormatSpecifiers(template);
            
            Object[] args = new Object[formatCount];
            args[0] = brand;
            
            if (formatCount >= 2) {
                args[1] = modelName;
            }
            
            // 根据类目和剩余格式符填充参数
            for (int i = 2; i < formatCount; i++) {
                args[i] = generateArgForCategory(categoryName, i, random);
            }
            
            return String.format(template, args);
        } catch (Exception e) {
            // 格式化失败时返回简单名称
            log.error("ProductDataGenerator.formatProductName 格式化失败, template={}", template, e);
            return brand + " " + modelName + " " + categoryName;
        }
    }
    
    /**
     * 统计模板中的格式符数量
     */
    private int countFormatSpecifiers(String template) {
        int count = 0;
        for (int i = 0; i < template.length() - 1; i++) {
            if (template.charAt(i) == '%') {
                char next = template.charAt(i + 1);
                if (next == 's' || next == 'd' || next == 'f') {
                    count++;
                } else if (next == '.') {
                    // 处理 %.1f 这类格式
                    for (int j = i + 2; j < template.length(); j++) {
                        if (template.charAt(j) == 'f') {
                            count++;
                            break;
                        }
                    }
                }
            }
        }
        return count;
    }
    
    /**
     * 根据类目和参数位置生成对应的参数值
     */
    private Object generateArgForCategory(String categoryName, int argIndex, ThreadLocalRandom random) {
        switch (categoryName) {
            case "手机":
                if (argIndex == 2) {
                    return random.nextInt(64, 512);  // 内存
                } else if (argIndex == 3) {
                    return random.nextInt(128, 1024);  // 存储
                } else {
                    return random.nextInt(4000, 10000);  // 像素
                }
            case "笔记本":
                if (argIndex == 2) {
                    return random.nextInt(5, 14);  // i5-i13代
                } else if (argIndex == 3) {
                    return random.nextInt(3050, 4090);  // 显卡型号
                } else {
                    return random.nextDouble(13.0, 17.0);  // 屏幕尺寸
                }
            case "耳机":
                return random.nextInt(20, 100);  // 续航小时或价格范围
            case "平板":
                if (argIndex == 2) {
                    return random.nextDouble(8.0, 12.9);  // 屏幕尺寸
                } else {
                    return random.nextInt(64, 512);  // 存储
                }
            case "显示器":
                if (argIndex == 2) {
                    return random.nextInt(24, 34);  // 英寸
                } else {
                    return random.nextInt(2, 5);  // 2K-4K
                }
            case "配件":
                if (argIndex == 2) {
                    return random.nextInt(20, 240);  // 功率
                } else {
                    return random.nextInt(5000, 30000);  // 容量
                }
            default:
                return random.nextInt(1, 100);
        }
    }

    /**
     * 生成型号名称
     */
    private String generateModelName(String brand, String category) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String[] suffixes = {"Pro", "Max", "Plus", "Ultra", "Lite", "SE", "Elite", "Air", "S", ""};

        if (brand.equals("Apple")) {
            int num = random.nextInt(12, 18);
            String suffix = suffixes[random.nextInt(suffixes.length)];
            return num + " " + suffix;
        } else if (brand.equals("华为")) {
            int num = random.nextInt(50, 80);
            String suffix = suffixes[random.nextInt(suffixes.length)];
            return "Mate " + num + " " + suffix;
        } else if (brand.equals("小米")) {
            int num = random.nextInt(10, 16);
            String suffix = suffixes[random.nextInt(suffixes.length)];
            return num + " " + suffix;
        } else {
            int num = random.nextInt(1, 100);
            String suffix = suffixes[random.nextInt(suffixes.length)];
            return num + " " + suffix;
        }
    }

    /**
     * 生成价格
     */
    private BigDecimal generatePrice(String category, String brand, ThreadLocalRandom random) {
        // 基础价格范围
        Map<String, double[]> priceRanges = Map.of(
                "手机", new double[]{999, 9999},
                "笔记本", new double[]{2999, 19999},
                "耳机", new double[]{99, 2999},
                "平板", new double[]{999, 8999},
                "显示器", new double[]{699, 9999},
                "配件", new double[]{19, 999}
        );

        double[] range = priceRanges.getOrDefault(category, new double[]{100, 1000});
        double basePrice = random.nextDouble(range[0], range[1]);

        // 品牌溢价
        if (List.of("Apple", "华为", "Sony", "Bose").contains(brand)) {
            basePrice *= 1.3;
        } else if (List.of("小米", "OPPO", "vivo").contains(brand)) {
            basePrice *= 0.9;
        }

        return BigDecimal.valueOf(Math.round(basePrice));
    }

    /**
     * 商品图片配置：类目 -> 图片URL列表
     * 使用公开免费图片网站的静态图片链接
     */
    private static final Map<String, List<String>> PRODUCT_IMAGES = Map.of(
            "手机", List.of(
                    "https://cdn.pixabay.com/photo/2016/11/20/09/06/mobile-phone-1842190_400.jpg",
                    "https://cdn.pixabay.com/photo/2017/10/06/10/44/mobile-phone-2823051_400.jpg",
                    "https://cdn.pixabay.com/photo/2016/12/09/11/33/smartphone-1894723_400.jpg",
                    "https://cdn.pixabay.com/photo/2014/08/05/10/30/iphone-410324_400.jpg"
            ),
            "笔记本", List.of(
                    "https://cdn.pixabay.com/photo/2016/11/19/15/32/laptop-1839876_400.jpg",
                    "https://cdn.pixabay.com/photo/2014/05/02/21/50/homeoffice-336378_400.jpg",
                    "https://cdn.pixabay.com/photo/2015/01/09/11/11/office-594132_400.jpg",
                    "https://cdn.pixabay.com/photo/2018/05/16/18/04/laptop-3406462_400.jpg"
            ),
            "耳机", List.of(
                    "https://cdn.pixabay.com/photo/2015/12/11/14/34/headphones-1088158_400.jpg",
                    "https://cdn.pixabay.com/photo/2018/04/26/17/52/headphones-3352201_400.jpg",
                    "https://cdn.pixabay.com/photo/2016/11/29/13/04/headphones-1869822_400.jpg",
                    "https://cdn.pixabay.com/photo/2017/10/26/19/54/headphones-2892750_400.jpg"
            ),
            "平板", List.of(
                    "https://cdn.pixabay.com/photo/2015/06/05/17/54/ipad-799310_400.jpg",
                    "https://cdn.pixabay.com/photo/2016/03/27/19/45/tablet-1283968_400.jpg",
                    "https://cdn.pixabay.com/photo/2014/08/05/10/29/ipad-410314_400.jpg",
                    "https://cdn.pixabay.com/photo/2016/11/29/13/48/tablet-1869840_400.jpg"
            ),
            "显示器", List.of(
                    "https://cdn.pixabay.com/photo/2015/01/21/09/31/monitor-606102_400.jpg",
                    "https://cdn.pixabay.com/photo/2018/05/16/18/08/computer-3406500_400.jpg",
                    "https://cdn.pixabay.com/photo/2017/02/08/14/25/computer-2048719_400.jpg",
                    "https://cdn.pixabay.com/photo/2012/11/07/16/37/monitor-65272_400.jpg"
            ),
            "配件", List.of(
                    "https://cdn.pixabay.com/photo/2017/01/28/15/21/charger-2016333_400.jpg",
                    "https://cdn.pixabay.com/photo/2015/06/25/16/54/usb-820739_400.jpg",
                    "https://cdn.pixabay.com/photo/2016/02/05/17/06/power-bank-1178591_400.jpg",
                    "https://cdn.pixabay.com/photo/2015/05/31/12/13/usb-791044_400.jpg"
            )
    );

    /**
     * 生成商品图片URL
     * 根据商品类目返回对应类型的占位图片
     */
    private String generateProductImage(String productId, String brand, String category) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        List<String> images = PRODUCT_IMAGES.get(category);
        if (images == null || images.isEmpty()) {
            // 默认使用电子产品图片
            images = PRODUCT_IMAGES.get("手机");
        }
        
        // 随机选择一张该类目的图片
        return images.get(random.nextInt(images.size()));
    }

    /**
     * 生成商品描述
     */
    private String generateDescription(String brand, String category, String model) {
        String[] features = {
                "全新升级，性能强劲",
                "轻薄便携，续航持久",
                "高清屏幕，护眼认证",
                "快速充电，安全可靠",
                "智能互联，操作便捷",
                "品质保证，全国联保"
        };

        StringBuilder sb = new StringBuilder();
        sb.append(brand).append(" ").append(model).append(" ");
        sb.append(category).append("，");

        // 随机添加特性
        int featureCount = ThreadLocalRandom.current().nextInt(2, 4);
        Set<Integer> usedIndices = new HashSet<>();
        for (int i = 0; i < featureCount; i++) {
            int idx;
            do {
                idx = ThreadLocalRandom.current().nextInt(features.length);
            } while (usedIndices.contains(idx));
            usedIndices.add(idx);

            sb.append(features[idx]);
            if (i < featureCount - 1) {
                sb.append("，");
            }
        }

        sb.append("。");
        return sb.toString();
    }

    /**
     * 获取或创建类目
     */
    private Category getOrCreateCategory(String categoryName) {
        // 尝试查找现有类目
        List<Category> categories = categoryService.listByLevel(2, 0);
        for (Category cat : categories) {
            if (categoryName.equals(cat.getCategoryName())) {
                return cat;
            }
        }

        // 创建新类目
        String categoryId = "C" + System.currentTimeMillis();
        Category category = new Category();
        category.setCategoryId(categoryId);
        category.setCategoryName(categoryName);
        category.setParentId("1001"); // 默认父类目
        category.setCategoryLevel(2);
        category.setIsDeleted(0);

        boolean saved = categoryService.save(category);
        if (saved) {
            log.info("ProductDataGenerator.getOrCreateCategory 创建类目: {}", categoryName);
            return category;
        }

        return null;
    }

    /**
     * 更新所有商品的图片URL
     * 根据商品类目设置对应类型的图片
     * 
     * @return 更新的商品数量
     */
    public int updateAllProductImages() {
        log.info("ProductDataGenerator.updateAllProductImages 开始更新商品图片");

        int updatedCount = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 获取所有商品
        List<Product> products = productService.list();
        if (products == null || products.isEmpty()) {
            log.warn("ProductDataGenerator.updateAllProductImages 没有找到商品");
            return 0;
        }

        for (Product product : products) {
            try {
                String category = product.getCategoryName();
                List<String> images = PRODUCT_IMAGES.get(category);
                
                if (images == null || images.isEmpty()) {
                    images = PRODUCT_IMAGES.get("手机"); // 默认使用手机图片
                }

                String imageUrl = images.get(random.nextInt(images.size()));
                
                product.setMainImage(imageUrl);
                product.setImages(imageUrl);
                
                boolean updated = productService.updateById(product);
                if (updated) {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.warn("ProductDataGenerator.updateAllProductImages 更新商品图片失败, productId={}", 
                        product.getProductId(), e);
            }
        }

        log.info("ProductDataGenerator.updateAllProductImages 完成, 共更新{}件商品图片", updatedCount);
        return updatedCount;
    }

    /**
     * 确保图片目录存在
     */
    private void ensureImageDirectory() {
        try {
            Path path = Paths.get(imagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("ProductDataGenerator.ensureImageDirectory 创建图片目录: {}", path);
            }
        } catch (Exception e) {
            log.error("ProductDataGenerator.ensureImageDirectory 创建目录失败: {}", e.getMessage());
        }
    }
}
