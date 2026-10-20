package cn.eldercare.dashboard;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;

import cn.eldercare.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final JdbcTemplate jdbc;
    public DashboardController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record UserDashboard(long waiting, long active, long completed, List<Map<String, Object>> recentOrders) { }
    public record WorkerDashboard(boolean accepting, long pending, long active, List<Map<String, Object>> tasks) { }
    public record AdminDashboard(long waitingAssignment, long waitingConfirmation, long inService,
                                 long completed, long activeWorkers, List<Map<String, Object>> recentOrders) { }
    public record AdminTodo(long orderId,String title,String reason,String priority) { }
    public record AdminOrderDetail(Map<String,Object> order,List<Map<String,Object>> events,
                                   List<Map<String,Object>> assignments,List<Map<String,Object>> fulfillments,
                                   List<Map<String,Object>> supplements,List<Map<String,Object>> incidents,
                                   List<Map<String,Object>> feeAdjustments,List<Map<String,Object>> disputes) { }

    @GetMapping("/user/dashboard")
    @PreAuthorize("hasRole('USER')")
    public UserDashboard user(Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        String from = " FROM service_order o JOIN family_grant g ON g.elder_id=o.elder_id WHERE g.account_id=? AND g.status='ACTIVE' AND g.can_view_service=1";
        long waiting = count("SELECT COUNT(*)" + from + " AND o.status IN ('WAITING_ASSIGNMENT','WAITING_ACCEPTANCE','WAITING_SERVICE')", actor);
        long active = count("SELECT COUNT(*)" + from + " AND o.status IN ('IN_SERVICE','WAITING_CONFIRMATION')", actor);
        long completed = count("SELECT COUNT(*)" + from + " AND o.status='COMPLETED'", actor);
        List<Map<String, Object>> recent = jdbc.queryForList("SELECT o.id,o.item_name_snapshot,o.start_at,o.status,o.payable_amount" + from + " ORDER BY o.id DESC LIMIT 5", actor);
        return new UserDashboard(waiting, active, completed, recent);
    }

    @GetMapping("/worker/dashboard")
    @PreAuthorize("hasRole('WORKER')")
    public WorkerDashboard worker(Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        Integer acceptingValue = jdbc.queryForObject("SELECT accepting FROM account WHERE id=?", Integer.class, actor);
        long pending = count("SELECT COUNT(*) FROM assignment WHERE worker_id=? AND status='PENDING'", actor);
        long active = count("SELECT COUNT(*) FROM assignment a JOIN service_order o ON o.id=a.order_id WHERE a.worker_id=? AND a.status='ACCEPTED' AND o.status IN ('WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION')", actor);
        List<Map<String, Object>> tasks = jdbc.queryForList("SELECT o.id AS order_id,o.item_name_snapshot,COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,o.status,a.status AS assignment_status FROM assignment a JOIN service_order o ON o.id=a.order_id LEFT JOIN supplement_plan s ON s.order_id=o.id AND s.status IN ('ASSIGNED','IN_SERVICE','SUBMITTED') WHERE a.worker_id=? AND a.status IN ('PENDING','ACCEPTED') AND o.status IN ('WAITING_ACCEPTANCE','WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION') ORDER BY COALESCE(s.start_at,o.start_at) LIMIT 6", actor);
        return new WorkerDashboard(acceptingValue != null && acceptingValue != 0, pending, active, tasks);
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboard admin() {
        long waiting = count("SELECT COUNT(*) FROM service_order WHERE status='WAITING_ASSIGNMENT'");
        long confirmation = count("SELECT COUNT(*) FROM service_order WHERE status='WAITING_CONFIRMATION'");
        long inService = count("SELECT COUNT(*) FROM service_order WHERE status='IN_SERVICE'");
        long completed = count("SELECT COUNT(*) FROM service_order WHERE status='COMPLETED'");
        long activeWorkers = count("SELECT COUNT(*) FROM account WHERE role='WORKER' AND status='ACTIVE'");
        List<Map<String, Object>> recent = jdbc.queryForList("SELECT o.id,o.item_name_snapshot,o.start_at,o.status,o.payable_amount,e.full_name AS elder_name FROM service_order o JOIN elder_profile e ON e.id=o.elder_id ORDER BY o.id DESC LIMIT 8");
        return new AdminDashboard(waiting, confirmation, inService, completed, activeWorkers, recent);
    }

    @GetMapping("/admin/todos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminTodo> todos(){
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT DISTINCT o.id,o.item_name_snapshot,o.status,COALESCE(s.start_at,o.start_at) AS start_at,w.status AS worker_status,c.status AS creator_status,f.submitted_at FROM service_order o JOIN account c ON c.id=o.creator_id LEFT JOIN supplement_plan s ON s.order_id=o.id AND s.status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED') LEFT JOIN assignment a ON a.order_id=o.id AND a.status IN ('PENDING','ACCEPTED') LEFT JOIN account w ON w.id=a.worker_id LEFT JOIN fulfillment f ON f.order_id=o.id AND f.status='SUBMITTED' WHERE o.status='INTERRUPTED' OR (a.id IS NOT NULL AND w.status='DISABLED') OR (c.status='DISABLED' AND o.status IN ('WAITING_ASSIGNMENT','WAITING_ACCEPTANCE','WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION')) OR (o.status='WAITING_ASSIGNMENT' AND COALESCE(s.start_at,o.start_at)<?) OR (o.status='WAITING_ACCEPTANCE' AND COALESCE(s.start_at,o.start_at)<?) OR (o.status='WAITING_SERVICE' AND COALESCE(s.start_at,o.start_at)<?) OR (o.status='WAITING_CONFIRMATION' AND f.submitted_at<?) ORDER BY o.id DESC LIMIT 100",
                now.plusHours(2),now.plusHours(1),now,now.minusHours(24));
        return rows.stream().map(row->{
            String status=(String)row.get("status");
            String reason="INTERRUPTED".equals(status)?"服务中断，需核实费用与后续安排":"DISABLED".equals(row.get("worker_status"))?"服务人员已停用，需立即协调任务":"DISABLED".equals(row.get("creator_status"))?"订单创建人已停用，需协调有权人操作":"WAITING_CONFIRMATION".equals(status)?"用户长时间未确认，需人工协调":"预约阶段已临近或逾期，需人工处理";
            return new AdminTodo(((Number)row.get("id")).longValue(),(String)row.get("item_name_snapshot"),reason,"HIGH");
        }).toList();
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> adminOrders(@RequestParam(defaultValue = "30") int size) {
        if (size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "每页数量须为 1–100");
        return jdbc.queryForList("SELECT o.id,o.item_name_snapshot,COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,o.status,o.payable_amount,o.street_name_snapshot,e.full_name AS elder_name FROM service_order o JOIN elder_profile e ON e.id=o.elder_id LEFT JOIN supplement_plan s ON s.order_id=o.id AND s.status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED','CONFIRMED') ORDER BY o.id DESC LIMIT ?", size);
    }

    @GetMapping("/admin/orders/{id}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> candidates(@PathVariable long id) {
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT o.item_id,o.street_id,COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,o.status FROM service_order o LEFT JOIN supplement_plan s ON s.order_id=o.id AND s.status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED') WHERE o.id=?", id);
        if (orders.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        Map<String, Object> order = orders.getFirst();
        if (!List.of("WAITING_ASSIGNMENT","WAITING_ACCEPTANCE","WAITING_SERVICE").contains(order.get("status"))) return List.of();
        java.time.LocalDateTime start = cn.eldercare.common.SqlValues.localDateTime(order.get("start_at"));
        java.time.LocalDateTime end = cn.eldercare.common.SqlValues.localDateTime(order.get("end_at"));
        return jdbc.queryForList("SELECT DISTINCT a.id,a.display_name FROM account a JOIN worker_capability c ON c.worker_id=a.id JOIN worker_schedule s ON s.worker_id=a.id WHERE a.role='WORKER' AND a.status='ACTIVE' AND a.accepting=1 AND c.item_id=? AND c.street_id=? AND c.active=1 AND s.weekday=? AND s.start_time<=? AND s.end_time>=? AND NOT EXISTS(SELECT 1 FROM worker_leave l WHERE l.worker_id=a.id AND l.status IN ('PENDING','APPROVED') AND l.start_at<? AND l.end_at>?) AND NOT EXISTS(SELECT 1 FROM worker_occupancy o WHERE o.worker_id=a.id AND o.active=1 AND o.start_at<? AND o.end_at>?) ORDER BY a.id",
                order.get("item_id"), order.get("street_id"), start.getDayOfWeek().getValue(),
                java.sql.Time.valueOf(start.toLocalTime()), java.sql.Time.valueOf(end.toLocalTime()), end, start, end, start);
    }

    @GetMapping("/admin/orders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOrderDetail adminOrder(@PathVariable long id) {
        List<Map<String,Object>> orders=jdbc.queryForList("SELECT o.*,e.full_name AS elder_name,a.display_name AS creator_name FROM service_order o JOIN elder_profile e ON e.id=o.elder_id JOIN account a ON a.id=o.creator_id WHERE o.id=?",id);
        if(orders.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");
        return new AdminOrderDetail(orders.getFirst(),
                jdbc.queryForList("SELECT action,old_status,new_status,note,created_at,actor_id FROM order_event WHERE order_id=? ORDER BY id",id),
                jdbc.queryForList("SELECT x.id,x.worker_id,a.display_name AS worker_name,x.status,x.assigned_at,x.responded_at,x.rejection_reason FROM assignment x JOIN account a ON a.id=x.worker_id WHERE x.order_id=? ORDER BY x.id",id),
                jdbc.queryForList("SELECT id,assignment_id,status,started_at,submitted_at,confirmed_at FROM fulfillment WHERE order_id=? ORDER BY id",id),
                jdbc.queryForList("SELECT id,reason,start_at,end_at,status,created_at FROM supplement_plan WHERE order_id=? ORDER BY id",id),
                jdbc.queryForList("SELECT id,reason,status,reported_by,resolved_by,resolution,created_at,resolved_at FROM service_incident WHERE order_id=? ORDER BY id",id),
                jdbc.queryForList("SELECT id,original_amount,proposed_amount,reason,status,proposed_by,decided_by,created_at,decided_at FROM fee_adjustment WHERE order_id=? ORDER BY id",id),
                jdbc.queryForList("SELECT id,reason,status,opened_by,resolved_by,resolution,created_at,resolved_at FROM order_dispute WHERE order_id=? ORDER BY id",id));
    }

    private long count(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
}
