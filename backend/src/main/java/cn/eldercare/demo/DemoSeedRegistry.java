package cn.eldercare.demo;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * 演示数据的小工具层：按业务自然键查已存在记录，按 seed_key 记录新建实体。
 * 目标是重复启动不重复写入，并且只增不改不删。
 */
@Component
public class DemoSeedRegistry {
    private final JdbcTemplate jdbc;

    public DemoSeedRegistry(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long findId(String sql, Object... args) {
        List<Long> ids = jdbc.query(sql, (row, index) -> row.getLong(1), args);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    public long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("演示数据写入未返回主键: " + sql);
        return id.longValue();
    }

    public Long recall(String seedKey) {
        List<Long> ids = jdbc.query("SELECT entity_id FROM demo_seed_record WHERE seed_key=?",
                (row, index) -> row.getLong(1), seedKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public void remember(String seedKey, String entityType, long entityId) {
        jdbc.update("INSERT IGNORE INTO demo_seed_record(seed_key,entity_type,entity_id) VALUES(?,?,?)",
                seedKey, entityType, entityId);
    }

    /** 已有登记直接复用，否则执行创建并登记。 */
    public long upsert(String seedKey, String entityType, java.util.function.Supplier<Long> creator) {
        Long existing = recall(seedKey);
        if (existing != null) return existing;
        long created = creator.get();
        remember(seedKey, entityType, created);
        return created;
    }
}
