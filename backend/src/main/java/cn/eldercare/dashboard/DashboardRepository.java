package cn.eldercare.dashboard;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.eldercare.common.PageQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 看板与运营查询的数据访问层。
 *
 * <p>此前这些 SQL 直接写在 {@code DashboardController} 里，控制器同时承担鉴权、参数校验、
 * 结果装配和 SQL 拼装四件事，既难测试也无法针对慢查询单独优化。这里把 SQL 全部收敛进来，
 * 控制器只保留装配逻辑。</p>
 *
 * <p>待办列表的写法需要特别说明：原实现是一条 {@code SELECT DISTINCT} 带 7 个 OR 条件、
 * 5 个 LEFT JOIN，优化器无法使用任何索引，只能全表扫描后再去重。现改为每个条件一条支路
 * 的 UNION ALL，配合 {@code ix_order_status_id(status,id)} 索引，每条支路都变成按状态的
 * 有序索引扫描，并且各自 {@code LIMIT 100} 提前收敛；同一订单命中多条支路时由
 * {@code ROW_NUMBER()} 按优先级取一条，既保留原来的去重语义，也让"原因"的判定顺序显式化。</p>
 */
@Repository
public class DashboardRepository {
    /** 需要人工跟进的在途状态：终态订单不需要再协调 */
    private static final String IN_FLIGHT_STATUSES =
            "'WAITING_ASSIGNMENT','WAITING_ACCEPTANCE','WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION'";

    private static final String USER_ORDERS =
            " FROM service_order o JOIN family_grant g ON g.elder_id=o.elder_id"
                    + " WHERE g.account_id=? AND g.status='ACTIVE' AND g.can_view_service=1";

    private static final String ADMIN_ORDERS =
            " FROM service_order o JOIN elder_profile e ON e.id=o.elder_id"
                    + " LEFT JOIN supplement_plan s ON s.order_id=o.id"
                    + " AND s.status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED','CONFIRMED')";

    private static final String SUPPLEMENT_WINDOW =
            " LEFT JOIN supplement_plan s ON s.order_id=o.id AND s.status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED')";

    private static final String TODOS = """
            SELECT id,item_name_snapshot,status,priority FROM (
              SELECT t.id,t.item_name_snapshot,t.status,t.priority,
                     ROW_NUMBER() OVER (PARTITION BY t.id ORDER BY t.priority) AS rn
              FROM (
                (SELECT 1 AS priority,o.id,o.item_name_snapshot,o.status
                   FROM service_order o WHERE o.status='INTERRUPTED' ORDER BY o.id DESC LIMIT 100)
                UNION ALL
                (SELECT 2,o.id,o.item_name_snapshot,o.status
                   FROM service_order o
                  WHERE o.status IN (IN_FLIGHT)
                    AND EXISTS (SELECT 1 FROM assignment a JOIN account w ON w.id=a.worker_id
                                 WHERE a.order_id=o.id AND a.status IN ('PENDING','ACCEPTED') AND w.status='DISABLED')
                  ORDER BY o.id DESC LIMIT 100)
                UNION ALL
                (SELECT 3,o.id,o.item_name_snapshot,o.status
                   FROM service_order o JOIN account c ON c.id=o.creator_id
                  WHERE c.status='DISABLED' AND o.status IN (IN_FLIGHT)
                  ORDER BY o.id DESC LIMIT 100)
                UNION ALL
                (SELECT 4,o.id,o.item_name_snapshot,o.status
                   FROM service_order o SUP_WINDOW
                  WHERE o.status='WAITING_ASSIGNMENT' AND COALESCE(s.start_at,o.start_at)<?
                  ORDER BY o.id DESC LIMIT 100)
                UNION ALL
                (SELECT 5,o.id,o.item_name_snapshot,o.status
                   FROM service_order o SUP_WINDOW
                  WHERE o.status='WAITING_ACCEPTANCE' AND COALESCE(s.start_at,o.start_at)<?
                  ORDER BY o.id DESC LIMIT 100)
                UNION ALL
                (SELECT 6,o.id,o.item_name_snapshot,o.status
                   FROM service_order o SUP_WINDOW
                  WHERE o.status='WAITING_SERVICE' AND COALESCE(s.start_at,o.start_at)<?
                  ORDER BY o.id DESC LIMIT 100)
                UNION ALL
                (SELECT 7,o.id,o.item_name_snapshot,o.status
                   FROM service_order o
                  WHERE o.status='WAITING_CONFIRMATION'
                    AND EXISTS (SELECT 1 FROM fulfillment f WHERE f.order_id=o.id AND f.status='SUBMITTED' AND f.submitted_at<?)
                  ORDER BY o.id DESC LIMIT 100)
              ) t
            ) ranked WHERE ranked.rn=1 ORDER BY ranked.id DESC LIMIT 100
            """;

