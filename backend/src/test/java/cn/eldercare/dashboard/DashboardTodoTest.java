package cn.eldercare.dashboard;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cn.eldercare.support.ApiClient;
import cn.eldercare.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端待办的判定逻辑。
 *
 * <p>待办查询从"一条 DISTINCT + 7 个 OR"改成按条件分路后，最容易出错的是原因判定顺序与去重语义。
 * 这里按支路逐一验证，并确认同一订单同时命中多个条件时只产生一条待办。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DashboardTodoTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void interruptedOrderNeedsManualFollowUp() {
        long orderId = createOrder("WAITING_ASSIGNMENT", LocalDateTime.now().plusDays(1));
        jdbc.update("UPDATE service_order SET status='INTERRUPTED' WHERE id=?", orderId);

        assertEquals("服务中断，需核实费用与后续安排", reasonOf(orderId));
    }

    @Test
    void disabledWorkerAssignmentNeedsReassignment() {
        long orderId = createOrder("WAITING_SERVICE", LocalDateTime.now().plusHours(3));
        long workerId = createWorker("todo_worker_1");
        jdbc.update("INSERT INTO assignment(order_id,worker_id,status,assigned_by) VALUES(?,?,'ACCEPTED',?)",
                orderId, workerId, adminId());
        jdbc.update("UPDATE account SET status='DISABLED' WHERE id=?", workerId);

        assertEquals("服务人员已停用，需立即协调任务", reasonOf(orderId));
    }

    @Test
    void overdueServiceStartNeedsManualHandling() {
        long orderId = createOrder("WAITING_SERVICE", LocalDateTime.now().minusHours(2));

        assertEquals("预约阶段已临近或逾期，需人工处理", reasonOf(orderId));
    }

    @Test
    void orderMatchingSeveralConditionsAppearsOnlyOnce() {
        // 同时命中"服务人员已停用"（优先级 2）与"预约逾期"（优先级 6）
        long orderId = createOrder("WAITING_SERVICE", LocalDateTime.now().minusHours(3));
        long workerId = createWorker("todo_worker_2");
        jdbc.update("INSERT INTO assignment(order_id,worker_id,status,assigned_by) VALUES(?,?,'ACCEPTED',?)",
                orderId, workerId, adminId());
        jdbc.update("UPDATE account SET status='DISABLED' WHERE id=?", workerId);

        long matches = fetchTodos().stream().filter(node -> node.path("orderId").asLong() == orderId).count();
        assertEquals(1, matches, "同一订单命中多个条件时只应产生一条待办");
        assertEquals("服务人员已停用，需立即协调任务", reasonOf(orderId), "去重后应保留优先级更高的原因");
    }

    // ---------- 辅助 ----------

    private List<JsonNode> fetchTodos() {
        ApiClient.Result result = admin().get("/admin/todos");
        assertEquals(200, result.status(), result.body());
        List<JsonNode> rows = new ArrayList<>();
        result.json().forEach(rows::add);
        return rows;
    }

    private String reasonOf(long orderId) {
        Optional<JsonNode> row = fetchTodos().stream()
                .filter(node -> node.path("orderId").asLong() == orderId)
                .findFirst();
        assertTrue(row.isPresent(), "待办列表中缺少订单 " + orderId);
        return row.get().path("reason").asText();
    }

    private long createOrder(String status, LocalDateTime startAt) {
        return insertAndGetId("INSERT INTO service_order(creator_id,elder_id,item_id,item_name_snapshot,"
                        + "category_snapshot,rule_snapshot,pricing_model_snapshot,unit_price_snapshot,duration_minutes,"
                        + "original_amount,payable_amount,street_id,street_name_snapshot,address_snapshot,start_at,end_at,"
                        + "status,version) VALUES(?,?,?,'待办测试项目','CLEANING','测试规则','HOURLY',?,120,?,?,?,"
                        + "'待办测试街道','测试地址',?,?,?,0)",
                adminId(), ensureElder(), ensureItem(), BigDecimal.valueOf(50), BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), ensureArea(), startAt, startAt.plusMinutes(120), status);
    }

    private long ensureArea() {
        Long id = findId("SELECT id FROM service_area WHERE name='待办测试街道'");
        if (id != null) return id;
        return insertAndGetId("INSERT INTO service_area(parent_id,level,name) VALUES(NULL,'STREET','待办测试街道')");
    }

    private long ensureItem() {
        Long id = findId("SELECT id FROM service_item WHERE name='待办测试项目'");
        if (id != null) return id;
        return insertAndGetId("INSERT INTO service_item(category,name,description,pricing_model,unit_price,"
                + "min_minutes,max_minutes,available_from,available_to,rule_text,active) VALUES('CLEANING','待办测试项目',"
                + "'用于待办测试','HOURLY',50.00,60,240,'08:00:00','18:00:00','测试规则',TRUE)");
    }

    private long ensureElder() {
        Long id = findId("SELECT id FROM elder_profile WHERE full_name='待办测试老人'");
        if (id != null) return id;
        return insertAndGetId("INSERT INTO elder_profile(full_name,birth_date,created_by,status) VALUES('待办测试老人','1940-01-01',?,'ACTIVE')",
                adminId());
    }

    /** 用 KeyHolder 取主键：连接池下 update 与 queryForObject 可能不是同一条连接，LAST_INSERT_ID() 不可靠 */
    private long insertAndGetId(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("插入未返回自增主键：" + sql);
        return id.longValue();
    }

    private long createWorker(String username) {
        ApiClient.Result created = admin().post("/admin/workers",
                "{\"username\":\"" + username + "\",\"name\":\"待办测试人员\",\"password\":\"secret123\",\"email\":\""
                        + username + "@example.com\"}");
        if (created.status() != 200) {
            return findId("SELECT id FROM account WHERE username=?", username);
        }
        return created.json().path("id").asLong();
    }

    private long adminId() {
        Long id = findId("SELECT id FROM account WHERE username='testadmin'");
        if (id == null) throw new IllegalStateException("管理员引导账号缺失，无法准备待办测试数据");
        return id;
    }

    /** queryForObject 在 0 行时会抛异常，这里显式允许"查不到" */
    private Long findId(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : ((Number) rows.getFirst().get("id")).longValue();
    }

    private ApiClient admin() {
        ApiClient client = new ApiClient(port);
        assertEquals(200, client.post("/auth/login",
                "{\"username\":\"testadmin\",\"password\":\"test-admin-pass\"}").status());
        return client;
    }
}
