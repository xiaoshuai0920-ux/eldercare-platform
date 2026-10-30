package cn.eldercare.family;

import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderActionAccess {
    private final JdbcTemplate jdbc;
    private final FamilyService family;

    public OrderActionAccess(JdbcTemplate jdbc, FamilyService family) {
        this.jdbc = jdbc;
        this.family = family;
    }

    public void require(long actorId, Map<String, Object> order, String action) {
        PermissionColumn permission = permission(action);
        long elderId = ((Number) order.get("elder_id")).longValue();
        family.requirePermission(actorId, elderId, PermissionColumn.VIEW_SERVICE);
        family.requirePermission(actorId, elderId, permission);
        long creatorId = ((Number) order.get("creator_id")).longValue();
        if (actorId == creatorId) return;
        Integer verified = jdbc.queryForObject("SELECT COUNT(*) FROM elder_identity_claim WHERE elder_id=? AND account_id=? AND status='APPROVED'", Integer.class, elderId, actorId);
        if (verified != null && verified > 0) return;
        List<Long> handovers = jdbc.query("SELECT id FROM order_action_handover WHERE order_id=? AND action=? AND target_account_id=? AND status='ACTIVE'", (row, index) -> row.getLong(1), order.get("id"), action, actorId);
        if (handovers.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_ACTION_FORBIDDEN", "当前没有此订单操作的有效授权");
    }

    public PermissionColumn permission(String action) {
        return switch (action) {
            case "CANCEL" -> PermissionColumn.CANCEL;
            case "CONFIRM" -> PermissionColumn.CONFIRM;
            case "REVIEW" -> PermissionColumn.REVIEW;
            case "FEE_CONFIRM" -> PermissionColumn.CONFIRM_FEE;
            case "DISPUTE", "PAYMENT" -> PermissionColumn.VIEW_SERVICE;
            default -> throw new IllegalArgumentException("未知订单操作");
        };
    }
}
