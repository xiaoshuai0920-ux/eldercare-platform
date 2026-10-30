package cn.eldercare.family;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.AuditService;
import cn.eldercare.common.SqlValues;
import cn.eldercare.identity.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class FamilyService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public FamilyService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    public record ElderView(Long id, String fullName, LocalDate birthDate) { }
    public record Permissions(boolean canViewService, boolean canBook, boolean canManageReminder,
                              boolean canCancel, boolean canConfirm, boolean canReview,
                              boolean canConfirmFee, boolean canManageFamily) { }

    @Transactional
    public ElderView createElder(long actorId, String name, LocalDate birthDate, boolean declaresAuthorization) {
        if (!declaresAuthorization) throw new ApiException(HttpStatus.BAD_REQUEST, "AUTHORIZATION_REQUIRED", "请先确认您有权创建该档案");
        String fullName = name.trim();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO elder_profile(full_name,birth_date,created_by,status) VALUES(?,?,?,'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, fullName);
            if (birthDate == null) statement.setNull(2, java.sql.Types.DATE);
            else statement.setDate(2, java.sql.Date.valueOf(birthDate));
            statement.setLong(3, actorId);
            return statement;
        }, key);
        long elderId = key.getKey().longValue();
        jdbc.update("INSERT INTO family_grant(elder_id,account_id,can_view_service,can_book,can_manage_reminder,can_cancel,can_confirm,can_review,can_confirm_fee,can_manage_family,status) VALUES(?,?,1,1,1,1,1,1,1,1,'ACTIVE')",
                elderId, actorId);
        audit(actorId, "ELDER_CREATE", "ELDER", elderId, "创建老人档案并声明管理授权");
        return new ElderView(elderId, fullName, birthDate);
    }

    public List<ElderView> listElders(long actorId) {
        return jdbc.query("SELECT e.id,e.full_name,e.birth_date FROM elder_profile e JOIN family_grant g ON g.elder_id=e.id WHERE g.account_id=? AND g.status='ACTIVE' AND g.can_view_service=1 AND e.status='ACTIVE' ORDER BY e.id DESC",
                (row, number) -> new ElderView(row.getLong("id"), row.getString("full_name"),
                        row.getDate("birth_date") == null ? null : row.getDate("birth_date").toLocalDate()), actorId);
    }

    public ElderView getElder(long actorId, long elderId) {
        requirePermission(actorId, elderId, PermissionColumn.VIEW_SERVICE);
        List<ElderView> items = jdbc.query("SELECT id,full_name,birth_date FROM elder_profile WHERE id=? AND status='ACTIVE'",
                (row, number) -> new ElderView(row.getLong("id"), row.getString("full_name"),
                        row.getDate("birth_date") == null ? null : row.getDate("birth_date").toLocalDate()), elderId);
        if (items.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "ELDER_NOT_FOUND", "档案不存在");
        return items.getFirst();
    }

    @Transactional
    public ElderView updateElder(long actorId, long elderId, String name, LocalDate birthDate) {
        requirePermission(actorId, elderId, PermissionColumn.MANAGE_FAMILY);
        String fullName = name.trim();
        jdbc.update("UPDATE elder_profile SET full_name=?,birth_date=? WHERE id=? AND status='ACTIVE'",
                fullName, birthDate, elderId);
        audit(actorId, "ELDER_UPDATE", "ELDER", elderId, "修改老人档案");
        return new ElderView(elderId, fullName, birthDate);
    }

    @Transactional
    public long invite(long actorId, long elderId, String inputUsername, Permissions permissions) {
        requirePermission(actorId, elderId, PermissionColumn.MANAGE_FAMILY);
        String username = AuthService.normalizeUsername(inputUsername);
        if (username.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVITEE_REQUIRED", "请填写家属账号");
        List<Map<String, Object>> invitee = jdbc.queryForList("SELECT id,email FROM account WHERE username=?", username);
        if (invitee.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "INVITEE_NOT_FOUND", "该账号不存在，请确认家属账号");
        long inviteeId = ((Number) invitee.getFirst().get("id")).longValue();
        if (inviteeId == actorId) throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_INVITATION", "不能邀请自己");
        String inviteeEmail = (String) invitee.getFirst().get("email");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO family_invitation(elder_id,inviter_id,invitee_account_id,invitee_email,can_view_service,can_book,can_manage_reminder,can_cancel,can_confirm,can_review,can_confirm_fee,can_manage_family,status,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, elderId);
            statement.setLong(2, actorId);
            statement.setLong(3, inviteeId);
            statement.setString(4, inviteeEmail);
            setPermissions(statement, 5, permissions);
            statement.setObject(13, now().plusDays(7));
            return statement;
        }, key);
        long id = key.getKey().longValue();
        audit(actorId, "FAMILY_INVITE", "INVITATION", id, "邀请家庭成员");
        return id;
    }

    public List<Map<String, Object>> pendingInvitations(long actorId) {
        return jdbc.queryForList("SELECT i.id,i.elder_id,e.full_name AS elder_name,i.expires_at,i.can_view_service,i.can_book,i.can_manage_reminder,i.can_cancel,i.can_confirm,i.can_review,i.can_confirm_fee,i.can_manage_family FROM family_invitation i JOIN elder_profile e ON e.id=i.elder_id JOIN family_grant inviter ON inviter.elder_id=i.elder_id AND inviter.account_id=i.inviter_id WHERE i.invitee_account_id=? AND i.status='PENDING' AND i.expires_at>? AND e.status='ACTIVE' AND inviter.status='ACTIVE' AND inviter.can_manage_family=1 ORDER BY i.id DESC",
                actorId, now());
    }

    @Transactional
    public void respond(long actorId, long invitationId, boolean accept) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM family_invitation WHERE id=? FOR UPDATE", invitationId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "邀请不存在");
        Map<String, Object> invitation = rows.getFirst();
        Object inviteeId = invitation.get("invitee_account_id");
        if (inviteeId == null || ((Number) inviteeId).longValue() != actorId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVITATION_FORBIDDEN", "无权处理该邀请");
        }
        if (!"PENDING".equals(invitation.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "INVITATION_CLOSED", "邀请已处理");
        if (SqlValues.localDateTime(invitation.get("expires_at")).isBefore(now())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITATION_EXPIRED", "邀请已过期");
        }
        long elderId = ((Number) invitation.get("elder_id")).longValue();
        if (accept) {
            jdbc.queryForList("SELECT id FROM family_grant WHERE elder_id=? AND account_id=? FOR UPDATE",elderId,invitation.get("inviter_id"));
            requirePermission(((Number)invitation.get("inviter_id")).longValue(),elderId,PermissionColumn.MANAGE_FAMILY);
            jdbc.update("INSERT INTO family_grant(elder_id,account_id,can_view_service,can_book,can_manage_reminder,can_cancel,can_confirm,can_review,can_confirm_fee,can_manage_family,status,revoked_at) VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE',NULL) ON DUPLICATE KEY UPDATE can_view_service=VALUES(can_view_service),can_book=VALUES(can_book),can_manage_reminder=VALUES(can_manage_reminder),can_cancel=VALUES(can_cancel),can_confirm=VALUES(can_confirm),can_review=VALUES(can_review),can_confirm_fee=VALUES(can_confirm_fee),can_manage_family=VALUES(can_manage_family),status='ACTIVE',revoked_at=NULL,granted_at=CURRENT_TIMESTAMP(3)",
                    elderId, actorId, invitation.get("can_view_service"), invitation.get("can_book"), invitation.get("can_manage_reminder"),
                    invitation.get("can_cancel"), invitation.get("can_confirm"), invitation.get("can_review"),
                    invitation.get("can_confirm_fee"), invitation.get("can_manage_family"));
        }
        jdbc.update("UPDATE family_invitation SET status=?,responded_at=? WHERE id=?", accept ? "ACCEPTED" : "REJECTED", now(), invitationId);
        audit(actorId, accept ? "FAMILY_ACCEPT" : "FAMILY_REJECT", "INVITATION", invitationId, accept ? "接受家庭邀请" : "拒绝家庭邀请");
    }

    public List<Map<String, Object>> grants(long actorId, long elderId) {
        requirePermission(actorId, elderId, PermissionColumn.MANAGE_FAMILY);
        return jdbc.queryForList("SELECT g.account_id,a.username,a.display_name,a.email,g.can_view_service,g.can_book,g.can_manage_reminder,g.can_cancel,g.can_confirm,g.can_review,g.can_confirm_fee,g.can_manage_family,g.status FROM family_grant g JOIN account a ON a.id=g.account_id WHERE g.elder_id=? ORDER BY g.id", elderId);
    }

    public Permissions permissions(long actorId, long elderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT g.can_view_service,g.can_book,g.can_manage_reminder,g.can_cancel,g.can_confirm,g.can_review,g.can_confirm_fee,g.can_manage_family FROM family_grant g JOIN elder_profile e ON e.id=g.elder_id WHERE g.elder_id=? AND g.account_id=? AND g.status='ACTIVE' AND e.status='ACTIVE'", elderId, actorId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "FAMILY_PERMISSION_REQUIRED", "当前没有此档案的有效授权");
        Map<String, Object> row = rows.getFirst();
        return new Permissions(enabled(row, "can_view_service"), enabled(row, "can_book"), enabled(row, "can_manage_reminder"),
                enabled(row, "can_cancel"), enabled(row, "can_confirm"), enabled(row, "can_review"),
                enabled(row, "can_confirm_fee"), enabled(row, "can_manage_family"));
    }

    private boolean enabled(Map<String, Object> row, String key) { return SqlValues.booleanValue(row.get(key)); }

    @Transactional
    public void revoke(long actorId, long elderId, long targetId) {
        requirePermission(actorId, elderId, PermissionColumn.MANAGE_FAMILY);
        if (actorId == targetId) {
            Integer managers = jdbc.queryForObject("SELECT COUNT(*) FROM family_grant WHERE elder_id=? AND status='ACTIVE' AND can_manage_family=1", Integer.class, elderId);
            if (managers != null && managers <= 1) throw new ApiException(HttpStatus.CONFLICT, "LAST_MANAGER", "最后一名家庭管理者不能自行撤销授权");
        }
        int changed = jdbc.update("UPDATE family_grant SET status='REVOKED',revoked_at=? WHERE elder_id=? AND account_id=? AND status='ACTIVE'", now(), elderId, targetId);
        if (changed == 0) throw new ApiException(HttpStatus.CONFLICT, "GRANT_NOT_ACTIVE", "有效授权不存在");
        audit(actorId, "FAMILY_REVOKE", "ELDER", elderId, "撤销家庭成员授权");
    }

    public void requirePermission(long actorId, long elderId, PermissionColumn permission) {
        String lock=TransactionSynchronizationManager.isActualTransactionActive()?" FOR UPDATE":"";
        List<Long> grants=jdbc.query("SELECT g.id FROM family_grant g JOIN elder_profile e ON e.id=g.elder_id WHERE g.account_id=? AND g.elder_id=? AND g.status='ACTIVE' AND e.status='ACTIVE' AND g."+permission.column()+"=1"+lock,(row,n)->row.getLong(1),actorId,elderId);
        if (grants.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "FAMILY_PERMISSION_REQUIRED", "当前没有此档案的有效授权");
    }

    private void setPermissions(PreparedStatement statement, int start, Permissions p) throws java.sql.SQLException {
        statement.setBoolean(start, p.canViewService());
        statement.setBoolean(start + 1, p.canBook());
        statement.setBoolean(start + 2, p.canManageReminder());
        statement.setBoolean(start + 3, p.canCancel());
        statement.setBoolean(start + 4, p.canConfirm());
        statement.setBoolean(start + 5, p.canReview());
        statement.setBoolean(start + 6, p.canConfirmFee());
        statement.setBoolean(start + 7, p.canManageFamily());
    }

    private LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private void audit(long actorId, String action, String type, long id, String summary) {
        audit.record(actorId, action, type, id, summary);
    }
}
