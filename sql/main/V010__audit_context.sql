-- V010：审计事件补充上下文（来源 IP、结果），并支持按动作与时间检索。
-- 说明：登录失败、限流拦截等场景可能没有 actor_id，因此 ip/result 独立成列；
-- 已存在的历史行由 DEFAULT 填充为 SUCCESS，无需回填。

ALTER TABLE audit_event
  ADD COLUMN ip VARCHAR(45) NULL COMMENT '来源 IP，IPv6 最长 45 字符' AFTER actor_id,
  ADD COLUMN result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS 成功 / FAILURE 失败 / BLOCKED 被拦截' AFTER action;

ALTER TABLE audit_event
  ADD INDEX ix_audit_action_created (action, created_at),
  ADD INDEX ix_audit_result_created (result, created_at);
