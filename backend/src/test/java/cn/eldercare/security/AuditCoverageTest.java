package cn.eldercare.security;

import java.util.List;
import java.util.Map;

import cn.eldercare.common.AuditService;
import cn.eldercare.support.ApiClient;
import cn.eldercare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计覆盖：登录与管理动必须留痕，且失败场景要能定位来源。
 *
 * <p>此前只有家庭协作与人员启停写 audit_event，登录成功/失败、账号锁定、限流拦截
 * 完全没有记录，事后无法回答"谁在什么时候从哪里尝试过"。这里把登录链路补上，
 * 并校验失败事件不会因为"找不到账号"而被丢掉。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditCoverageTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditService audit;

    @Test
    void successfulLoginIsAuditedWithSourceIp() {
        String username = "audit_ok_1";
        ApiClient client = new ApiClient(port);
        assertEquals(201, client.post("/auth/register",
                "{\"username\":\"" + username + "\",\"name\":\"审计用户\",\"email\":\"audit_ok_1@example.com\",\"password\":\"secret123\"}").status());

        assertTrue(hasEvent("REGISTER", "SUCCESS", username), "注册应写入审计");

        assertEquals(200, client.post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}").status());

        Map<String, Object> event = latestLoginEvent(username);
        assertEquals("SUCCESS", event.get("result"));
        assertEquals("127.0.0.1", event.get("ip"), "成功登录应记录来源 IP");
    }

    @Test
    void failedLoginIsAuditedAsFailure() {
        String username = "audit_bad_1";
        ApiClient client = new ApiClient(port);
        assertEquals(201, client.post("/auth/register",
                "{\"username\":\"" + username + "\",\"name\":\"审计用户\",\"email\":\"audit_bad_1@example.com\",\"password\":\"secret123\"}").status());

        assertEquals(401, client.post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}").status());

        Map<String, Object> event = latestLoginEvent(username);
        assertEquals("FAILURE", event.get("result"));
        assertEquals("127.0.0.1", event.get("ip"), "失败登录同样要记录来源 IP，否则无法定位尝试来源");
    }

    @Test
    void unknownAccountStillAuditedWithoutActor() {
        ApiClient client = new ApiClient(port);
        assertEquals(401, client.post("/auth/login",
                "{\"username\":\"no_such_account_xyz\",\"password\":\"secret123\"}").status());

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT actor_id,result,ip,summary FROM audit_event WHERE action='LOGIN' AND summary LIKE ? ORDER BY id DESC LIMIT 1",
                "%no_such_account_xyz%");
        assertEquals(1, events.size(), "账号不存在时不能因为没有 actor 就丢弃审计");
        Map<String, Object> event = events.getFirst();
        assertEquals("FAILURE", event.get("result"));
        assertNull(event.get("actor_id"), "账号不存在，actor 应为空");
        assertEquals("127.0.0.1", event.get("ip"));
    }

    @Test
    void auditSummaryIsTruncatedToColumnLimit() {
        long actor = registerAndGetId("audit_long_1");
        String reason = "很长的审计说明".repeat(120);
        assertTrue(reason.length() > 500);

        audit.record(actor, "MANUAL_NOTE", "ACCOUNT", actor, reason);

        Integer length = jdbc.queryForObject(
                "SELECT CHAR_LENGTH(summary) FROM audit_event WHERE actor_id=? AND action='MANUAL_NOTE'", Integer.class, actor);
        assertEquals(500, length, "summary 超过 500 会被数据库拒绝，必须提前截断");
    }

    private boolean hasEvent(String action, String result, String username) {
        return !jdbc.queryForList("SELECT id FROM audit_event WHERE action=? AND result=? AND summary LIKE ?",
                action, result, "%" + username + "%").isEmpty();
    }

    private Map<String, Object> latestLoginEvent(String username) {
        long accountId = jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, username);
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT actor_id,result,ip,summary FROM audit_event WHERE action='LOGIN' AND actor_id=? ORDER BY id DESC LIMIT 1",
                accountId);
        assertTrue(!events.isEmpty(), "登录未写入审计事件");
        return events.getFirst();
    }

    private long registerAndGetId(String username) {
        new ApiClient(port).post("/auth/register",
                "{\"username\":\"" + username + "\",\"name\":\"审计用户\",\"email\":\"" + username + "@example.com\",\"password\":\"secret123\"}");
        return jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, username);
    }
}
