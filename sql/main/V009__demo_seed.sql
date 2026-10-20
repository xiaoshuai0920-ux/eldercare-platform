-- 演示数据登记表：记录 seeder 创建过的实体，保证重复启动不重复写入。
-- 本脚本只建表，不含任何业务数据；演示数据由 DemoDataSeeder 在应用启动时写入。
CREATE TABLE IF NOT EXISTS demo_seed_record (
  seed_key VARCHAR(80) NOT NULL PRIMARY KEY,
  entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_demo_seed_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
