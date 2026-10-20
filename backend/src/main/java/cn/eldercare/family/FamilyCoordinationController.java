package cn.eldercare.family;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FamilyCoordinationController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final FamilyService family;
    private final OrderActionAccess access;

    public FamilyCoordinationController(JdbcTemplate jdbc, FamilyService family, OrderActionAccess access) {
        this.jdbc = jdbc;
        this.family = family;
        this.access = access;
    }

    public record Reason(@NotBlank @Size(max = 500) String reason) { }
    public record Handover(@NotBlank String action, @Positive long targetAccountId,
                           @NotBlank @Size(max = 500) String reason) { }

    @PostMapping("/elders/{elderId}/identity-claims")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void claim(@PathVariable long elderId, Authentication auth) {
        long actor = actor(auth);
        family.requirePermission(actor, elderId, "can_view_service");
        jdbc.queryForList("SELECT id FROM elder_profile WHERE id=? FOR UPDATE", elderId);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM elder_identity_claim WHERE account_id=? AND status IN ('PENDING','APPROVED')", Integer.class, actor);
        if (existing != null && existing > 0) throw conflict("IDENTITY_CLAIM_EXISTS", "已有待核验或已核验的本人关联");
        existing = jdbc.queryForObject("SELECT COUNT(*) FROM elder_identity_claim WHERE elder_id=? AND status IN ('PENDING','APPROVED')", Integer.class, elderId);
        if (existing != null && existing > 0) throw conflict("ELDER_CLAIM_EXISTS", "该档案已有待核验或已核验的本人关联");
        jdbc.update("INSERT INTO elder_identity_claim(elder_id,account_id,status) VALUES(?,?,'PENDING')", elderId, actor);
        audit(actor, "ELDER_IDENTITY_CLAIM", "ELDER", elderId, "提交老人本人关联申请，等待管理员线下核验");
    }

    @GetMapping("/elders/{elderId}/identity-claims")
    @PreAuthorize("hasRole('USER')")
    public List<Map<String, Object>> ownClaims(@PathVariable long elderId, Authentication auth) {
        family.requirePermission(actor(auth), elderId, "can_view_service");
        return jdbc.queryForList("SELECT id,status,requested_at,review_note,reviewed_at FROM elder_identity_claim WHERE elder_id=? AND account_id=? ORDER BY id DESC", elderId, actor(auth));
    }

    @GetMapping("/admin/identity-claims")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> claims() {
        return jdbc.queryForList("SELECT c.id,c.elder_id,e.full_name AS elder_name,c.account_id,a.display_name AS account_name,c.status,c.requested_at,c.review_note,c.reviewed_at FROM elder_identity_claim c JOIN elder_profile e ON e.id=c.elder_id JOIN account a ON a.id=c.account_id ORDER BY c.id DESC LIMIT 100");
    }

    @PostMapping("/admin/identity-claims/{id}/{decision:approve|reject|revoke}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void decideClaim(@PathVariable long id, @PathVariable String decision, @Valid @RequestBody Reason input, Authentication auth) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM elder_identity_claim WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "IDENTITY_CLAIM_NOT_FOUND", "本人关联申请不存在");
        Map<String, Object> claim = rows.getFirst();
        String required = "revoke".equals(decision) ? "APPROVED" : "PENDING";
        if (!required.equals(claim.get("status"))) throw conflict("IDENTITY_CLAIM_STATE", "本人关联申请状态已变化");
        long elderId = ((Number) claim.get("elder_id")).longValue();
        long accountId = ((Number) claim.get("account_id")).longValue();
        if ("approve".equals(decision)) {
            family.requirePermission(accountId, elderId, "can_view_service");
            List<Map<String, Object>> users = jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='USER' AND status='ACTIVE'", accountId);
            if (users.isEmpty()) throw conflict("IDENTITY_ACCOUNT_INACTIVE", "账号已停用或非普通用户");
        }
        String status = switch (decision) { case "approve" -> "APPROVED"; case "reject" -> "REJECTED"; default -> "REVOKED"; };
        try {
            jdbc.update("UPDATE elder_identity_claim SET status=?,reviewed_by=?,review_note=?,reviewed_at=? WHERE id=?", status, actor(auth), input.reason().trim(), now(), id);
        } catch (DuplicateKeyException duplicate) {
            throw conflict("IDENTITY_ALREADY_VERIFIED", "该账号或档案已有有效本人关联");
        }
        audit(actor(auth), "ELDER_IDENTITY_" + status, "IDENTITY_CLAIM", id, input.reason().trim());
    }

    @PostMapping("/elders/{elderId}/family-disputes")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void openDispute(@PathVariable long elderId, @Valid @RequestBody Reason input, Authentication auth) {
        long actor = actor(auth);
        Integer known = jdbc.queryForObject("SELECT COUNT(*) FROM family_grant WHERE elder_id=? AND account_id=?", Integer.class, elderId, actor);
        if (known == null || known == 0) throw new ApiException(HttpStatus.FORBIDDEN, "FAMILY_RELATION_REQUIRED", "无权对该档案提交争议");
        jdbc.update("INSERT INTO family_dispute(elder_id,reporter_id,reason,status) VALUES(?,?,?,'OPEN')", elderId, actor, input.reason().trim());
        audit(actor, "FAMILY_DISPUTE_OPEN", "ELDER", elderId, "提交家庭关联争议");
    }

    @GetMapping("/elders/{elderId}/family-disputes")
    @PreAuthorize("hasRole('USER')")
    public List<Map<String, Object>> ownDisputes(@PathVariable long elderId, Authentication auth) {
        return jdbc.queryForList("SELECT id,reason,status,reported_at,resolution,resolved_at FROM family_dispute WHERE elder_id=? AND reporter_id=? ORDER BY id DESC", elderId, actor(auth));
    }

    @GetMapping("/admin/family-disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> disputes() {
        return jdbc.queryForList("SELECT d.id,d.elder_id,e.full_name AS elder_name,d.reporter_id,a.display_name AS reporter_name,d.reason,d.status,d.reported_at,d.resolution,d.resolved_at FROM family_dispute d JOIN elder_profile e ON e.id=d.elder_id JOIN account a ON a.id=d.reporter_id ORDER BY d.id DESC LIMIT 100");
    }

    @PostMapping("/admin/family-disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resolveDispute(@PathVariable long id, @Valid @RequestBody Reason input, Authentication auth) {
        int changed = jdbc.update("UPDATE family_dispute SET status='RESOLVED',resolved_by=?,resolution=?,resolved_at=? WHERE id=? AND status='OPEN'", actor(auth), input.reason().trim(), now(), id);
        if (changed != 1) throw conflict("FAMILY_DISPUTE_STATE", "争议不存在或已处理");
        audit(actor(auth), "FAMILY_DISPUTE_RESOLVE", "FAMILY_DISPUTE", id, input.reason().trim());
    }

    @GetMapping("/admin/orders/{orderId}/handovers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> handovers(@PathVariable long orderId) {
        return jdbc.queryForList("SELECT h.id,h.action,h.target_account_id,a.display_name AS target_name,h.reason,h.status,h.assigned_at,h.revoked_at FROM order_action_handover h JOIN account a ON a.id=h.target_account_id WHERE h.order_id=? ORDER BY h.id DESC", orderId);
    }

    @PostMapping("/admin/orders/{orderId}/handovers")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void handover(@PathVariable long orderId, @Valid @RequestBody Handover input, Authentication auth) {
        String action = input.action().trim().toUpperCase(java.util.Locale.ROOT);
        String permission;
        try { permission = access.permission(action); }
        catch (IllegalArgumentException invalid) { throw new ApiException(HttpStatus.BAD_REQUEST, "HANDOVER_ACTION_INVALID", "未知订单操作"); }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,creator_id,elder_id,status FROM service_order WHERE id=? FOR UPDATE", orderId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        Map<String, Object> order = rows.getFirst();
        long elderId = ((Number) order.get("elder_id")).longValue();
        long creatorId = ((Number) order.get("creator_id")).longValue();
        if (creatorId == input.targetAccountId()) throw conflict("HANDOVER_TARGET_INVALID", "创建人无需交接");
        if (!applicable(action, (String) order.get("status"))) throw conflict("HANDOVER_STATE_INVALID", "当前订单状态不适用此交接操作");
        Integer creatorActive = jdbc.queryForObject("SELECT COUNT(*) FROM account a JOIN family_grant g ON g.account_id=a.id WHERE a.id=? AND a.status='ACTIVE' AND g.elder_id=? AND g.status='ACTIVE' AND g.can_view_service=1 AND g." + permission + "=1", Integer.class, creatorId, elderId);
        if (creatorActive != null && creatorActive > 0) throw conflict("HANDOVER_NOT_NEEDED", "创建人仍有当前操作权限，无需交接");
        List<Map<String, Object>> target = jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='USER' AND status='ACTIVE'", input.targetAccountId());
        if (target.isEmpty()) throw conflict("HANDOVER_TARGET_INVALID", "接收人账号不可用");
        family.requirePermission(input.targetAccountId(), elderId, "can_view_service");
        family.requirePermission(input.targetAccountId(), elderId, permission);
        jdbc.update("UPDATE order_action_handover SET status='REVOKED',revoked_by=?,revoked_at=? WHERE order_id=? AND action=? AND status='ACTIVE'", actor(auth), now(), orderId, action);
        jdbc.update("INSERT INTO order_action_handover(order_id,action,target_account_id,assigned_by,reason,status) VALUES(?,?,?,?,?,'ACTIVE')", orderId, action, input.targetAccountId(), actor(auth), input.reason().trim());
        audit(actor(auth), "ORDER_ACTION_HANDOVER", "ORDER", orderId, action + " 指定接收人 #" + input.targetAccountId() + "；" + input.reason().trim());
    }

    @PostMapping("/admin/orders/{orderId}/handovers/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void revokeHandover(@PathVariable long orderId, @PathVariable long id, Authentication auth) {
        int changed = jdbc.update("UPDATE order_action_handover SET status='REVOKED',revoked_by=?,revoked_at=? WHERE id=? AND order_id=? AND status='ACTIVE'", actor(auth), now(), id, orderId);
        if (changed != 1) throw conflict("HANDOVER_STATE", "交接不存在或已撤销");
        audit(actor(auth), "ORDER_ACTION_HANDOVER_REVOKE", "ORDER", orderId, "撤销指定操作交接 #" + id);
    }

    private boolean applicable(String action, String status) {
        return switch (action) {
            case "CANCEL" -> List.of("WAITING_ASSIGNMENT", "WAITING_ACCEPTANCE", "WAITING_SERVICE").contains(status);
            case "CONFIRM" -> "WAITING_CONFIRMATION".equals(status);
            case "REVIEW", "DISPUTE" -> "COMPLETED".equals(status);
            case "FEE_CONFIRM" -> "INTERRUPTED".equals(status);
            case "PAYMENT" -> List.of("COMPLETED", "ABNORMAL_CLOSED").contains(status);
            default -> false;
        };
    }

    private long actor(Authentication auth) { return Long.parseLong(auth.getName()); }
    private LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private void audit(long actor, String action, String type, long id, String summary) {
        jdbc.update("INSERT INTO audit_event(actor_id,action,target_type,target_id,summary) VALUES(?,?,?,?,?)", actor, action, type, id, summary);
    }
}
