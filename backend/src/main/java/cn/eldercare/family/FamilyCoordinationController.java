package cn.eldercare.family;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.AuditService;
import cn.eldercare.common.PageQuery;
import cn.eldercare.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FamilyCoordinationController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final FamilyCoordinationRepository coordination;
    private final FamilyService family;
    private final OrderActionAccess access;
    private final AuditService audit;

    public FamilyCoordinationController(FamilyCoordinationRepository coordination, FamilyService family,
                                        OrderActionAccess access, AuditService audit) {
        this.coordination = coordination;
        this.family = family;
        this.access = access;
        this.audit = audit;
    }

    public record Reason(@NotBlank @Size(max = 500) String reason) { }
    public record Handover(@NotBlank String action, @Positive long targetAccountId,
                           @NotBlank @Size(max = 500) String reason) { }

    @PostMapping("/elders/{elderId}/identity-claims")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void claim(@PathVariable long elderId, Authentication auth) {
        long actor = actor(auth);
        family.requirePermission(actor, elderId, PermissionColumn.VIEW_SERVICE);
        if (!coordination.elderExistsForUpdate(elderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ELDER_NOT_FOUND", "老人档案不存在");
        }
        if (coordination.hasActiveClaimForAccount(actor)) throw conflict("IDENTITY_CLAIM_EXISTS", "已有待核验或已核验的本人关联");
        if (coordination.hasActiveClaimForElder(elderId)) throw conflict("ELDER_CLAIM_EXISTS", "该档案已有待核验或已核验的本人关联");
        coordination.insertClaim(elderId, actor);
        audit(actor, "ELDER_IDENTITY_CLAIM", "ELDER", elderId, "提交老人本人关联申请，等待管理员线下核验");
    }

    @GetMapping("/elders/{elderId}/identity-claims")
    @PreAuthorize("hasRole('USER')")
    public List<Map<String, Object>> ownClaims(@PathVariable long elderId, Authentication auth) {
        family.requirePermission(actor(auth), elderId, PermissionColumn.VIEW_SERVICE);
        return coordination.ownClaims(elderId, actor(auth));
    }

    @GetMapping("/admin/identity-claims")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<Map<String, Object>> claims(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        PageQuery query = PageQuery.of(page, size);
        return PageResult.of(coordination.claimsPage(query.size(), query.offset()), query, coordination.claimsCount());
    }

    @PostMapping("/admin/identity-claims/{id}/{decision:approve|reject|revoke}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void decideClaim(@PathVariable long id, @PathVariable String decision, @Valid @RequestBody Reason input,
                            Authentication auth) {
        Map<String, Object> claim = coordination.claimForUpdate(id);
        if (claim == null) throw new ApiException(HttpStatus.NOT_FOUND, "IDENTITY_CLAIM_NOT_FOUND", "本人关联申请不存在");
        String required = "revoke".equals(decision) ? "APPROVED" : "PENDING";
        if (!required.equals(claim.get("status"))) throw conflict("IDENTITY_CLAIM_STATE", "本人关联申请状态已变化");
        long elderId = ((Number) claim.get("elder_id")).longValue();
        long accountId = ((Number) claim.get("account_id")).longValue();
        if ("approve".equals(decision)) {
            family.requirePermission(accountId, elderId, PermissionColumn.VIEW_SERVICE);
            if (!coordination.isActiveUser(accountId)) throw conflict("IDENTITY_ACCOUNT_INACTIVE", "账号已停用或非普通用户");
        }
        String status = switch (decision) { case "approve" -> "APPROVED"; case "reject" -> "REJECTED"; default -> "REVOKED"; };
        try {
            coordination.decideClaim(id, status, actor(auth), input.reason().trim(), now());
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
        if (!coordination.hasGrant(elderId, actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FAMILY_RELATION_REQUIRED", "无权对该档案提交争议");
        }
        coordination.openDispute(elderId, actor, input.reason().trim());
        audit(actor, "FAMILY_DISPUTE_OPEN", "ELDER", elderId, "提交家庭关联争议");
    }

    @GetMapping("/elders/{elderId}/family-disputes")
    @PreAuthorize("hasRole('USER')")
    public List<Map<String, Object>> ownDisputes(@PathVariable long elderId, Authentication auth) {
        return coordination.ownDisputes(elderId, actor(auth));
    }

    @GetMapping("/admin/family-disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<Map<String, Object>> disputes(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        PageQuery query = PageQuery.of(page, size);
        return PageResult.of(coordination.disputesPage(query.size(), query.offset()), query, coordination.disputesCount());
    }

    @PostMapping("/admin/family-disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resolveDispute(@PathVariable long id, @Valid @RequestBody Reason input, Authentication auth) {
        if (coordination.resolveDispute(id, actor(auth), input.reason().trim(), now()) != 1) {
            throw conflict("FAMILY_DISPUTE_STATE", "争议不存在或已处理");
        }
        audit(actor(auth), "FAMILY_DISPUTE_RESOLVE", "FAMILY_DISPUTE", id, input.reason().trim());
    }

    @GetMapping("/admin/orders/{orderId}/handovers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> handovers(@PathVariable long orderId) {
        return coordination.handovers(orderId);
    }

    @GetMapping("/admin/orders/{orderId}/handover-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> handoverCandidates(@PathVariable long orderId, @RequestParam String action) {
        PermissionColumn permission;
        try { permission = access.permission(action.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new ApiException(HttpStatus.BAD_REQUEST, "HANDOVER_ACTION_INVALID", "未知订单操作"); }
        return coordination.handoverCandidates(orderId, permission);
    }

    @PostMapping("/admin/orders/{orderId}/handovers")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void handover(@PathVariable long orderId, @Valid @RequestBody Handover input, Authentication auth) {
        String action = input.action().trim().toUpperCase(Locale.ROOT);
        PermissionColumn permission;
        try { permission = access.permission(action); }
        catch (IllegalArgumentException invalid) { throw new ApiException(HttpStatus.BAD_REQUEST, "HANDOVER_ACTION_INVALID", "未知订单操作"); }
        Map<String, Object> order = coordination.orderForUpdate(orderId);
        if (order == null) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        long elderId = ((Number) order.get("elder_id")).longValue();
        long creatorId = ((Number) order.get("creator_id")).longValue();
        if (creatorId == input.targetAccountId()) throw conflict("HANDOVER_TARGET_INVALID", "创建人无需交接");
        if (!applicable(action, (String) order.get("status"))) throw conflict("HANDOVER_STATE_INVALID", "当前订单状态不适用此交接操作");
        if (coordination.creatorHolds(permission, creatorId, elderId)) throw conflict("HANDOVER_NOT_NEEDED", "创建人仍有当前操作权限，无需交接");
        if (!coordination.isActiveUser(input.targetAccountId())) throw conflict("HANDOVER_TARGET_INVALID", "接收人账号不可用");
        family.requirePermission(input.targetAccountId(), elderId, PermissionColumn.VIEW_SERVICE);
        family.requirePermission(input.targetAccountId(), elderId, permission);
        coordination.revokeHandovers(orderId, action, actor(auth), now());
        coordination.insertHandover(orderId, action, input.targetAccountId(), actor(auth), input.reason().trim());
        audit(actor(auth), "ORDER_ACTION_HANDOVER", "ORDER", orderId,
                action + " 指定接收人 #" + input.targetAccountId() + "；" + input.reason().trim());
    }

    @PostMapping("/admin/orders/{orderId}/handovers/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void revokeHandover(@PathVariable long orderId, @PathVariable long id, Authentication auth) {
        if (coordination.revokeHandover(orderId, id, actor(auth), now()) != 1) {
            throw conflict("HANDOVER_STATE", "交接不存在或已撤销");
        }
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
        audit.record(actor, action, type, id, summary);
    }
}
