package cn.eldercare.orders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.PageQuery;
import cn.eldercare.common.SqlValues;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.eldercare.family.PermissionColumn;

@Service
public class OrderService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final FamilyService family;
    private final OrderActionAccess actionAccess;
    public OrderService(JdbcTemplate jdbc, FamilyService family, OrderActionAccess actionAccess) { this.jdbc = jdbc; this.family = family; this.actionAccess = actionAccess; }

    public record CreateOrder(long elderId, long itemId, long streetId, String address, String contactPhone, LocalDateTime startAt, int durationMinutes) { }
    public record OrderCounts(long total, long pending, long active, long upcoming, long done) { }
    public record Page<T>(List<T> items, int page, int size, long total, OrderCounts counts) { }
    private static final Map<String, List<String>> ORDER_GROUPS = Map.of(
            "action", List.of("WAITING_CONFIRMATION"),
            "active", List.of("IN_SERVICE"),
            "upcoming", List.of("WAITING_ASSIGNMENT", "WAITING_ACCEPTANCE", "WAITING_SERVICE"),
            "done", List.of("COMPLETED", "CANCELLED", "ABNORMAL_CLOSED", "INTERRUPTED"));

    @Transactional
    public long create(long actorId, CreateOrder request, String key) {
        family.requirePermission(actorId, request.elderId(), PermissionColumn.BOOK);
        String fingerprint = hash(request.toString());
        try {
            jdbc.update("INSERT INTO idempotency_record(actor_id,command_name,request_key,request_hash) VALUES(?,'ORDER_CREATE',?,?)", actorId, key, fingerprint);
        } catch (DuplicateKeyException duplicate) {
            Map<String, Object> prior = jdbc.queryForMap("SELECT request_hash,result_id FROM idempotency_record WHERE actor_id=? AND command_name='ORDER_CREATE' AND request_key=?", actorId, key);
            if (!fingerprint.equals(prior.get("request_hash"))) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_MISMATCH", "此请求标识已用于不同预约");
            if (prior.get("result_id") == null) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同预约正在处理");
            return ((Number) prior.get("result_id")).longValue();
        }
        Map<String, Object> item = one("SELECT * FROM service_item WHERE id=? AND active=1", request.itemId(), "SERVICE_NOT_AVAILABLE", "服务已下架或不存在");
        Integer covered = jdbc.queryForObject("SELECT COUNT(*) FROM service_item_area WHERE item_id=? AND street_id=?", Integer.class, request.itemId(), request.streetId());
        if (covered == null || covered == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "AREA_NOT_COVERED", "该项目暂不覆盖所选街道");
        Map<String, Object> area = one("SELECT s.name AS street,d.name AS district,c.name AS city FROM service_area s JOIN service_area d ON d.id=s.parent_id JOIN service_area c ON c.id=d.parent_id WHERE s.id=? AND s.level='STREET' AND s.active=1 AND d.active=1 AND c.active=1", request.streetId(), "AREA_NOT_AVAILABLE", "服务区域无效");
        LocalDateTime now = LocalDateTime.now(ZONE);
        BigDecimal amount = OrderRules.quote((String) item.get("pricing_model"), (BigDecimal) item.get("unit_price"), request.durationMinutes(),
                ((Number) item.get("min_minutes")).intValue(), ((Number) item.get("max_minutes")).intValue(),
                ((Time) item.get("available_from")).toLocalTime(), ((Time) item.get("available_to")).toLocalTime(),
                ((Number) item.get("advance_minutes")).intValue(), request.startAt(), now);
        String streetName = area.get("city") + " " + area.get("district") + " " + area.get("street");
        GeneratedKeyHolder generated = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO service_order(creator_id,elder_id,item_id,item_name_snapshot,category_snapshot,rule_snapshot,pricing_model_snapshot,unit_price_snapshot,duration_minutes,original_amount,payable_amount,street_id,street_name_snapshot,address_snapshot,contact_phone,start_at,end_at,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'WAITING_ASSIGNMENT')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, actorId);
            statement.setLong(2, request.elderId());
            statement.setLong(3, request.itemId());
            statement.setString(4, (String) item.get("name"));
            statement.setString(5, (String) item.get("category"));
            statement.setString(6, (String) item.get("rule_text"));
            statement.setString(7, (String) item.get("pricing_model"));
            statement.setBigDecimal(8, (BigDecimal) item.get("unit_price"));
            statement.setInt(9, request.durationMinutes());
            statement.setBigDecimal(10, amount);
            statement.setBigDecimal(11, amount);
            statement.setLong(12, request.streetId());
            statement.setString(13, streetName);
            statement.setString(14, request.address().trim());
            statement.setString(15, request.contactPhone() == null ? null : request.contactPhone().trim());
            statement.setObject(16, request.startAt());
            statement.setObject(17, request.startAt().plusMinutes(request.durationMinutes()));
            return statement;
        }, generated);
        long orderId = generated.getKey().longValue();
        jdbc.update("UPDATE idempotency_record SET result_id=? WHERE actor_id=? AND command_name='ORDER_CREATE' AND request_key=?", orderId, actorId, key);
        event(orderId, actorId, "CREATE", null, "WAITING_ASSIGNMENT", "预约已创建，等待人工派单");
        return orderId;
    }

    public Page<Map<String, Object>> userOrders(long actorId, int page, int size, String group) {
        PageQuery.of(page, size);
        String selected = group == null || group.isBlank() ? "ALL" : group;
        if (!"ALL".equals(selected) && !ORDER_GROUPS.containsKey(selected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_GROUP", "订单分组无效");
        }
        String access = " FROM service_order o JOIN family_grant g ON g.elder_id=o.elder_id"
                + " WHERE g.account_id=? AND g.status='ACTIVE' AND g.can_view_service=1";
        List<Map<String, Object>> totals = jdbc.queryForList("SELECT o.status,COUNT(*) AS total" + access + " GROUP BY o.status", actorId);
        long all = 0, pending = 0, active = 0, upcoming = 0, done = 0;
        for (Map<String, Object> item : totals) {
            String status = (String) item.get("status");
            long count = ((Number) item.get("total")).longValue();
            all += count;
            if (ORDER_GROUPS.get("action").contains(status)) pending += count;
            if (ORDER_GROUPS.get("active").contains(status)) active += count;
            if (ORDER_GROUPS.get("upcoming").contains(status)) upcoming += count;
            if (ORDER_GROUPS.get("done").contains(status)) done += count;
        }
        var counts = new OrderCounts(all, pending, active, upcoming, done);
        long filteredTotal = switch (selected) {
            case "action" -> pending;
            case "active" -> active;
            case "upcoming" -> upcoming;
            case "done" -> done;
            default -> all;
        };
        List<Object> args = new ArrayList<>();
        args.add(actorId);
        String filter = "";
        if (!"ALL".equals(selected)) {
            List<String> statuses = ORDER_GROUPS.get(selected);
            filter = " AND o.status IN (" + String.join(",", java.util.Collections.nCopies(statuses.size(), "?")) + ")";
            args.addAll(statuses);
        }
        args.add(size);
        args.add((long) (page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.id,o.elder_id,o.item_name_snapshot,o.category_snapshot,"
                + "COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,o.payable_amount,o.status"
                + " FROM service_order o JOIN family_grant g ON g.elder_id=o.elder_id"
                + " LEFT JOIN supplement_plan s ON s.id=(SELECT MAX(sp.id) FROM supplement_plan sp WHERE sp.order_id=o.id"
                + " AND sp.status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED','CONFIRMED'))"
                + " WHERE g.account_id=? AND g.status='ACTIVE' AND g.can_view_service=1" + filter
                + " ORDER BY o.id DESC LIMIT ? OFFSET ?", args.toArray());
        return new Page<>(rows, page, size, filteredTotal, counts);
    }

    public Map<String, Object> userOrder(long actorId, long orderId) {
        Map<String, Object> order = order(orderId, false);
        family.requirePermission(actorId, ((Number) order.get("elder_id")).longValue(), PermissionColumn.VIEW_SERVICE);
        return order;
    }

    @Transactional
    public void assign(long adminId, long orderId, long workerId) {
        Map<String, Object> order = order(orderId, true);
        String current=(String)order.get("status");
        if(!List.of("WAITING_ASSIGNMENT","WAITING_ACCEPTANCE","WAITING_SERVICE").contains(current))
            throw conflict("REASSIGN_NOT_ALLOWED","服务已开始或订单已结束，不能普通改派");
        Map<String,Object> oldAssignment = "WAITING_ASSIGNMENT".equals(current) ? null : activeAssignment(orderId);
        if(oldAssignment!=null && ((Number)oldAssignment.get("worker_id")).longValue()==workerId){
            if("WAITING_ACCEPTANCE".equals(current))return;
            throw conflict("SAME_WORKER","请选择不同的服务人员");
        }
        Map<String, Object> worker = oneForUpdate("SELECT id,role,status,accepting FROM account WHERE id=?", workerId, "WORKER_NOT_FOUND", "服务人员不存在");
        if (!"WORKER".equals(worker.get("role")) || !"ACTIVE".equals(worker.get("status")) || !enabled(worker, "accepting")) {
            throw conflict("WORKER_UNAVAILABLE", "服务人员当前不可接单");
        }
        long itemId = ((Number) order.get("item_id")).longValue();
        long streetId = ((Number) order.get("street_id")).longValue();
        if (count("SELECT COUNT(*) FROM worker_capability WHERE worker_id=? AND item_id=? AND street_id=? AND active=1", workerId, itemId, streetId) == 0) {
            throw conflict("WORKER_UNQUALIFIED", "人员不具备此项目和区域能力");
        }
        Map<String,Object> supplement=activeSupplement(orderId);
        LocalDateTime start = supplement==null ? timestamp(order, "start_at") : timestamp(supplement,"start_at");
        LocalDateTime end = supplement==null ? timestamp(order, "end_at") : timestamp(supplement,"end_at");
        if (count("SELECT COUNT(*) FROM worker_schedule WHERE worker_id=? AND weekday=? AND start_time<=? AND end_time>=?", workerId,
                start.getDayOfWeek().getValue(), Time.valueOf(start.toLocalTime()), Time.valueOf(end.toLocalTime())) == 0) {
            throw conflict("OUTSIDE_SCHEDULE", "预约时间不在人员工作时段内");
        }
        if (count("SELECT COUNT(*) FROM worker_leave WHERE worker_id=? AND status IN ('PENDING','APPROVED') AND start_at<? AND end_at>?", workerId, end, start) > 0
                || count("SELECT COUNT(*) FROM worker_occupancy WHERE worker_id=? AND active=1 AND start_at<? AND end_at>?", workerId, end, start) > 0) {
            throw conflict("WORKER_TIME_CONFLICT", "人员请假或任务时间冲突");
        }
        if(oldAssignment!=null){
            jdbc.update("UPDATE assignment SET status='REPLACED' WHERE id=?",oldAssignment.get("id"));
            jdbc.update("UPDATE worker_occupancy SET active=0 WHERE assignment_id=?",oldAssignment.get("id"));
        }
        GeneratedKeyHolder generated = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO assignment(order_id,worker_id,status,assigned_by) VALUES(?,?,'PENDING',?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, orderId); statement.setLong(2, workerId); statement.setLong(3, adminId); return statement;
        }, generated);
        long assignmentId = generated.getKey().longValue();
        jdbc.update("INSERT INTO worker_occupancy(assignment_id,worker_id,start_at,end_at,active) VALUES(?,?,?,?,1)", assignmentId, workerId, start, end);
        if(supplement!=null)jdbc.update("UPDATE supplement_plan SET status='ASSIGNED' WHERE id=?",supplement.get("id"));
        transition(order, "WAITING_ACCEPTANCE");
        event(orderId, adminId, oldAssignment==null ? "ASSIGN" : "REASSIGN", current, "WAITING_ACCEPTANCE", oldAssignment==null ? "人工派单，等待服务人员接单" : "普通改派，原派单已释放，等待新人员接单");
    }

    @Transactional
    public void respondToAssignment(long workerId, long orderId, boolean accept, String reason) {
        Map<String, Object> order = order(orderId, true);
        if(accept && "WAITING_SERVICE".equals(order.get("status"))){requireWorkerAssignment(orderId,workerId);return;}
        requireStatus(order, "WAITING_ACCEPTANCE");
        Map<String, Object> assignment = activeAssignment(orderId);
        if (((Number) assignment.get("worker_id")).longValue() != workerId || !"PENDING".equals(assignment.get("status"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ASSIGNMENT_FORBIDDEN", "当前没有可处理的本人派单");
        }
        if (!accept && (reason == null || reason.isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "拒单必须填写原因");
        jdbc.update("UPDATE assignment SET status=?,responded_at=?,rejection_reason=? WHERE id=?", accept ? "ACCEPTED" : "REJECTED", LocalDateTime.now(ZONE), accept ? null : reason.trim(), assignment.get("id"));
        if (!accept) jdbc.update("UPDATE worker_occupancy SET active=0 WHERE assignment_id=?", assignment.get("id"));
        if(!accept)jdbc.update("UPDATE supplement_plan SET status='PLANNED' WHERE order_id=? AND status='ASSIGNED'",orderId);
        String next = accept ? "WAITING_SERVICE" : "WAITING_ASSIGNMENT";
        transition(order, next);
        event(orderId, workerId, accept ? "ACCEPT" : "REJECT", "WAITING_ACCEPTANCE", next, accept ? "服务人员已接单" : reason.trim());
    }

    @Transactional
    public void start(long workerId, long orderId) {
        Map<String, Object> order = order(orderId, true);
        if("IN_SERVICE".equals(order.get("status"))){requireWorkerAssignment(orderId,workerId);return;}
        requireStatus(order, "WAITING_SERVICE");
        Map<String, Object> assignment = requireWorkerAssignment(orderId, workerId);
        jdbc.update("INSERT INTO fulfillment(order_id,assignment_id,started_at,status) VALUES(?,?,?,'IN_PROGRESS')", orderId, assignment.get("id"), LocalDateTime.now(ZONE));
        jdbc.update("UPDATE supplement_plan SET status='IN_SERVICE' WHERE order_id=? AND status='ASSIGNED'",orderId);
        transition(order, "IN_SERVICE");
        event(orderId, workerId, "START", "WAITING_SERVICE", "IN_SERVICE", "开始服务");
    }

    @Transactional
    public void submitCompletion(long workerId, long orderId) {
        Map<String, Object> order = order(orderId, true);
        if("WAITING_CONFIRMATION".equals(order.get("status"))){requireWorkerAssignment(orderId,workerId);return;}
        requireStatus(order, "IN_SERVICE");
        Map<String, Object> assignment = requireWorkerAssignment(orderId, workerId);
        jdbc.update("UPDATE fulfillment SET submitted_at=?,status='SUBMITTED' WHERE order_id=? AND assignment_id=? AND status='IN_PROGRESS'", LocalDateTime.now(ZONE), orderId, assignment.get("id"));
        jdbc.update("UPDATE supplement_plan SET status='SUBMITTED' WHERE order_id=? AND status='IN_SERVICE'",orderId);
        transition(order, "WAITING_CONFIRMATION");
        event(orderId, workerId, "SUBMIT_COMPLETION", "IN_SERVICE", "WAITING_CONFIRMATION", "已提交完成，等待用户确认");
    }

    @Transactional
    public void confirm(long actorId, long orderId) {
        Map<String, Object> order = order(orderId, true);
        actionAccess.require(actorId, order, "CONFIRM");
        if("COMPLETED".equals(order.get("status")))return;
        requireStatus(order, "WAITING_CONFIRMATION");
        jdbc.update("UPDATE fulfillment SET confirmed_at=?,status='CONFIRMED' WHERE order_id=? AND status='SUBMITTED'", LocalDateTime.now(ZONE), orderId);
        jdbc.update("UPDATE worker_occupancy o JOIN assignment a ON a.id=o.assignment_id SET o.active=0 WHERE a.order_id=? AND a.status='ACCEPTED'", orderId);
        jdbc.update("UPDATE supplement_plan SET status='CONFIRMED' WHERE order_id=? AND status='SUBMITTED'",orderId);
        jdbc.update("UPDATE service_incident SET status='RESOLVED',resolved_by=?,resolution='补服务已由用户确认',resolved_at=? WHERE order_id=? AND status='OPEN'",actorId,LocalDateTime.now(ZONE),orderId);
        transition(order, "COMPLETED");
        jdbc.update("INSERT INTO settlement(order_id,amount,version,status) VALUES(?,?,1,'WAITING_METHOD')", orderId, order.get("payable_amount"));
        event(orderId, actorId, "CONFIRM", "WAITING_CONFIRMATION", "COMPLETED", "用户确认服务完成");
    }

    @Transactional
    public void cancel(long actorId, long orderId) {
        Map<String, Object> order = order(orderId, true);
        actionAccess.require(actorId, order, "CANCEL");
        String current = (String) order.get("status");
        if("CANCELLED".equals(current))return;
        if (!List.of("WAITING_ASSIGNMENT", "WAITING_ACCEPTANCE", "WAITING_SERVICE").contains(current)) {
            throw conflict("CANCEL_NOT_ALLOWED", "当前阶段不能普通取消，请联系管理员协调");
        }
        if(activeSupplement(orderId)!=null)throw conflict("SUPPLEMENT_CANCEL_COORDINATION","补服务取消需要管理员协调原中断订单");
        jdbc.update("UPDATE worker_occupancy o JOIN assignment a ON a.id=o.assignment_id SET o.active=0 WHERE a.order_id=? AND a.status IN ('PENDING','ACCEPTED')", orderId);
        jdbc.update("UPDATE assignment SET status='RELEASED' WHERE order_id=? AND status IN ('PENDING','ACCEPTED')", orderId);
        transition(order, "CANCELLED");
        event(orderId, actorId, "CANCEL", current, "CANCELLED", "用户取消预约");
    }

    private Map<String, Object> requireWorkerAssignment(long orderId, long workerId) {
        Map<String, Object> assignment = activeAssignment(orderId);
        if (((Number) assignment.get("worker_id")).longValue() != workerId || !"ACCEPTED".equals(assignment.get("status"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ASSIGNMENT_FORBIDDEN", "当前没有有效的本人派单");
        }
        return assignment;
    }

    private Map<String, Object> order(long id, boolean locked) {
        return one("SELECT * FROM service_order WHERE id=?" + (locked ? " FOR UPDATE" : ""), id, "ORDER_NOT_FOUND", "订单不存在");
    }
    private Map<String, Object> activeAssignment(long orderId) {
        return one("SELECT * FROM assignment WHERE order_id=? AND status IN ('PENDING','ACCEPTED') FOR UPDATE", orderId, "ASSIGNMENT_NOT_FOUND", "有效派单不存在");
    }
    private Map<String,Object> activeSupplement(long orderId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,start_at,end_at,status FROM supplement_plan WHERE order_id=? AND status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED') ORDER BY id DESC LIMIT 1 FOR UPDATE",orderId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private Map<String, Object> one(String sql, long id, String code, String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, code, message);
        return rows.getFirst();
    }
    private Map<String, Object> oneForUpdate(String sql, long id, String code, String message) { return one(sql + " FOR UPDATE", id, code, message); }
    private int count(String sql, Object... args) { Integer value = jdbc.queryForObject(sql, Integer.class, args); return value == null ? 0 : value; }
    private boolean enabled(Map<String, Object> row, String field) { return SqlValues.booleanValue(row.get(field)); }
    private LocalDateTime timestamp(Map<String, Object> row, String field) { return SqlValues.localDateTime(row.get(field)); }
    private void requireStatus(Map<String, Object> order, String expected) {
        if (!expected.equals(order.get("status"))) throw conflict("ORDER_STATE_CONFLICT", "订单状态已变化，请刷新后重试");
    }
    private void transition(Map<String, Object> order, String next) {
        int changed = jdbc.update("UPDATE service_order SET status=?,version=version+1 WHERE id=? AND version=? AND status=?", next, order.get("id"), order.get("version"), order.get("status"));
        if (changed != 1) throw conflict("ORDER_STATE_CONFLICT", "订单状态已变化，请刷新后重试");
    }
    private void event(long id, long actor, String action, String from, String to, String note) {
        jdbc.update("INSERT INTO order_event(order_id,actor_id,action,old_status,new_status,note) VALUES(?,?,?,?,?,?)", id, actor, action, from, to, note);
        jdbc.update("INSERT INTO outbox_event(order_event_id) VALUES(LAST_INSERT_ID())");
    }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private String hash(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
