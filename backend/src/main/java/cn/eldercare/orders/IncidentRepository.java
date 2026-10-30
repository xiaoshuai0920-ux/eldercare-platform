package cn.eldercare.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 服务异常（中断、补服务、费用核实、事后争议）的数据访问层。
 *
 * <p>这些写操作彼此耦合在同一笔事务里，搬动时保持"一次调用一条语句"的粒度，
 * 事务边界与状态机判断仍留在控制器，避免把业务规则隐式塞进 SQL。</p>
 */
@Repository
public class IncidentRepository {
    private final JdbcTemplate jdbc;

    public IncidentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> order(long id, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,creator_id,elder_id,item_id,duration_minutes,"
                + "status,version,payable_amount FROM service_order WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 带行锁的乐观状态推进；返回 0 表示订单已被并发修改 */
    public int transition(Object orderId, Object version, String currentStatus, String next) {
        return jdbc.update("UPDATE service_order SET status=?,version=version+1 WHERE id=? AND version=? AND status=?",
                next, orderId, version, currentStatus);
    }

    /** 写订单事件并投递到发件箱，保证通知不丢 */
    public void event(long orderId, long actor, String action, String from, String to, String note) {
        jdbc.update("INSERT INTO order_event(order_id,actor_id,action,old_status,new_status,note) VALUES(?,?,?,?,?,?)",
                orderId, actor, action, from, to, note);
        jdbc.update("INSERT INTO outbox_event(order_event_id) VALUES(LAST_INSERT_ID())");
    }

    // ---------- 服务中断 ----------

    /** 管理员按订单查已接单派单；服务人员按订单+本人查 */
    public List<Map<String, Object>> acceptedAssignmentsForUpdate(long orderId, Long workerId) {
        return workerId == null
                ? jdbc.queryForList("SELECT id FROM assignment WHERE order_id=? AND status='ACCEPTED' FOR UPDATE", orderId)
                : jdbc.queryForList("SELECT id FROM assignment WHERE order_id=? AND worker_id=? AND status='ACCEPTED' FOR UPDATE",
                        orderId, workerId);
    }

    public void reportIncident(long orderId, long reporter, String reason) {
        jdbc.update("INSERT INTO service_incident(order_id,reported_by,reason,status) VALUES(?,?,?,'OPEN')",
                orderId, reporter, reason);
    }

    public void interruptFulfillment(long orderId, long assignmentId) {
        jdbc.update("UPDATE fulfillment SET status='INTERRUPTED' WHERE order_id=? AND assignment_id=? AND status='IN_PROGRESS'",
                orderId, assignmentId);
    }

    public void releaseOccupancy(long assignmentId) {
        jdbc.update("UPDATE worker_occupancy SET active=0 WHERE assignment_id=?", assignmentId);
    }

    public void releaseAssignment(long assignmentId) {
        jdbc.update("UPDATE assignment SET status='RELEASED' WHERE id=?", assignmentId);
    }

    public void interruptSupplement(long orderId) {
        jdbc.update("UPDATE supplement_plan SET status='INTERRUPTED' WHERE order_id=? AND status='IN_SERVICE'", orderId);
    }

    // ---------- 异常列表 ----------

    public long incidentCount() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM service_incident i JOIN service_order o ON o.id=i.order_id"
                + " JOIN elder_profile e ON e.id=o.elder_id", Long.class);
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> incidentPage(int size, int offset) {
        return jdbc.queryForList("SELECT i.id,i.order_id,i.reason,i.status,i.created_at,o.status AS order_status,"
                + "o.item_name_snapshot,o.payable_amount,e.full_name AS elder_name FROM service_incident i"
                + " JOIN service_order o ON o.id=i.order_id JOIN elder_profile e ON e.id=o.elder_id"
                + " ORDER BY i.id DESC LIMIT ? OFFSET ?", size, offset);
    }

    public Map<String, Object> incidentForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,order_id,status FROM service_incident WHERE id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int resolveIncident(long id, long resolver, String resolution, LocalDateTime resolvedAt) {
        return jdbc.update("UPDATE service_incident SET status='RESOLVED',resolved_by=?,resolution=?,resolved_at=?"
                + " WHERE id=? AND status='OPEN'", resolver, resolution, resolvedAt, id);
    }

