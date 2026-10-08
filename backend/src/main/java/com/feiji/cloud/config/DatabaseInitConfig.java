package com.feiji.cloud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 启动时自动执行 db/schema.sql 建表（幂等）。
 * 数据库不可用时仅告警不阻断启动，登录等依赖库的功能届时不可用。
 */
@Configuration
public class DatabaseInitConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitConfig.class);

    @Bean
    @org.springframework.core.annotation.Order(0)
    public ApplicationRunner schemaInitializer(DataSource dataSource) {
        return (ApplicationArguments args) -> {
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
                // 已存在的旧库补列（CREATE TABLE IF NOT EXISTS 不会生效）
                ensureColumn(connection, "device", "vendor", "VARCHAR(20) DEFAULT 'DJI'");
                ensureColumn(connection, "wayline_job", "report_status", "VARCHAR(16) DEFAULT 'NONE'");
                ensureColumn(connection, "wayline_job", "report_no", "VARCHAR(64)");
                log.info("数据库表结构初始化完成");
            } catch (Exception e) {
                log.error("数据库初始化失败（依赖库的功能不可用）: {}", e.getMessage());
            }
        };
    }

    private void ensureColumn(Connection connection, String table, String column, String ddl) throws Exception {
        var rs = connection.getMetaData().getColumns(null, null, table, column);
        if (!rs.next()) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
            }
            log.info("已为表 {} 补充列 {}", table, column);
        }
    }
}
