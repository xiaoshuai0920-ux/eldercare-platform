package cn.eldercare.notifications;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 生活提醒的数据访问层。 */
@Repository
public class ReminderRepository {
    private final JdbcTemplate jdbc;

    public ReminderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> list(long accountId) {
        return jdbc.queryForList("SELECT r.id,r.elder_id,e.full_name AS elder_name,r.title,r.recurrence,"
                + "r.next_fire_at,r.status FROM life_reminder r JOIN elder_profile e ON e.id=r.elder_id"
                + " JOIN family_grant g ON g.elder_id=r.elder_id WHERE g.account_id=? AND g.status='ACTIVE'"
                + " AND g.can_manage_reminder=1 ORDER BY r.id DESC LIMIT 100", accountId);
    }

    public void create(long elderId, long createdBy, String title, String recurrence, LocalDateTime firstAt) {
        jdbc.update("INSERT INTO life_reminder(elder_id,created_by,title,recurrence,next_fire_at,status) VALUES(?,?,?,?,?,'ACTIVE')",
                elderId, createdBy, title.trim(), recurrence, firstAt);
    }

    /** 行锁读取，用于修改或停止提醒前的状态校验 */
    public Map<String, Object> forUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT elder_id,status FROM life_reminder WHERE id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void stop(long id) {
        jdbc.update("UPDATE life_reminder SET status='STOPPED',next_fire_at=NULL WHERE id=?", id);
    }

    public void update(long id, String title, String recurrence, LocalDateTime nextFireAt) {
        jdbc.update("UPDATE life_reminder SET title=?,recurrence=?,next_fire_at=? WHERE id=?",
                title.trim(), recurrence, nextFireAt, id);
    }
}
