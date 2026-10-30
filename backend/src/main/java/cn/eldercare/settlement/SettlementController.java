package cn.eldercare.settlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import cn.eldercare.family.PermissionColumn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/settlement")
public class SettlementController {
    private final SettlementRepository settlements;
    private final FamilyService family;
    private final OrderActionAccess actionAccess;
    private final PaymentQueryClient paymentClient;
    private final String paymentUrl;
    private final String serviceToken;
    private final boolean mockPaymentEnabled;

    public SettlementController(SettlementRepository settlements, FamilyService family, OrderActionAccess actionAccess,
                                PaymentQueryClient paymentClient,
                                @Value("${app.payment-base-url}") String paymentUrl,
                                @Value("${app.payment-service-token:}") String serviceToken,
                                @Value("${app.mock-payment.enabled:false}") boolean mockPaymentEnabled) {
        this.settlements = settlements;
        this.family = family;
        this.actionAccess = actionAccess;
        this.paymentClient = paymentClient;
        this.paymentUrl = paymentUrl;
        this.serviceToken = serviceToken;
        this.mockPaymentEnabled = mockPaymentEnabled;
    }

    public record View(Map<String, Object> settlement, List<Map<String, Object>> attempts) { }
    public record Attempt(String attemptRef, long orderRef, int settlementVersion, BigDecimal amount) { }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public View view(@PathVariable long orderId, Authentication auth) {
        requireRead(orderId, auth);
        Map<String, Object> settlement = settlement(orderId, false);
        return new View(settlement, settlements.attempts(((Number) settlement.get("id")).longValue()));
    }

    @PostMapping("/payment-attempts")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public Attempt attempt(@PathVariable long orderId, Authentication auth) {
        requireMockPaymentEnabled();
        Map<String, Object> order = order(orderId, true);
        long actor = Long.parseLong(auth.getName());
        actionAccess.require(actor, order, "PAYMENT");
        Map<String, Object> settlement = settlement(orderId, true);
        if (!List.of("WAITING_METHOD", "FAILED").contains(settlement.get("status"))) {
            throw conflict("SETTLEMENT_STATE_CONFLICT", "当前不可创建支付尝试");
        }
        long id = ((Number) settlement.get("id")).longValue();
        String ref = UUID.randomUUID().toString();
        int version = ((Number) settlement.get("version")).intValue();
        BigDecimal amount = (BigDecimal) settlement.get("amount");
        settlements.insertAttempt(id, ref, amount, version);
        settlements.markProcessing(id);
        settlements.event(id, actor, "CREATE_ATTEMPT", ref);
        return new Attempt(ref, orderId, version, amount);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Transactional
    public View sync(@PathVariable long orderId, Authentication auth) {
        requireMockPaymentEnabled();
        requireRead(orderId, auth);
        Map<String, Object> settlement = settlement(orderId, true);
        long id = ((Number) settlement.get("id")).longValue();
        Map<String, Object> attempt = settlements.latestAttemptForUpdate(id);
        if (attempt == null) throw conflict("NO_ATTEMPT", "尚未创建支付尝试");
        String ref = (String) attempt.get("attempt_ref");
        PaymentQueryClient.Remote remote = query(ref);
        if (remote == null) return view(orderId, auth);
        if (remote.orderRef() != orderId
                || remote.settlementVersion() != ((Number) attempt.get("settlement_version")).intValue()
                || remote.amount().compareTo((BigDecimal) attempt.get("amount")) != 0) {
            throw conflict("REMOTE_MISMATCH", "模拟交易的订单、版本或金额不匹配");
        }
        if (!List.of("PROCESSING", "SUCCESS", "FAILED", "CANCELLED").contains(remote.status())) {
            throw conflict("REMOTE_STATE_INVALID", "模拟交易状态无效");
        }
        if (remote.status().equals(attempt.get("remote_status")) && attempt.get("synced_at") != null) {
            return view(orderId, auth);
        }
        settlements.updateAttemptRemote(attempt.get("id"), remote.status(), LocalDateTime.now());
        if ("SETTLED".equals(settlement.get("status"))) return view(orderId, auth);
        if ("SUCCESS".equals(remote.status())) {
            settlements.settleSimulated(id, LocalDateTime.now());
            settlements.event(id, Long.parseLong(auth.getName()), "SIMULATED_SETTLED", ref);
        } else if (List.of("FAILED", "CANCELLED").contains(remote.status())) {
            settlements.markFailed(id);
            settlements.event(id, Long.parseLong(auth.getName()), "SIMULATED_TERMINAL", remote.status());
        }
        return view(orderId, auth);
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public View offline(@PathVariable long orderId, Authentication auth) {
        Map<String, Object> settlement = settlement(orderId, true);
        long id = ((Number) settlement.get("id")).longValue();
        if (!List.of("WAITING_METHOD", "FAILED").contains(settlement.get("status"))) {
            throw conflict("SETTLEMENT_STATE_CONFLICT", "当前不可登记线下结算");
        }
        if (settlements.unresolvedAttempts(id) > 0) {
            throw conflict("PAYMENT_ATTEMPT_UNRESOLVED", "存在未终结的模拟交易，不能登记线下结算");
        }
        settlements.settleOffline(id, LocalDateTime.now());
        settlements.event(id, Long.parseLong(auth.getName()), "OFFLINE_SETTLED", "管理员登记线下结算");
        return view(orderId, auth);
    }

    private PaymentQueryClient.Remote query(String ref) {
        if (serviceToken.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "模拟支付查询令牌未配置");
        }
        try {
            return paymentClient.query(paymentUrl, serviceToken, ref);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_QUERY_UNAVAILABLE", "模拟支付查询不可用");
        }
    }

    private void requireMockPaymentEnabled() {
        if (!mockPaymentEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MOCK_PAYMENT_DISABLED", "当前环境未启用本地模拟支付");
        }
    }

    private void requireRead(long orderId, Authentication auth) {
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return;
        Map<String, Object> order = order(orderId, false);
        family.requirePermission(Long.parseLong(auth.getName()),
                ((Number) order.get("elder_id")).longValue(), PermissionColumn.VIEW_SERVICE);
    }

    private Map<String, Object> order(long id, boolean lock) {
        Map<String, Object> order = settlements.order(id, lock);
        if (order == null) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        return order;
    }

    private Map<String, Object> settlement(long orderId, boolean lock) {
        Map<String, Object> settlement = settlements.settlement(orderId, lock);
        if (settlement == null) throw new ApiException(HttpStatus.NOT_FOUND, "SETTLEMENT_NOT_FOUND", "订单尚未进入结算");
        return settlement;
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
