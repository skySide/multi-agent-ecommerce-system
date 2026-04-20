package com.ecommerce.bootstrap;

import com.ecommerce.service.MilvusService;
import com.ecommerce.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 系统启动初始化器
 * 负责数据库、向量数据库的初始化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemBootstrap implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final MilvusService milvusService;
    private final UserService userService;

    @Override
    public void run(String... args) throws Exception {
        log.info("========== 系统启动初始化开始 ==========");

        // 1. 初始化 MySQL 数据库
        initMySQL();

        // 2. 初始化 Milvus 向量数据库
        initMilvus();

        log.info("========== 系统启动初始化完成 ==========");
    }

    /**
     * 初始化 MySQL 数据库
     */
    private void initMySQL() {
        try {
            log.info("开始初始化 MySQL 数据库...");

            // 检查是否需要初始化（检查表是否存在）
            boolean needInit = checkNeedInitMySQL();

            if (needInit) {
                // 执行 schema.sql
                executeSqlScript("database/mysql/schema.sql");
                log.info("MySQL 表结构初始化完成");

                // 执行 init_data.sql
                executeSqlScript("database/mysql/init_data.sql");
                log.info("MySQL 初始数据导入完成");
            } else {
                log.info("MySQL 数据库已初始化，跳过");
            }
        } catch (Exception e) {
            log.error("MySQL 初始化失败", e);
        }
    }

    /**
     * 检查是否需要初始化 MySQL
     */
    private boolean checkNeedInitMySQL() {
        try {
            // 使用 UserService 检查 user 表是否有数据
            userService.count();
            return false;
        } catch (Exception e) {
            // 表不存在或无法访问，需要初始化
            return true;
        }
    }

    /**
     * 执行 SQL 脚本
     */
    private void executeSqlScript(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("SQL 脚本不存在: {}", resourcePath);
            return;
        }

        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

        // 按分号分割执行（简单处理，不考虑存储过程等复杂情况）
        String[] statements = sql.split(";");
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("/*")) {
                try {
                    jdbcTemplate.execute(trimmed);
                } catch (Exception e) {
                    log.warn("执行 SQL 失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 初始化 Milvus 向量数据库
     */
    private void initMilvus() {
        try {
            log.info("开始初始化 Milvus 向量数据库...");

            // 初始化商品向量 Collection
            milvusService.initProductCollection();

            // 初始化用户向量 Collection
            milvusService.initUserCollection();

            log.info("Milvus 向量数据库初始化完成");
        } catch (Exception e) {
            log.error("Milvus 初始化失败", e);
        }
    }
}
