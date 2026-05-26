package com.ecommerce.bootstrap;

import com.ecommerce.service.DocumentVectorService;
import com.ecommerce.service.UserService;
import com.ecommerce.service.VectorSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
        // 【注意】向量服务配置错误时不阻塞主流程启动
        try {
            syncProductsToVectorStore();
        } catch (Exception e) {
            log.error("SystemBootstrap.run 向量同步失败，继续启动: {}", e.getMessage());
        }

        // 4. 初始化 RAG 知识库文档（文档分块向量化）
        try {
            initKnowledgeBase();
        } catch (Exception e) {
            log.error("SystemBootstrap.run 知识库初始化失败，继续启动: {}", e.getMessage());
        }

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
     * 从 resources/knowledge/ 目录扫描加载知识文档
     * 目录名 → knowledge_type（大类），文件名（不含扩展名） → sub_type（子类）
     */
    private void initKnowledgeBase() {
        try {
            log.info("SystemBootstrap.initKnowledgeBase 开始从文件加载RAG知识库");

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:knowledge/**/*.txt");

            if (resources.length == 0) {
                log.warn("SystemBootstrap.initKnowledgeBase knowledge目录下未找到.txt知识文件，跳过初始化");
                return;
            }

            int loadedCount = 0;
            for (org.springframework.core.io.Resource resource : resources) {
                try {
                    String path = resource.getURI().toString();
                    // 从路径中提取: knowledge/{knowledge_type}/{sub_type}.txt
                    String relativePath = path.substring(path.indexOf("knowledge/"));
                    String[] parts = relativePath.replace("knowledge/", "").split("/");
                    if (parts.length < 2) {
                        log.warn("SystemBootstrap.initKnowledgeBase 跳过路径格式不正确的文件: {}", relativePath);
                        continue;
                    }
                    String knowledgeType = parts[0];           // 目录名 = 大类
                    String subType = parts[1].replace(".txt", ""); // 文件名 = 子类

                    String content = StreamUtils.copyToString(
                            resource.getInputStream(), StandardCharsets.UTF_8);

                    if (content.trim().isEmpty()) {
                        log.warn("SystemBootstrap.initKnowledgeBase 跳过空文件: {}", relativePath);
                        continue;
                    }

                    String source = knowledgeType + "_" + subType;
                    documentVectorService.addTextToKnowledgeBase(content, source, knowledgeType, subType);
                    loadedCount++;
                    log.info("SystemBootstrap.initKnowledgeBase 已加载: {} ({} chunks, {} chars)",
                            relativePath,
                            (content.length() / 500) + 1,
                            content.length());
                } catch (Exception e) {
                    log.error("SystemBootstrap.initKnowledgeBase 加载知识文件失败: {}", resource.getFilename(), e);
                }
            }

            log.info("SystemBootstrap.initKnowledgeBase RAG知识库初始化完成，共加载 {} 个知识文件", loadedCount);
        } catch (Exception e) {
            log.error("SystemBootstrap.initKnowledgeBase 初始化知识库失败: {}", e.getMessage());
            log.warn("SystemBootstrap.initKnowledgeBase 向量数据库可能不可用，跳过知识库初始化");
        }
    }
}
