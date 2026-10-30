package cn.eldercare.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.AuditService;
import cn.eldercare.common.PageQuery;
import cn.eldercare.common.PageResult;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import cn.eldercare.family.PermissionColumn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class IncidentController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final IncidentRepository incidents;
    private final FamilyService family;
    private final OrderActionAccess actionAccess;
    private final AuditService audit;

    public IncidentController(IncidentRepository incidents, FamilyService family,
                              OrderActionAccess actionAccess, AuditService audit) {
        this.incidents = incidents;
        this.family = family;
        this.actionAccess = actionAccess;
        this.audit = audit;
    }

    public record Reason(@NotBlank @Size(max = 500) String reason) { }
    public record FeeProposal(@NotNull @DecimalMin("0.00") BigDecimal amount, @NotBlank @Size(max = 500) String reason) { }
    public record SupplementRequest(@NotNull LocalDateTime startAt, @NotBlank @Size(max = 500) String reason) { }

    @PostMapping("/orders/{orderId}/interrupt")
    @PreAuthorize("hasRole('WORKER')")
    @Transactional
    public void interrupt(@PathVariable long orderId, @Valid @RequestBody Reason input, Authentication auth) {
        interruptInternal(orderId, Long.parseLong(auth.getName()), input.reason(), false);
    }

    @PostMapping("/admin/orders/{orderId}/interrupt")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void adminInterrupt(@PathVariable long orderId, @Valid @RequestBody Reason input, Authentication auth) {
        interruptInternal(orderId, Long.parseLong(auth.getName()), input.reason(), true);
    }

    private void interruptInternal(long orderId, long actor, String reason, boolean admin) {
        Map<String, Object> order = order(orderId, true);
        if (!"IN_SERVICE".equals(order.get("status"))) throw conflict("INTERRUPT_STATE_CONFLICT", "只有服务中订单可报告中断");
        List<Map<String, Object>> assignments = incidents.acceptedAssignmentsForUpdate(orderId, admin ? null : actor);
        if (assignments.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "ASSIGNMENT_FORBIDDEN", "当前没有有效的本人派单");
        long assignmentId = ((Number) assignments.getFirst().get("id")).longValue();
        incidents.reportIncident(orderId, actor, reason.trim());
        incidents.interruptFulfillment(orderId, assignmentId);
        incidents.releaseOccupancy(assignmentId);
        incidents.releaseAssignment(assignmentId);
        incidents.interruptSupplement(orderId);
        transition(order, "INTERRUPTED");
        incidents.event(orderId, actor, "INTERRUPT", "IN_SERVICE", "INTERRUPTED", reason.trim());
        audit.record(actor, admin ? "ADMIN_ORDER_INTERRUPT" : "ORDER_INTERRUPT", "ORDER", orderId,
                (admin ? "管理员强制中断服务：" : "服务人员报告服务中断：") + reason.trim());
    }

    @GetMapping("/admin/incidents")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<Map<String, Object>> incidents(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        PageQuery query = PageQuery.of(page, size);
        return PageResult.of(this.incidents.incidentPage(query.size(), query.offset()), query, this.incidents.incidentCount());
    }

    @PostMapping("/admin/incidents/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resolveIncident(@PathVariable long id, @Valid @RequestBody Reason input, Authentication auth) {
        Map<String, Object> incident = incidents.incidentForUpdate(id);
        if (incident == null || !"OPEN".equals(incident.get("status"))) {
            throw conflict("INCIDENT_STATE_CONFLICT", "服务中断记录不存在或已处理");
        }
        long admin = Long.parseLong(auth.getName());
        if (incidents.resolveIncident(id, admin, input.reason().trim(), LocalDateTime.now()) != 1) {
            throw conflict("INCIDENT_STATE_CONFLICT", "服务中断记录不存在或已处理");
        }
        audit.record(admin, "SERVICE_INCIDENT_RESOLVE", "SERVICE_INCIDENT", id,
                "管理员记录服务中断处理结果：" + input.reason().trim());
    }

    @PostMapping("/admin/orders/{orderId}/supplements")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void scheduleSupplement(@PathVariable long orderId, @Valid @RequestBody SupplementRequest input, Authentication auth) {
        Map<String, Object> order = order(orderId, true);
        if (!"INTERRUPTED".equals(order.get("status"))) throw conflict("SUPPLEMENT_STATE_CONFLICT", "只有中断订单可安排补服务");
        int duration = ((Number) order.get("duration_minutes")).intValue();
        LocalDateTime endAt = input.startAt().plusMinutes(duration);
        if (!input.startAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))
                || !input.startAt().toLocalDate().equals(endAt.toLocalDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLEMENT_TIME_INVALID", "补服务须安排在未来且不能跨日");
        }
        long admin = Long.parseLong(auth.getName());
        incidents.planSupplement(orderId, admin, input.reason().trim(), input.startAt(), endAt);
        transition(order, "WAITING_ASSIGNMENT");
        incidents.event(orderId, admin, "PLAN_SUPPLEMENT", "INTERRUPTED", "WAITING_ASSIGNMENT", "已安排独立补服务，等待人工派单");
        audit.record(admin, "ORDER_SUPPLEMENT_PLAN", "ORDER", orderId,
                "安排补服务：" + input.startAt() + " 至 " + endAt + "；" + input.reason().trim());
    }

    @GetMapping("/orders/{orderId}/supplements")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<Map<String, Object>> supplements(@PathVariable long orderId, Authentication auth) {
        Map<String, Object> order = order(orderId, false);
        if (!isAdmin(auth)) {
            family.requirePermission(Long.parseLong(auth.getName()),
                    ((Number) order.get("elder_id")).longValue(), PermissionColumn.VIEW_SERVICE);
        }
        return incidents.supplements(orderId);
    }

    @PostMapping("/orders/{orderId}/fee-adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void proposeFee(@PathVariable long orderId, @Valid @RequestBody FeeProposal input, Authentication auth) {
        Map<String, Object> order = order(orderId, true);
        if (!"INTERRUPTED".equals(order.get("status"))) throw conflict("FEE_STATE_CONFLICT", "仅中断订单可按此流程核实费用");
        if (incidents.pendingFeeProposals(orderId) > 0) throw conflict("FEE_PROPOSAL_PENDING", "已有待确认费用提案");
        long admin = Long.parseLong(auth.getName());
        incidents.proposeFee(orderId, admin, order.get("payable_amount"), input.amount(), input.reason().trim());
        incidents.event(orderId, admin, "PROPOSE_FEE", "INTERRUPTED", "INTERRUPTED", "管理员提出异常费用，待有权用户确认");
        audit.record(admin, "ORDER_FEE_PROPOSE", "ORDER", orderId,
                "异常费用核实：原 " + order.get("payable_amount") + " 拟改 " + input.amount() + "；" + input.reason().trim());
    }

    @GetMapping("/orders/{orderId}/fee-adjustments")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<Map<String, Object>> fees(@PathVariable long orderId, Authentication auth) {
        Map<String, Object> order = order(orderId, false);
        if (!isAdmin(auth)) {
            family.requirePermission(Long.parseLong(auth.getName()),
                    ((Number) order.get("elder_id")).longValue(), PermissionColumn.VIEW_SERVICE);
        }
        return incidents.fees(orderId);
    }

    @PostMapping("/orders/{orderId}/fee-adjustments/{id}/approve")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void approveFee(@PathVariable long orderId, @PathVariable long id, Authentication auth) {
        decideFee(orderId, id, auth, true);
    }

    @PostMapping("/orders/{orderId}/fee-adjustments/{id}/reject")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void rejectFee(@PathVariable long orderId, @PathVariable long id, Authentication auth) {
        decideFee(orderId, id, auth, false);
    }

    private void decideFee(long orderId, long id, Authentication auth, boolean approve) {
        Map<String, Object> order = order(orderId, true);
        long actor = Long.parseLong(auth.getName());
        actionAccess.require(actor, order, "FEE_CONFIRM");
        if (!"INTERRUPTED".equals(order.get("status"))) throw conflict("FEE_STATE_CONFLICT", "订单不在异常费用待核实阶段");
        Map<String, Object> fee = incidents.feeForUpdate(id, orderId);
        if (fee == null || !"PENDING".equals(fee.get("status"))) throw conflict("FEE_PROPOSAL_STATE_CONFLICT", "费用提案不存在或已处理");
        incidents.decideFee(id, actor, approve, LocalDateTime.now());
        audit.record(actor, approve ? "ORDER_FEE_APPROVE" : "ORDER_FEE_REJECT", "ORDER", orderId,
                (approve ? "确认" : "拒绝") + "异常费用提案 #" + id + "（金额 " + fee.get("proposed_amount") + "）");
        if (!approve) {
            incidents.event(orderId, actor, "REJECT_FEE", "INTERRUPTED", "INTERRUPTED", "用户拒绝费用提案，继续异常协调");
            return;
        }
        BigDecimal amount = (BigDecimal) fee.get("proposed_amount");
        incidents.updatePayable(orderId, amount);
        transition(order, "ABNORMAL_CLOSED");
        incidents.resolveIncident(orderId, fee.get("proposed_by"), "用户已确认异常结案费用", LocalDateTime.now());
        if (amount.signum() > 0) incidents.createSettlement(orderId, amount);
        else incidents.createWaivedSettlement(orderId, LocalDateTime.now());
        incidents.event(orderId, actor, "APPROVE_FEE", "INTERRUPTED", "ABNORMAL_CLOSED", "有权用户确认异常结案费用");
    }

    @PostMapping("/orders/{orderId}/disputes")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void dispute(@PathVariable long orderId, @Valid @RequestBody Reason input, Authentication auth) {
        Map<String, Object> order = order(orderId, true);
        long actor = Long.parseLong(auth.getName());
        actionAccess.require(actor, order, "DISPUTE");
        if (!"COMPLETED".equals(order.get("status"))) throw conflict("DISPUTE_STATE_CONFLICT", "仅正常完成的订单可发起事后争议");
        if (incidents.openDisputes(orderId) > 0) throw conflict("DISPUTE_EXISTS", "已有待处理争议");
        incidents.openDispute(orderId, actor, input.reason().trim());
        incidents.event(orderId, actor, "OPEN_DISPUTE", "COMPLETED", "COMPLETED", "完成后争议已登记");
        audit.record(actor, "ORDER_DISPUTE_OPEN", "ORDER", orderId, "发起事后争议：" + input.reason().trim());
    }

    @GetMapping("/admin/disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<Map<String, Object>> disputes(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        PageQuery query = PageQuery.of(page, size);
        return PageResult.of(incidents.disputePage(query.size(), query.offset()), query, incidents.disputeCount());
    }

    @PostMapping("/admin/disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resolve(@PathVariable long id, @Valid @RequestBody Reason input, Authentication auth) {
        Map<String, Object> dispute = incidents.disputeForUpdate(id);
        if (dispute == null || !"OPEN".equals(dispute.get("status"))) throw conflict("DISPUTE_STATE_CONFLICT", "争议不存在或已处理");
        long admin = Long.parseLong(auth.getName());
        incidents.resolveDispute(id, admin, input.reason().trim(), LocalDateTime.now());
        audit.record(admin, "ORDER_DISPUTE_RESOLVE", "ORDER_DISPUTE", id, "管理员处理争议：" + input.reason().trim());
    }

    private Map<String, Object> order(long id, boolean locked) {
        Map<String, Object> order = incidents.order(id, locked);
        if (order == null) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        return order;
    }

    private void transition(Map<String, Object> order, String next) {
        if (incidents.transition(order.get("id"), order.get("version"), (String) order.get("status"), next) != 1) {
            throw conflict("ORDER_STATE_CONFLICT", "订单状态已变化");
        }
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
