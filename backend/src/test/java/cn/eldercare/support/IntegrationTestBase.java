package cn.eldercare.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * 集成测试基类：在 Spring 上下文启动前重建独立测试库 eldercare_test 并执行全部迁移。
 * 安全约束：
 * 1) 只操作 eldercare_test，库名在此硬编码，禁止从外部配置覆盖；
 * 2) 数据库口令从项目根 .env 读取后以系统属性注入，不写入测试配置；
 * 3) 3306 不可用或 .env 缺失时直接失败并给出可读原因，不静默跳过。
 */
public abstract class IntegrationTestBase {
    protected static final String TEST_DATABASE = "eldercare_test";
    private static final String SERVER_URL =
            "jdbc:mysql://127.0.0.1:3306/?allowMultiQueries=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final List<String> MIGRATIONS = List.of(
            "V000__migration.sql",
            "V001__identity.sql",
            "V002__catalog_and_orders.sql",
            "V003__settlement.sql",
            "V004__operations.sql",
            "V005__notifications.sql",
            "V006__recommendation_tags.sql",
            "V007__family_coordination.sql",
            "V008__username_login.sql",
            "V009__demo_seed.sql"
    );

    static {
        prepareDatabase();
    }

    private static void prepareDatabase() {
        String password = readDatabasePassword();
        System.setProperty("DB_PASSWORD", password);
        try (Connection connection = DriverManager.getConnection(SERVER_URL, "root", password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DATABASE);
            statement.execute("CREATE DATABASE " + TEST_DATABASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        } catch (Exception error) {
            throw new IllegalStateException("无法准备测试库 " + TEST_DATABASE
                    + "：请确认 127.0.0.1:3306 可用且项目根 .env 的 DB_PASSWORD 正确", error);
        }
        String databaseUrl = SERVER_URL.replace("/?", "/" + TEST_DATABASE + "?");
        for (String migration : MIGRATIONS) {
            Path script = Paths.get("..", "sql", "main", migration);
            try (Connection connection = DriverManager.getConnection(databaseUrl, "root", password);
                 Statement statement = connection.createStatement()) {
                statement.execute(Files.readString(script, StandardCharsets.UTF_8));
            } catch (Exception error) {
                throw new IllegalStateException("执行迁移失败：" + migration, error);
            }
        }
    }

    private static String readDatabasePassword() {
        Path envFile = Paths.get("..", ".env");
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("DB_PASSWORD=")) {
                    return trimmed.substring("DB_PASSWORD=".length()).trim();
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("读取 " + envFile.toAbsolutePath() + " 失败，集成测试需要本地 MySQL 口令", error);
        }
        throw new IllegalStateException("项目根 .env 中缺少 DB_PASSWORD，集成测试无法连接本地 MySQL");
    }
}
