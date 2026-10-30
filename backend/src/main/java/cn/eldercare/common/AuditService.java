package cn.eldercare.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 安全审计统一入口。
 *
 * <p>此前审计写入散落在各个 Service / Controller 里，同一条 INSERT 被复制了十余次，
 * 且登录、派单、费用裁决等高风险动作完全没有留痕。这里收敛为唯一入口，
 * 并统一处理两件容易被忽略的事：
 * <ul>
 *   <li>{@code summary} 列只有 500 字符，超限会直接让业务事务回滚，因此统一截断；</li>
 *   <li>登录失败等场景没有操作人，{@code actor_id} 允许为 NULL，但不能因此丢掉 IP。</li>
 * </ul>
 * 写入失败仍然向上抛出：审计缺失属于安全问题，静默吞掉比报错更危险。</p>
 */
@Service
public class AuditService {
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
    public static final String BLOCKED = "BLOCKED";

    private static final int MAX_SUMMARY = 500;
    private static final int MAX_IP = 45;
    private static final int MAX_ACTION = 80;
    private static final int MAX_TARGET_TYPE = 50;

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 记录一次成功操作 */
    public void record(Long actorId, String action, String targetType, Long targetId, String summary) {
        record(actorId, action, targetType, targetId, summary, SUCCESS, null);
    }

    /** 记录一次成功操作并附带来源 IP（登录等场景） */
    public void success(Long actorId, String action, String targetType, Long targetId, String summary, String ip) {
        record(actorId, action, targetType, targetId, summary, SUCCESS, ip);
    }

    /** 记录一次失败操作（如登录失败、被限流拦截） */
    public void failure(Long actorId, String action, String targetType, Long targetId, String summary, String ip) {
        record(actorId, action, targetType, targetId, summary, FAILURE, ip);
    }

    /** 记录一次被拦截的操作（如限流、账号锁定） */
    public void blocked(Long actorId, String action, String targetType, Long targetId, String summary, String ip) {
        record(actorId, action, targetType, targetId, summary, BLOCKED, ip);
    }

    public void record(Long actorId, String action, String targetType, Long targetId, String summary,
                       String result, String ip) {
        jdbc.update("INSERT INTO audit_event(actor_id,ip,action,result,target_type,target_id,summary) VALUES(?,?,?,?,?,?,?)",
                actorId,
                trim(ip, MAX_IP),
                trim(action, MAX_ACTION),
                result == null ? SUCCESS : result,
                trim(targetType, MAX_TARGET_TYPE),
                targetId,
                trim(summary == null || summary.isBlank() ? "-" : summary.trim(), MAX_SUMMARY));
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
