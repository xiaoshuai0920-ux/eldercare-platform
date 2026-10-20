-- 账号登录体系迁移：
-- 1) account 新增 username 作为唯一登录名，email 降级为可选联系方式
-- 2) 家庭邀请改为按账号定位（email 可空后不能再作为定位键）
-- 3) 下线邮箱验证码与人员一次性激活口令两张表
-- 依赖 V000 的 schema_migration 保证本脚本只执行一次。

ALTER TABLE account
  ADD COLUMN username VARCHAR(32) NULL AFTER id,
  MODIFY COLUMN email VARCHAR(254) NULL;

-- 历史数据回填：角色小写 + 主键，保证 4–32 位且唯一
UPDATE account SET username = CONCAT(LOWER(role), id) WHERE username IS NULL;

-- 首个管理员回填为可识别的 admin
UPDATE account a
  JOIN (SELECT MIN(id) AS id FROM account WHERE role = 'ADMIN') t ON a.id = t.id
SET a.username = 'admin';

-- 取消激活口令前，先把遗留待激活账号停用，避免产生永远无法登录的僵尸账号
UPDATE account SET status = 'DISABLED', accepting = 0 WHERE status = 'PENDING_ACTIVATION';

ALTER TABLE account
  MODIFY COLUMN username VARCHAR(32) NOT NULL,
  ADD UNIQUE KEY uk_account_username (username);

-- 家庭邀请改按账号定位；invitee_email 保留为可空冗余信息
ALTER TABLE family_invitation
  ADD COLUMN invitee_account_id BIGINT NULL AFTER inviter_id,
  MODIFY COLUMN invitee_email VARCHAR(254) NULL,
  ADD INDEX ix_invitation_account (invitee_account_id, status),
  ADD CONSTRAINT fk_invitation_invitee FOREIGN KEY (invitee_account_id) REFERENCES account(id);

UPDATE family_invitation i
  JOIN account a ON a.email = i.invitee_email
SET i.invitee_account_id = a.id
WHERE i.invitee_account_id IS NULL;

DROP TABLE IF EXISTS worker_activation;
DROP TABLE IF EXISTS email_verification;
