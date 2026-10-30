package cn.eldercare.identity;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 管理员视角下普通用户账号的数据访问层。 */
@Repository
public class UserAdminRepository {
    private final JdbcTemplate jdbc;

    public UserAdminRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> users() {
        return jdbc.queryForList("SELECT id,username,display_name,email,status,created_at FROM account"
                + " WHERE role='USER' ORDER BY id DESC LIMIT 100");
    }

    /** 行锁读取当前状态，避免并发的启停操作互相覆盖 */
    public Map<String, Object> userForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,status FROM account WHERE id=? AND role='USER' FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE account SET status=? WHERE id=?", status, id);
    }
}
