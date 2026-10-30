package cn.eldercare.dashboard;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.PageQuery;
import cn.eldercare.common.PageResult;
import cn.eldercare.common.SqlValues;
import org.springframework.http.HttpStatus;
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
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> ASSIGNABLE_STATUSES =
            List.of("WAITING_ASSIGNMENT", "WAITING_ACCEPTANCE", "WAITING_SERVICE");

    private final DashboardRepository queries;

    public DashboardController(DashboardRepository queries) { this.queries = queries; }

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
        return new UserDashboard(
                queries.userOrders(actor, List.of("WAITING_ASSIGNMENT", "WAITING_ACCEPTANCE", "WAITING_SERVICE")),
                queries.userOrders(actor, List.of("IN_SERVICE", "WAITING_CONFIRMATION")),
                queries.userOrders(actor, List.of("COMPLETED")),
                queries.userRecentOrders(actor));
    }

    @GetMapping("/worker/dashboard")
    @PreAuthorize("hasRole('WORKER')")
    public WorkerDashboard worker(Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        return new WorkerDashboard(
                queries.workerAccepting(actor),
                queries.workerPendingAssignments(actor),
                queries.workerActiveTasks(actor),
                queries.workerTasks(actor));
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboard admin() {
        Map<String, Object> counts = queries.adminOrderCounts();
        return new AdminDashboard(
                number(counts.get("waiting")),
                number(counts.get("confirming")),
                number(counts.get("in_service")),
                number(counts.get("completed")),
                queries.activeWorkers(),
                queries.adminRecentOrders());
    }

    @GetMapping("/admin/todos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminTodo> todos() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        return queries.todos(now.plusHours(2), now.plusHours(1), now, now.minusHours(24)).stream()
                .map(row -> new AdminTodo(row.id(), row.title(), reasonOf(row.priority()), "HIGH"))
                .toList();
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<Map<String, Object>> adminOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        PageQuery query = PageQuery.of(page, size);
        return PageResult.of(queries.adminOrders(status, keyword, query), query, queries.adminOrderTotal(status, keyword));
    }

    @GetMapping("/admin/orders/{id}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> candidates(@PathVariable long id) {
        Map<String, Object> order = queries.orderWindow(id);
        if (order == null) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        if (!ASSIGNABLE_STATUSES.contains(order.get("status"))) return List.of();
        LocalDateTime start = SqlValues.localDateTime(order.get("start_at"));
        LocalDateTime end = SqlValues.localDateTime(order.get("end_at"));
        return queries.candidates(((Number) order.get("item_id")).longValue(),
                ((Number) order.get("street_id")).longValue(), start, end);
    }

    @GetMapping("/admin/orders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOrderDetail adminOrder(@PathVariable long id) {
        Map<String, Object> order = queries.adminOrder(id);
        if (order == null) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        return new AdminOrderDetail(order,
                queries.orderEvents(id),
                queries.orderAssignments(id),
                queries.orderFulfillments(id),
                queries.orderSupplements(id),
                queries.orderIncidents(id),
                queries.orderFeeAdjustments(id),
                queries.orderDisputes(id));
    }

    /** 待办原因按优先级判定：与 SQL 中 UNION ALL 支路的 priority 一一对应 */
    private static String reasonOf(int priority) {
        return switch (priority) {
            case 1 -> "服务中断，需核实费用与后续安排";
            case 2 -> "服务人员已停用，需立即协调任务";
            case 3 -> "订单创建人已停用，需协调有权人操作";
            case 7 -> "用户长时间未确认，需人工协调";
            default -> "预约阶段已临近或逾期，需人工处理";
        };
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
