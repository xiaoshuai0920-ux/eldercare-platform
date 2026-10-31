package cn.eldercare.notifications;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 站内消息的数据访问层。
 *
 * <p>可见性规则按角色分成三档，由控制器挑档后传入，SQL 只在本类中出现一次。
 * 分类口径（提醒/家庭算「系统」，其余算「订单」）同样收在这里，避免与前端判断走偏。</p>
 */
@Repository
public class NotificationRepository {
    /** 管理员可看全部消息 */
    public static final String VISIBLE_ADMIN = "1=1";

    /** 服务人员只看与自己有效派单相关的订单事件 */
    public static final String VISIBLE_WORKER = "n.order_event_id IS NOT NULL AND EXISTS(SELECT 1 FROM order_event e"
            + " JOIN assignment x ON x.order_id=e.order_id WHERE e.id=n.order_event_id AND x.worker_id=n.account_id"
            + " AND x.status IN ('PENDING','ACCEPTED'))";

    /** 普通用户只看自己有查看授权的老人相关的订单事件或生活提醒 */
    public static final String VISIBLE_USER = "((n.order_event_id IS NOT NULL AND EXISTS(SELECT 1 FROM order_event e"
            + " JOIN service_order o ON o.id=e.order_id JOIN family_grant g ON g.elder_id=o.elder_id"
            + " WHERE e.id=n.order_event_id AND g.account_id=n.account_id AND g.status='ACTIVE' AND g.can_view_service=1))"
            + " OR (n.reminder_id IS NOT NULL AND EXISTS(SELECT 1 FROM life_reminder r"
            + " JOIN family_grant g ON g.elder_id=r.elder_id WHERE r.id=n.reminder_id AND g.account_id=n.account_id"
            + " AND g.status='ACTIVE' AND g.can_manage_reminder=1)))";

    private static final String KIND_SYSTEM = " AND (n.target_path LIKE '%/reminders%' OR n.target_path LIKE '%/family%')";
    private static final String KIND_ORDER =
            " AND NOT (n.target_path LIKE '%/reminders%' OR n.target_path LIKE '%/family%')";

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 分类筛选条件；kind 为空或未知时不过滤 */
    public String kindClause(String kind) {
        if ("system".equalsIgnoreCase(kind)) return KIND_SYSTEM;
        if ("order".equalsIgnoreCase(kind)) return KIND_ORDER;
        return "";
    }

    public long count(long accountId, String visibility, String kindClause) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notification n WHERE n.account_id=? AND "
                + visibility + kindClause, Long.class, accountId);
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> page(long accountId, String visibility, String kindClause, int size, int offset) {
        return jdbc.queryForList("SELECT n.id,n.title,n.body,n.target_path,n.priority,n.read_at,n.created_at"
                + " FROM notification n WHERE n.account_id=? AND " + visibility + kindClause
                + " ORDER BY n.id DESC LIMIT ? OFFSET ?", accountId, size, offset);
    }

    public boolean existsFor(long id, long accountId, String visibility) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM notification n WHERE n.id=? AND n.account_id=? AND "
                + visibility, Integer.class, id, accountId);
        return count != null && count > 0;
    }

    public void markRead(long id, long accountId) {
        jdbc.update("UPDATE notification SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) WHERE id=? AND account_id=?",
                id, accountId);
    }

    public long unreadCount(long accountId, String visibility, String kindClause) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM notification n WHERE n.account_id=? AND n.read_at IS NULL AND "
                + visibility + kindClause, Long.class, accountId);
        return count == null ? 0 : count;
    }
}
