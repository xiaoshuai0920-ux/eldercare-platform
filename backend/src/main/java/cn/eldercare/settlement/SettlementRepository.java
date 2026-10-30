package cn.eldercare.settlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 结算与模拟支付尝试的数据访问层。 */
@Repository
public class SettlementRepository {
    private final JdbcTemplate jdbc;

    public SettlementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> order(long id, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,creator_id,elder_id FROM service_order WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> settlement(long orderId, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM settlement WHERE order_id=?" + (lock ? " FOR UPDATE" : ""), orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> attempts(long settlementId) {
        return jdbc.queryForList("SELECT attempt_ref,amount,settlement_version,remote_status,created_at,synced_at"
                + " FROM payment_attempt WHERE settlement_id=? ORDER BY id DESC", settlementId);
    }

    /** 行锁读取最近一次支付尝试，用于同步远程状态时避免并发重复入账 */
    public Map<String, Object> latestAttemptForUpdate(long settlementId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM payment_attempt WHERE settlement_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE", settlementId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insertAttempt(long settlementId, String ref, BigDecimal amount, int version) {
        jdbc.update("INSERT INTO payment_attempt(settlement_id,attempt_ref,amount,settlement_version,remote_status)"
                + " VALUES(?,?,?,?,'UNREGISTERED')", settlementId, ref, amount, version);
    }

    public void markProcessing(long settlementId) {
        jdbc.update("UPDATE settlement SET method='SIMULATED',status='PROCESSING' WHERE id=?", settlementId);
    }

    public void updateAttemptRemote(Object attemptId, String status, LocalDateTime syncedAt) {
        jdbc.update("UPDATE payment_attempt SET remote_status=?,synced_at=? WHERE id=?", status, syncedAt, attemptId);
    }

    /** 未终结的模拟交易数量（FAILED/CANCELLED 之外的都算未终结） */
    public int unresolvedAttempts(long settlementId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt WHERE settlement_id=?"
                + " AND remote_status NOT IN ('FAILED','CANCELLED')", Integer.class, settlementId);
        return count == null ? 0 : count;
    }

    public void settleSimulated(long settlementId, LocalDateTime settledAt) {
        jdbc.update("UPDATE settlement SET status='SETTLED',method='SIMULATED',settled_at=? WHERE id=?",
                settledAt, settlementId);
    }

    public void settleOffline(long settlementId, LocalDateTime settledAt) {
        jdbc.update("UPDATE settlement SET status='SETTLED',method='OFFLINE',settled_at=? WHERE id=?",
                settledAt, settlementId);
    }

    public void markFailed(long settlementId) {
        jdbc.update("UPDATE settlement SET status='FAILED' WHERE id=?", settlementId);
    }

    public void event(long settlementId, long actor, String action, String note) {
        jdbc.update("INSERT INTO settlement_event(settlement_id,actor_id,action,note) VALUES(?,?,?,?)",
                settlementId, actor, action, note);
    }
}
