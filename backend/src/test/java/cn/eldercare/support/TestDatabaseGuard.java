package cn.eldercare.support;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates destructive integration-test targets before any schema is cleared. */
final class TestDatabaseGuard {
    private static final Set<String> ALLOWED_DATABASES = Set.of("eldercare_test", "eldercare_payment_test");
    private static final Pattern MYSQL_DATABASE = Pattern.compile("^jdbc:mysql://127\\.0\\.0\\.1(?::\\d+)?/([^?;]+)(?:[?;].*)?$");

    private TestDatabaseGuard() { }

    static String requireAllowedTarget(String jdbcUrl, String expectedDatabase) {
        Matcher matcher = MYSQL_DATABASE.matcher(jdbcUrl == null ? "" : jdbcUrl.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("测试 JDBC URL 必须明确指定受保护的测试库。");
        }
        String database = matcher.group(1);
        if (!database.endsWith("_test") || !ALLOWED_DATABASES.contains(database)
                || !database.equals(expectedDatabase)) {
            throw new IllegalStateException("拒绝清理非允许的测试库：" + database);
        }
        return database;
    }
}
