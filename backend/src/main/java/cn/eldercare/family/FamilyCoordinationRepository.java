package cn.eldercare.family;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 家庭协作（本人关联、家庭争议、订单操作交接）的数据访问层。
 *
 * <p>迁移时发现一处隐患：交接校验原本把权限枚举直接拼进 SQL（{@code "g." + permission}），
 * 实际生成的是 {@code g.VIEW_SERVICE=1} 这类列名，MySQL 会直接报未知列。
 * 这里统一改用 {@link PermissionColumn#column()}，让枚举只作为白名单存在。</p>
 */
@Repository
public class FamilyCoordinationRepository {
    private final JdbcTemplate jdbc;

    public FamilyCoordinationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---------- 老人本人关联 ----------

    public boolean elderExistsForUpdate(long elderId) {
        return !jdbc.queryForList("SELECT id FROM elder_profile WHERE id=? FOR UPDATE", elderId).isEmpty();
    }

    public boolean hasActiveClaimForAccount(long accountId) {
        return count("SELECT COUNT(*) FROM elder_identity_claim WHERE account_id=? AND status IN ('PENDING','APPROVED')",
                accountId) > 0;
    }

    public boolean hasActiveClaimForElder(long elderId) {
        return count("SELECT COUNT(*) FROM elder_identity_claim WHERE elder_id=? AND status IN ('PENDING','APPROVED')",
                elderId) > 0;
    }

    public void insertClaim(long elderId, long accountId) {
        jdbc.update("INSERT INTO elder_identity_claim(elder_id,account_id,status) VALUES(?,?,'PENDING')", elderId, accountId);
    }

    public List<Map<String, Object>> ownClaims(long elderId, long accountId) {
        return jdbc.queryForList("SELECT id,status,requested_at,review_note,reviewed_at FROM elder_identity_claim"
                + " WHERE elder_id=? AND account_id=? ORDER BY id DESC", elderId, accountId);
    }

    public long claimsCount() {
        return count("SELECT COUNT(*) FROM elder_identity_claim c JOIN elder_profile e ON e.id=c.elder_id"
                + " JOIN account a ON a.id=c.account_id");
    }

    public List<Map<String, Object>> claimsPage(int size, int offset) {
        return jdbc.queryForList("SELECT c.id,c.elder_id,e.full_name AS elder_name,c.account_id,"
                + "a.display_name AS account_name,c.status,c.requested_at,c.review_note,c.reviewed_at"
                + " FROM elder_identity_claim c JOIN elder_profile e ON e.id=c.elder_id"
                + " JOIN account a ON a.id=c.account_id ORDER BY c.id DESC LIMIT ? OFFSET ?", size, offset);
    }

    public Map<String, Object> claimForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM elder_identity_claim WHERE id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void decideClaim(long id, String status, long reviewer, String note, LocalDateTime reviewedAt) {
        jdbc.update("UPDATE elder_identity_claim SET status=?,reviewed_by=?,review_note=?,reviewed_at=? WHERE id=?",
                status, reviewer, note, reviewedAt, id);
    }

    public boolean isActiveUser(long accountId) {
        return !jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='USER' AND status='ACTIVE'", accountId).isEmpty();
    }

    // ---------- 家庭关联争议 ----------

    public boolean hasGrant(long elderId, long accountId) {
        return count("SELECT COUNT(*) FROM family_grant WHERE elder_id=? AND account_id=?", elderId, accountId) > 0;
    }

    public void openDispute(long elderId, long reporter, String reason) {
        jdbc.update("INSERT INTO family_dispute(elder_id,reporter_id,reason,status) VALUES(?,?,?,'OPEN')",
                elderId, reporter, reason);
    }

    public List<Map<String, Object>> ownDisputes(long elderId, long reporterId) {
        return jdbc.queryForList("SELECT id,reason,status,reported_at,resolution,resolved_at FROM family_dispute"
                + " WHERE elder_id=? AND reporter_id=? ORDER BY id DESC", elderId, reporterId);
    }

    public long disputesCount() {
        return count("SELECT COUNT(*) FROM family_dispute d JOIN elder_profile e ON e.id=d.elder_id"
                + " JOIN account a ON a.id=d.reporter_id");
    }

    public List<Map<String, Object>> disputesPage(int size, int offset) {
        return jdbc.queryForList("SELECT d.id,d.elder_id,e.full_name AS elder_name,d.reporter_id,"
                + "a.display_name AS reporter_name,d.reason,d.status,d.reported_at,d.resolution,d.resolved_at"
                + " FROM family_dispute d JOIN elder_profile e ON e.id=d.elder_id"
                + " JOIN account a ON a.id=d.reporter_id ORDER BY d.id DESC LIMIT ? OFFSET ?", size, offset);
    }

    public int resolveDispute(long id, long resolver, String resolution, LocalDateTime resolvedAt) {
        return jdbc.update("UPDATE family_dispute SET status='RESOLVED',resolved_by=?,resolution=?,resolved_at=?"
                + " WHERE id=? AND status='OPEN'", resolver, resolution, resolvedAt, id);
    }

    // ---------- 订单操作交接 ----------

    public List<Map<String, Object>> handovers(long orderId) {
        return jdbc.queryForList("SELECT h.id,h.action,h.target_account_id,a.display_name AS target_name,h.reason,"
                + "h.status,h.assigned_at,h.revoked_at FROM order_action_handover h"
                + " JOIN account a ON a.id=h.target_account_id WHERE h.order_id=? ORDER BY h.id DESC", orderId);
    }

    public List<Map<String, Object>> handoverCandidates(long orderId, PermissionColumn permission) {
        return jdbc.queryForList(handoverCandidateSql(permission), orderId);
    }

    static String handoverCandidateSql(PermissionColumn permission) {
        return "SELECT DISTINCT a.id AS account_id,a.username,a.display_name FROM service_order o"
                + " JOIN family_grant g ON g.elder_id=o.elder_id AND g.status='ACTIVE' AND g.can_view_service=1"
                + " AND g." + permission.column() + "=1 JOIN account a ON a.id=g.account_id"
                + " AND a.role='USER' AND a.status='ACTIVE' WHERE o.id=? AND a.id<>o.creator_id"
                + " ORDER BY a.display_name,a.id";
    }

    public Map<String, Object> orderForUpdate(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,creator_id,elder_id,status FROM service_order WHERE id=? FOR UPDATE", orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 创建人是否仍持有该老人档案上的某个操作权限 */
    public boolean creatorHolds(PermissionColumn permission, long creatorId, long elderId) {
        return count("SELECT COUNT(*) FROM account a JOIN family_grant g ON g.account_id=a.id"
                + " WHERE a.id=? AND a.status='ACTIVE' AND g.elder_id=? AND g.status='ACTIVE'"
                + " AND g.can_view_service=1 AND g." + permission.column() + "=1", creatorId, elderId) > 0;
    }

    public void revokeHandovers(long orderId, String action, long actor, LocalDateTime revokedAt) {
        jdbc.update("UPDATE order_action_handover SET status='REVOKED',revoked_by=?,revoked_at=?"
                + " WHERE order_id=? AND action=? AND status='ACTIVE'", actor, revokedAt, orderId, action);
    }

    public void insertHandover(long orderId, String action, long targetAccountId, long assignedBy, String reason) {
        jdbc.update("INSERT INTO order_action_handover(order_id,action,target_account_id,assigned_by,reason,status)"
                + " VALUES(?,?,?,?,?,'ACTIVE')", orderId, action, targetAccountId, assignedBy, reason);
    }

    public int revokeHandover(long orderId, long id, long actor, LocalDateTime revokedAt) {
        return jdbc.update("UPDATE order_action_handover SET status='REVOKED',revoked_by=?,revoked_at=?"
                + " WHERE id=? AND order_id=? AND status='ACTIVE'", actor, revokedAt, id, orderId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
