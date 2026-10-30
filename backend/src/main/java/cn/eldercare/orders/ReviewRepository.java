package cn.eldercare.orders;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 服务评价的数据访问层。 */
@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbc;

    public ReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> order(long id, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,creator_id,elder_id,status FROM service_order WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> byOrder(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,stars,content,visibility,created_at FROM service_review WHERE order_id=?", orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 一个订单只允许一条评价，重复插入由唯一键抛出 DuplicateKeyException */
    public void insert(long orderId, long authorId, int stars, String content) {
        jdbc.update("INSERT INTO service_review(order_id,author_id,stars,content) VALUES(?,?,?,?)",
                orderId, authorId, stars, content);
    }

    public long adminCount() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM service_review r JOIN account a ON a.id=r.author_id",
                Long.class);
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> adminPage(int size, int offset) {
        return jdbc.queryForList("SELECT r.id,r.order_id,r.stars,r.content,r.visibility,r.created_at,"
                + "a.display_name AS author_name FROM service_review r JOIN account a ON a.id=r.author_id"
                + " ORDER BY r.id DESC LIMIT ? OFFSET ?", size, offset);
    }

    /** 仅对仍处于 VISIBLE 的评价生效，返回受影响行数 */
    public int hide(long id, long moderatorId, String reason) {
        return jdbc.update("UPDATE service_review SET visibility='HIDDEN',moderated_by=?,moderation_reason=?,"
                + "moderated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND visibility='VISIBLE'", moderatorId, reason, id);
    }
}
