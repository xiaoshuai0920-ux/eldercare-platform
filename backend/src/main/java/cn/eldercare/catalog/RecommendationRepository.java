package cn.eldercare.catalog;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 个性化推荐与偏好标签的数据访问层。 */
@Repository
public class RecommendationRepository {
    private final JdbcTemplate jdbc;

    public RecommendationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 指定街道可下单的上架项目 */
    public List<Map<String, Object>> itemsByStreet(long streetId) {
        return jdbc.queryForList("SELECT DISTINCT i.id,i.category,i.name,i.description,i.pricing_model,i.unit_price,"
                + "i.min_minutes,i.max_minutes,i.rule_text FROM service_item i"
                + " JOIN service_item_area a ON a.item_id=i.id WHERE i.active=1 AND a.street_id=? ORDER BY i.id DESC",
                streetId);
    }

    /** 单个项目与老人偏好命中的标签 */
    public List<String> matchedTags(long elderId, Object itemId) {
        return jdbc.query("SELECT t.tag FROM service_item_tag t JOIN elder_preference_tag p ON p.tag=t.tag AND p.elder_id=?"
                + " WHERE t.item_id=? ORDER BY t.tag", (row, n) -> row.getString(1), elderId, itemId);
    }

    public List<String> preferences(long elderId) {
        return jdbc.query("SELECT tag FROM elder_preference_tag WHERE elder_id=? ORDER BY tag",
                (row, n) -> row.getString(1), elderId);
    }

    public void addPreference(long elderId, String tag) {
        jdbc.update("INSERT IGNORE INTO elder_preference_tag(elder_id,tag) VALUES(?,?)", elderId, tag.trim());
    }

    public void removePreference(long elderId, String tag) {
        jdbc.update("DELETE FROM elder_preference_tag WHERE elder_id=? AND tag=?", elderId, tag);
    }

    public List<String> itemTags(long itemId) {
        return jdbc.query("SELECT tag FROM service_item_tag WHERE item_id=? ORDER BY tag",
                (row, n) -> row.getString(1), itemId);
    }

    public boolean itemExists(long itemId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM service_item WHERE id=?", Integer.class, itemId);
        return count != null && count > 0;
    }

    public void addItemTag(long itemId, String tag) {
        jdbc.update("INSERT IGNORE INTO service_item_tag(item_id,tag) VALUES(?,?)", itemId, tag.trim());
    }

    public void removeItemTag(long itemId, String tag) {
        jdbc.update("DELETE FROM service_item_tag WHERE item_id=? AND tag=?", itemId, tag);
    }
}
