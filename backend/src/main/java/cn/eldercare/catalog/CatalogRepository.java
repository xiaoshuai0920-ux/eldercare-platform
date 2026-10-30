package cn.eldercare.catalog;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** 服务目录与区域覆盖的数据访问层：控制器不再直接持有 SQL。 */
@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> areas() {
        return jdbc.queryForList("SELECT a.id,a.parent_id,a.level,a.name,"
                + "CONCAT_WS(' ',grand.name,parent.name,a.name) AS full_name FROM service_area a"
                + " LEFT JOIN service_area parent ON parent.id=a.parent_id"
                + " LEFT JOIN service_area grand ON grand.id=parent.parent_id"
                + " WHERE a.active=1 ORDER BY a.level,a.id");
    }

    public List<Map<String, Object>> items(Long streetId, String category) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT i.id,i.category,i.name,i.description,i.pricing_model,"
                + "i.unit_price,i.min_minutes,i.max_minutes,i.available_from,i.available_to,i.rule_text"
                + " FROM service_item i LEFT JOIN service_item_area a ON a.item_id=i.id WHERE i.active=1");
        List<Object> args = new ArrayList<>();
        if (streetId != null) { sql.append(" AND a.street_id=?"); args.add(streetId); }
        if (category != null && !category.isBlank()) { sql.append(" AND i.category=?"); args.add(category); }
        return jdbc.queryForList(sql.append(" ORDER BY i.id DESC").toString(), args.toArray());
    }

    /** 上架项目详情；下架或不存在返回 null */
    public Map<String, Object> publicItem(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM service_item WHERE id=? AND active=1", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> adminItem(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM service_item WHERE id=?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> adminItems() {
        return jdbc.queryForList("SELECT id,category,name,description,pricing_model,unit_price,min_minutes,"
                + "max_minutes,available_from,available_to,advance_minutes,rule_text,active"
                + " FROM service_item ORDER BY id DESC");
    }

    public int updateItem(long id, ItemRequest input) {
        return jdbc.update("UPDATE service_item SET category=?,name=?,description=?,pricing_model=?,unit_price=?,"
                        + "min_minutes=?,max_minutes=?,available_from=?,available_to=?,advance_minutes=?,rule_text=? WHERE id=?",
                input.category(), input.name().trim(), input.description().trim(), input.pricingModel(), input.unitPrice(),
                input.minMinutes(), input.maxMinutes(), input.availableFrom(), input.availableTo(),
                input.advanceMinutes(), input.ruleText().trim(), id);
    }

    public long createItem(ItemRequest input) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO service_item(category,name,description,pricing_model,unit_price,min_minutes,"
                            + "max_minutes,available_from,available_to,advance_minutes,rule_text,active)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, input.category());
            statement.setString(2, input.name().trim());
            statement.setString(3, input.description().trim());
            statement.setString(4, input.pricingModel());
            statement.setBigDecimal(5, input.unitPrice());
            statement.setInt(6, input.minMinutes());
            statement.setInt(7, input.maxMinutes());
            statement.setObject(8, input.availableFrom());
            statement.setObject(9, input.availableTo());
            statement.setInt(10, input.advanceMinutes());
            statement.setString(11, input.ruleText().trim());
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("创建服务项目未返回主键");
        return id.longValue();
    }

    /** 上级区域的层级；上级不存在或未启用返回 null */
    public String parentLevel(long parentId) {
        List<String> levels = jdbc.query("SELECT level FROM service_area WHERE id=? AND active=1",
                (row, n) -> row.getString(1), parentId);
        return levels.isEmpty() ? null : levels.getFirst();
    }

    public void createArea(Long parentId, String level, String name) {
        jdbc.update("INSERT INTO service_area(parent_id,level,name,active) VALUES(?,?,?,1)", parentId, level, name.trim());
    }

    public boolean isActiveStreet(long streetId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM service_area WHERE id=? AND level='STREET' AND active=1",
                Integer.class, streetId);
        return count != null && count > 0;
    }

    public void cover(long itemId, long streetId) {
        jdbc.update("INSERT IGNORE INTO service_item_area(item_id,street_id) VALUES(?,?)", itemId, streetId);
    }

    public List<Map<String, Object>> coverageAreas(long itemId) {
        return jdbc.queryForList("SELECT street.id,CONCAT_WS(' ',city.name,district.name,street.name) AS full_name"
                + " FROM service_item_area cover JOIN service_area street ON street.id=cover.street_id"
                + " JOIN service_area district ON district.id=street.parent_id"
                + " JOIN service_area city ON city.id=district.parent_id"
                + " WHERE cover.item_id=? AND street.active=1 AND district.active=1 AND city.active=1 ORDER BY street.id",
                itemId);
    }

    /** 行锁读取，用于覆盖街道变更前的并发保护 */
    public Map<String, Object> itemForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,active FROM service_item WHERE id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int countCoverage(long itemId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM service_item_area WHERE item_id=?", Integer.class, itemId);
        return count == null ? 0 : count;
    }

    public int deleteCoverage(long itemId, long streetId) {
        return jdbc.update("DELETE FROM service_item_area WHERE item_id=? AND street_id=?", itemId, streetId);
    }

    public int countActiveCoverage(long itemId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM service_item_area a"
                + " JOIN service_area s ON s.id=a.street_id WHERE a.item_id=? AND s.active=1", Integer.class, itemId);
        return count == null ? 0 : count;
    }

    public void publish(long id) {
        jdbc.update("UPDATE service_item SET active=1 WHERE id=?", id);
    }

    public int unpublish(long id) {
        return jdbc.update("UPDATE service_item SET active=0 WHERE id=?", id);
    }
}
