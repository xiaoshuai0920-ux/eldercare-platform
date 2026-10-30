package cn.eldercare.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集成测试基类：只清理已存在的 eldercare_test schema，并在其中执行全部迁移。
 * 测试账号必须由 MySQL 管理员预先限制在 *_test 库；本代码不创建或删除数据库。
 */
public abstract class IntegrationTestBase {
    protected static final String TEST_DATABASE = "eldercare_test";
    private static final String EXPECTED_DATABASE = "eldercare_test";
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
            "V009__demo_seed.sql",
            "V010__audit_context.sql",
            "V011__order_status_index.sql"
    );

    static {
        prepareDatabase();
    }

    private static void prepareDatabase() {
        Map<String, String> settings = readTestSettings();
        String databaseUrl = settings.get("TEST_DB_URL");
        String databaseUser = settings.get("TEST_DB_USER");
        String password = settings.get("TEST_DB_PASSWORD");
        if (!databaseUser.matches("[A-Za-z0-9_]{1,32}") || databaseUser.equalsIgnoreCase("root")) {
            throw new IllegalStateException("TEST_DB_USER 必须是专用测试账号，不能使用 root。");
        }
        TestDatabaseGuard.requireAllowedTarget(databaseUrl, EXPECTED_DATABASE);
        System.setProperty("spring.datasource.url", databaseUrl);
        System.setProperty("spring.datasource.username", databaseUser);
        System.setProperty("spring.datasource.password", password);
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUser, password)) {
            if (!EXPECTED_DATABASE.equals(connection.getCatalog())) {
                throw new IllegalStateException("数据库连接实际目标与允许的测试库不一致。");
            }
            clearExistingTables(connection);
        } catch (Exception error) {
            throw new IllegalStateException("无法安全准备测试库 " + TEST_DATABASE
                    + "：确认该库已由管理员预建，且 .env 中 TEST_DB_USER 仅被授予测试库权限。", error);
        }
        for (String migration : MIGRATIONS) {
            Path script = Paths.get("..", "sql", "main", migration);
            try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUser, password);
                 Statement statement = connection.createStatement()) {
                statement.execute(Files.readString(script, StandardCharsets.UTF_8));
            } catch (Exception error) {
                throw new IllegalStateException("执行迁移失败：" + migration, error);
            }
        }
    }

    private static void clearExistingTables(Connection connection) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> names = new java.util.ArrayList<>();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) names.add(tables.getString("TABLE_NAME"));
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                for (String name : names) {
                    statement.execute("DROP TABLE `" + name.replace("`", "``") + "`");
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        }
    }

    private static Map<String, String> readTestSettings() {
        Path envFile = Paths.get("..", ".env");
        try {
            Map<String, String> settings = new LinkedHashMap<>();
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf('=');
                if (separator > 0) settings.put(trimmed.substring(0, separator), trimmed.substring(separator + 1).trim());
            }
            for (String key : List.of("TEST_DB_URL", "TEST_DB_USER", "TEST_DB_PASSWORD")) {
                if (settings.getOrDefault(key, "").isBlank()) throw new IllegalStateException("缺少配置项 " + key);
            }
            return settings;
        } catch (Exception error) {
            throw new IllegalStateException("读取 " + envFile.toAbsolutePath()
                    + " 失败；集成测试需要 TEST_DB_URL、TEST_DB_USER 和 TEST_DB_PASSWORD。", error);
        }
    }
}