    // ---------- 补服务 ----------

    public void planSupplement(long orderId, long createdBy, String reason, LocalDateTime startAt, LocalDateTime endAt) {
        jdbc.update("INSERT INTO supplement_plan(order_id,created_by,reason,start_at,end_at,status) VALUES(?,?,?,?,?,'PLANNED')",
                orderId, createdBy, reason, startAt, endAt);
    }

    public List<Map<String, Object>> supplements(long orderId) {
        return jdbc.queryForList("SELECT id,reason,start_at,end_at,status,created_at FROM supplement_plan"
                + " WHERE order_id=? ORDER BY id DESC", orderId);
    }

    // ---------- 异常费用 ----------

    public int pendingFeeProposals(long orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM fee_adjustment WHERE order_id=? AND status='PENDING'",
                Integer.class, orderId);
        return count == null ? 0 : count;
    }

    public void proposeFee(long orderId, long proposedBy, Object originalAmount, BigDecimal proposed, String reason) {
        jdbc.update("INSERT INTO fee_adjustment(order_id,proposed_by,original_amount,proposed_amount,reason,status)"
                + " VALUES(?,?,?,?,?,'PENDING')", orderId, proposedBy, originalAmount, proposed, reason);
    }

    public List<Map<String, Object>> fees(long orderId) {
        return jdbc.queryForList("SELECT id,original_amount,proposed_amount,reason,status,created_at FROM fee_adjustment"
                + " WHERE order_id=? ORDER BY id DESC", orderId);
    }

    public Map<String, Object> feeForUpdate(long id, long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM fee_adjustment WHERE id=? AND order_id=? FOR UPDATE", id, orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void decideFee(long id, long decidedBy, boolean approve, LocalDateTime decidedAt) {
        jdbc.update("UPDATE fee_adjustment SET status=?,decided_by=?,decided_at=? WHERE id=?",
                approve ? "APPROVED" : "REJECTED", decidedBy, decidedAt, id);
    }

    public void updatePayable(long orderId, BigDecimal amount) {
        jdbc.update("UPDATE service_order SET payable_amount=? WHERE id=?", amount, orderId);
    }

    public void resolveIncident(long orderId, Object resolver, String resolution, LocalDateTime resolvedAt) {
        jdbc.update("UPDATE service_incident SET status='RESOLVED',resolved_by=?,resolution=?,resolved_at=?"
                + " WHERE order_id=? AND status='OPEN'", resolver, resolution, resolvedAt, orderId);
    }

    public void createSettlement(long orderId, BigDecimal amount) {
        jdbc.update("INSERT INTO settlement(order_id,amount,version,status) VALUES(?,?,1,'WAITING_METHOD')", orderId, amount);
    }

    public void createWaivedSettlement(long orderId, LocalDateTime settledAt) {
        jdbc.update("INSERT INTO settlement(order_id,amount,version,method,status,settled_at) VALUES(?,0,1,'WAIVED','SETTLED',?)",
                orderId, settledAt);
    }

    // ---------- 事后争议 ----------

    public int openDisputes(long orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM order_dispute WHERE order_id=? AND status='OPEN'",
                Integer.class, orderId);
        return count == null ? 0 : count;
    }

    public void openDispute(long orderId, long openedBy, String reason) {
        jdbc.update("INSERT INTO order_dispute(order_id,opened_by,reason,status) VALUES(?,?,?,'OPEN')",
                orderId, openedBy, reason);
    }

    public long disputeCount() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM order_dispute d JOIN account a ON a.id=d.opened_by", Long.class);
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> disputePage(int size, int offset) {
        return jdbc.queryForList("SELECT d.id,d.order_id,d.reason,d.status,d.created_at,a.display_name AS opened_by_name"
                + " FROM order_dispute d JOIN account a ON a.id=d.opened_by ORDER BY d.id DESC LIMIT ? OFFSET ?", size, offset);
    }

    public Map<String, Object> disputeForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM order_dispute WHERE id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void resolveDispute(long id, long resolvedBy, String resolution, LocalDateTime resolvedAt) {
        jdbc.update("UPDATE order_dispute SET status='RESOLVED',resolved_by=?,resolution=?,resolved_at=? WHERE id=?",
                resolvedBy, resolution, resolvedAt, id);
    }
}
