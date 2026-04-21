package com.ecommerce.bootstrap;

import com.ecommerce.service.DocumentVectorService;
import com.ecommerce.service.UserService;
import com.ecommerce.service.VectorSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 系统启动初始化器
 * 负责数据库初始化、模拟数据生成、向量库同步
 */
@Slf4j
@Component
public class SystemBootstrap implements CommandLineRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private UserService userService;

    @Resource
    private MockDataGenerator mockDataGenerator;

    @Resource
    private VectorSyncService vectorSyncService;

    @Resource
    private DocumentVectorService documentVectorService;

    @Override
    public void run(String... args) throws Exception {
        log.info("SystemBootstrap.run 系统启动初始化开始");

        // 1. 初始化 MySQL 数据库（表结构 + 基础数据）
        boolean mysqlInitialized = initMySQL();

        // 2. 如果 MySQL 刚初始化，生成丰富的模拟数据
        if (mysqlInitialized) {
            mockDataGenerator.generateAllMockData();
        }

        // 3. 同步商品数据到向量数据库（结构化数据同步）
        syncProductsToVectorStore();

        // 4. 初始化 RAG 知识库文档（文档分块向量化）
        initKnowledgeBase();

        log.info("SystemBootstrap.run 系统启动初始化完成");
    }

    /**
     * 初始化 MySQL 数据库
     * @return true 表示本次进行了初始化（表刚创建）
     */
    private boolean initMySQL() {
        try {
            log.info("SystemBootstrap.initMySQL 开始初始化MySQL数据库");

            boolean needInit = checkNeedInitMySQL();

            if (needInit) {
                executeSqlScript("database/mysql/schema.sql");
                log.info("SystemBootstrap.initMySQL MySQL表结构初始化完成");

                executeSqlScript("database/mysql/init_data.sql");
                log.info("SystemBootstrap.initMySQL MySQL基础数据导入完成");

                return true;
            } else {
                log.info("SystemBootstrap.initMySQL MySQL数据库已初始化，跳过");
                return false;
            }
        } catch (Exception e) {
            log.error("SystemBootstrap.initMySQL MySQL初始化失败", e);
            return false;
        }
    }

    /**
     * 检查是否需要初始化 MySQL
     */
    private boolean checkNeedInitMySQL() {
        try {
            userService.count();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 执行 SQL 脚本
     */
    private void executeSqlScript(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("SystemBootstrap.executeSqlScript SQL脚本不存在: {}", resourcePath);
            return;
        }

        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        String[] statements = sql.split(";");
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("/*")) {
                try {
                    jdbcTemplate.execute(trimmed);
                } catch (Exception e) {
                    log.warn("SystemBootstrap.executeSqlScript 执行SQL失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 同步商品数据到向量数据库
     */
    private void syncProductsToVectorStore() {
        try {
            log.info("SystemBootstrap.syncProductsToVectorStore 开始同步商品数据到向量数据库");
            vectorSyncService.syncAllProducts();
            log.info("SystemBootstrap.syncProductsToVectorStore 商品数据同步完成");
        } catch (Exception e) {
            log.error("SystemBootstrap.syncProductsToVectorStore 同步商品数据到向量数据库失败: {}", e.getMessage());
            log.warn("SystemBootstrap.syncProductsToVectorStore 向量数据库可能不可用，跳过同步");
        }
    }

    /**
     * 初始化 RAG 知识库
     */
    private void initKnowledgeBase() {
        try {
            log.info("SystemBootstrap.initKnowledgeBase 开始初始化RAG知识库");

            documentVectorService.addTextToKnowledgeBase(
                    "退换货政策：\n" +
                    "1. 7天无理由退货：自签收之日起7天内，商品未使用、包装完好可申请无理由退货。\n" +
                    "2. 15天质量问题换货：自签收之日起15天内，如发现商品质量问题可申请换货。\n" +
                    "3. 退货运费：因商品质量问题退货运费由商家承担；无理由退货运费由消费者承担。\n" +
                    "4. 特殊商品：生鲜、内衣、定制类商品不支持无理由退货。",
                    "return_policy"
            );

            documentVectorService.addTextToKnowledgeBase(
                    "配送说明：\n" +
                    "1. 配送范围：全国配送（港澳台及海外除外）。\n" +
                    "2. 配送时效：一线城市1-2天，二三线城市2-4天，偏远地区5-7天。\n" +
                    "3. 运费标准：订单满99元免运费，不满99元收取6元运费。\n" +
                    "4. 配送方式：支持快递配送、自提点取货、同城配送。",
                    "shipping_policy"
            );

            documentVectorService.addTextToKnowledgeBase(
                    "会员权益：\n" +
                    "1. 普通会员：注册即成为普通会员，享受积分累计、生日优惠券。\n" +
                    "2. 银牌会员：累计消费满1000元，享受95折优惠、优先发货。\n" +
                    "3. 金牌会员：累计消费满5000元，享受9折优惠、专属客服、免费退换货。\n" +
                    "4. 钻石会员：累计消费满20000元，享受85折优惠、专属礼品、新品优先购买权。",
                    "member_benefits"
            );

            documentVectorService.addTextToKnowledgeBase(
                    "手机选购指南：\n" +
                    "1. 处理器：目前主流处理器包括苹果A18系列、高通骁龙8 Gen4、联发科天玑9400。处理器性能决定手机流畅度。\n" +
                    "2. 屏幕：关注分辨率、刷新率、亮度。2K分辨率+120Hz高刷是旗舰标配。\n" +
                    "3. 拍照：关注主摄像素、传感器尺寸、光学防抖。目前1英寸大底是顶级配置。\n" +
                    "4. 续航：关注电池容量和快充功率。5000mAh+100W快充是主流配置。\n" +
                    "5. 系统：iOS系统流畅稳定，Android系统开放自由，鸿蒙系统分布式体验好。",
                    "phone_buying_guide"
            );

            log.info("SystemBootstrap.initKnowledgeBase RAG知识库初始化完成");
        } catch (Exception e) {
            log.error("SystemBootstrap.initKnowledgeBase 初始化知识库失败: {}", e.getMessage());
            log.warn("SystemBootstrap.initKnowledgeBase 向量数据库可能不可用，跳过知识库初始化");
        }
    }
}
