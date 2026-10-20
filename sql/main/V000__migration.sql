-- 迁移登记表：让 scripts/init-db.ps1 可以安全重放版本化 SQL。
-- 只记录已执行的脚本名，不含业务数据。
CREATE TABLE IF NOT EXISTS schema_migration (
  script_name VARCHAR(200) NOT NULL PRIMARY KEY,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
