package cn.eldercare.demo;

import cn.eldercare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DemoSeederIdempotencyTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DemoSeedRegistry registry;
    @Autowired
    private PasswordEncoder encoder;

    @Test
    void seedingTwiceKeepsDataStable() {
        DemoDataSeeder seeder = new DemoDataSeeder(jdbc, registry, encoder, true, false, "123456");
        DefaultApplicationArguments noArgs = new DefaultApplicationArguments(new String[0]);

        seeder.run(noArgs);
        int accounts = count("SELECT COUNT(*) FROM account");
        int orders = count("SELECT COUNT(*) FROM service_order");
        int notifications = count("SELECT COUNT(*) FROM notification");
        int settlements = count("SELECT COUNT(*) FROM settlement");

        assertTrue(orders >= 15, "演示订单应有 15 张，实际 " + orders);
        assertTrue(accounts >= 7, "至少应有 1 个测试管理员与 6 个演示账号，实际 " + accounts);
        assertEquals(15, count("SELECT COUNT(*) FROM demo_seed_record WHERE entity_type='ORDER'"));

        seeder.run(noArgs);

        assertEquals(accounts, count("SELECT COUNT(*) FROM account"), "重复执行不应新增账号");
        assertEquals(orders, count("SELECT COUNT(*) FROM service_order"), "重复执行不应新增订单");
        assertEquals(notifications, count("SELECT COUNT(*) FROM notification"), "重复执行不应新增通知");
        assertEquals(settlements, count("SELECT COUNT(*) FROM settlement"), "重复执行不应新增结算");
    }

    @Test
    void invalidDataIsNotWrittenWhenDisabled() {
        DemoDataSeeder disabled = new DemoDataSeeder(jdbc, registry, encoder, false, false, "123456");
        int before = count("SELECT COUNT(*) FROM service_order");
        disabled.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(before, count("SELECT COUNT(*) FROM service_order"));
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