    private final String todoSql;
    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.todoSql = TODOS.replace("IN_FLIGHT", IN_FLIGHT_STATUSES).replace("SUP_WINDOW", SUPPLEMENT_WINDOW);
    }

    public record TodoRow(long id, String title, String status, int priority) { }

    // ---------- 用户端看板 ----------

    public long userOrders(long actor, List<String> statuses) {
        return count("SELECT COUNT(*)" + USER_ORDERS + inClause(statuses), actor);
    }

    public List<Map<String, Object>> userRecentOrders(long actor) {
        return jdbc.queryForList("SELECT o.id,o.item_name_snapshot,o.start_at,o.status,o.payable_amount"
                + USER_ORDERS + " ORDER BY o.id DESC LIMIT 5", actor);
    }

    // ---------- 服务人员看板 ----------

    public boolean workerAccepting(long workerId) {
        Integer value = jdbc.queryForObject("SELECT accepting FROM account WHERE id=?", Integer.class, workerId);
        return value != null && value != 0;
    }

    public long workerPendingAssignments(long workerId) {
        return count("SELECT COUNT(*) FROM assignment WHERE worker_id=? AND status='PENDING'", workerId);
    }

    public long workerActiveTasks(long workerId) {
        return count("SELECT COUNT(*) FROM assignment a JOIN service_order o ON o.id=a.order_id"
                + " WHERE a.worker_id=? AND a.status='ACCEPTED'"
                + " AND o.status IN ('WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION')", workerId);
    }

    public List<Map<String, Object>> workerTasks(long workerId) {
        return jdbc.queryForList("SELECT o.id AS order_id,o.item_name_snapshot,COALESCE(s.start_at,o.start_at) AS start_at,"
                + "COALESCE(s.end_at,o.end_at) AS end_at,o.status,a.status AS assignment_status"
                + " FROM assignment a JOIN service_order o ON o.id=a.order_id" + SUPPLEMENT_WINDOW
                + " WHERE a.worker_id=? AND a.status IN ('PENDING','ACCEPTED')"
                + " AND o.status IN ('WAITING_ACCEPTANCE','WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION')"
                + " ORDER BY COALESCE(s.start_at,o.start_at) LIMIT 6", workerId);
    }

    // ---------- 管理端看板 ----------

    /** 四个状态计数合并为一次扫描，避免同一张大表被连续 COUNT 四次 */
    public Map<String, Object> adminOrderCounts() {
        return jdbc.queryForMap("SELECT"
                + " COALESCE(SUM(status='WAITING_ASSIGNMENT'),0) AS waiting,"
                + " COALESCE(SUM(status='WAITING_CONFIRMATION'),0) AS confirming,"
                + " COALESCE(SUM(status='IN_SERVICE'),0) AS in_service,"
                + " COALESCE(SUM(status='COMPLETED'),0) AS completed"
                + " FROM service_order");
    }

    public long activeWorkers() {
        return count("SELECT COUNT(*) FROM account WHERE role='WORKER' AND status='ACTIVE'");
    }

    public List<Map<String, Object>> adminRecentOrders() {
        return jdbc.queryForList("SELECT o.id,o.item_name_snapshot,o.start_at,o.status,o.payable_amount,"
                + "e.full_name AS elder_name FROM service_order o JOIN elder_profile e ON e.id=o.elder_id"
                + " ORDER BY o.id DESC LIMIT 8");
    }

    /**
     * 管理端待办。四个时间参数依次为：待派单预警线、待接单预警线、待服务逾期线、待确认超时线。
     */
    public List<TodoRow> todos(LocalDateTime assignDeadline, LocalDateTime acceptDeadline,
                               LocalDateTime serviceDeadline, LocalDateTime confirmDeadline) {
        return jdbc.queryForList(todoSql, assignDeadline, acceptDeadline, serviceDeadline, confirmDeadline)
                .stream()
                .map(row -> new TodoRow(((Number) row.get("id")).longValue(),
                        (String) row.get("item_name_snapshot"),
                        (String) row.get("status"),
                        ((Number) row.get("priority")).intValue()))
                .toList();
    }

    // ---------- 管理端订单检索 ----------

    public long adminOrderTotal(String status, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = adminOrderArgs(where, status, keyword);
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT o.id)" + ADMIN_ORDERS + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> adminOrders(String status, String keyword, PageQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = adminOrderArgs(where, status, keyword);
        args.add(query.size());
        args.add(query.offset());
        return jdbc.queryForList("SELECT DISTINCT o.id,o.item_name_snapshot,"
                + "COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,"
                + "o.status,o.payable_amount,o.street_name_snapshot,e.full_name AS elder_name"
                + ADMIN_ORDERS + where + " ORDER BY o.id DESC LIMIT ? OFFSET ?", args.toArray());
    }

    public Map<String, Object> orderWindow(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.item_id,o.street_id,"
                + "COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,o.status"
                + " FROM service_order o" + SUPPLEMENT_WINDOW + " WHERE o.id=?", orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> candidates(long itemId, long streetId, java.time.LocalDateTime start,
                                                java.time.LocalDateTime end) {
        return jdbc.queryForList("SELECT DISTINCT a.id,a.display_name FROM account a"
                + " JOIN worker_capability c ON c.worker_id=a.id"
                + " JOIN worker_schedule s ON s.worker_id=a.id"
                + " WHERE a.role='WORKER' AND a.status='ACTIVE' AND a.accepting=1"
                + " AND c.item_id=? AND c.street_id=? AND c.active=1"
                + " AND s.weekday=? AND s.start_time<=? AND s.end_time>=?"
                + " AND NOT EXISTS(SELECT 1 FROM worker_leave l WHERE l.worker_id=a.id"
                + "   AND l.status IN ('PENDING','APPROVED') AND l.start_at<? AND l.end_at>?)"
                + " AND NOT EXISTS(SELECT 1 FROM worker_occupancy o WHERE o.worker_id=a.id AND o.active=1"
                + "   AND o.start_at<? AND o.end_at>?)"
                + " ORDER BY a.id",
                itemId, streetId, start.getDayOfWeek().getValue(),
                java.sql.Time.valueOf(start.toLocalTime()), java.sql.Time.valueOf(end.toLocalTime()),
                end, start, end, start);
    }

    // ---------- 管理端订单详情 ----------

    public Map<String, Object> adminOrder(long id) {
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT o.*,e.full_name AS elder_name,"
                + "a.display_name AS creator_name FROM service_order o"
                + " JOIN elder_profile e ON e.id=o.elder_id JOIN account a ON a.id=o.creator_id WHERE o.id=?", id);
        return orders.isEmpty() ? null : orders.getFirst();
    }

    public List<Map<String, Object>> orderEvents(long id) {
        return jdbc.queryForList("SELECT action,old_status,new_status,note,created_at,actor_id"
                + " FROM order_event WHERE order_id=? ORDER BY id", id);
    }

    public List<Map<String, Object>> orderAssignments(long id) {
        return jdbc.queryForList("SELECT x.id,x.worker_id,a.display_name AS worker_name,x.status,"
                + "x.assigned_at,x.responded_at,x.rejection_reason FROM assignment x"
                + " JOIN account a ON a.id=x.worker_id WHERE x.order_id=? ORDER BY x.id", id);
    }

    public List<Map<String, Object>> orderFulfillments(long id) {
        return jdbc.queryForList("SELECT id,assignment_id,status,started_at,submitted_at,confirmed_at"
                + " FROM fulfillment WHERE order_id=? ORDER BY id", id);
    }

    public List<Map<String, Object>> orderSupplements(long id) {
        return jdbc.queryForList("SELECT id,reason,start_at,end_at,status,created_at"
                + " FROM supplement_plan WHERE order_id=? ORDER BY id", id);
    }

    public List<Map<String, Object>> orderIncidents(long id) {
        return jdbc.queryForList("SELECT id,reason,status,reported_by,resolved_by,resolution,created_at,resolved_at"
                + " FROM service_incident WHERE order_id=? ORDER BY id", id);
    }

    public List<Map<String, Object>> orderFeeAdjustments(long id) {
        return jdbc.queryForList("SELECT id,original_amount,proposed_amount,reason,status,proposed_by,decided_by,"
                + "created_at,decided_at FROM fee_adjustment WHERE order_id=? ORDER BY id", id);
    }

    public List<Map<String, Object>> orderDisputes(long id) {
        return jdbc.queryForList("SELECT id,reason,status,opened_by,resolved_by,resolution,created_at,resolved_at"
                + " FROM order_dispute WHERE order_id=? ORDER BY id", id);
    }

    // ---------- 内部辅助 ----------

    private static List<Object> adminOrderArgs(StringBuilder where, String status, String keyword) {
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            where.append(" AND o.status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (CAST(o.id AS CHAR) LIKE ? OR e.full_name LIKE ? OR o.item_name_snapshot LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return args;
    }

    private static String inClause(List<String> statuses) {
        StringBuilder sql = new StringBuilder(" AND o.status IN (");
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('\'').append(statuses.get(i)).append('\'');
        }
        return sql.append(')').toString();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
