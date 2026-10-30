-- V011：为订单按状态检索补索引。
--
-- 背景：/api/admin/todos 与管理端订单筛选都按 status 过滤，但 service_order 原本只有
-- (elder_id,status,created_at) 和 (start_at,status) 两个复合索引，status 不在最左列，
-- 按状态过滤只能全表扫描。待办列表改成按状态分路查询后，这个索引是每条支路走索引的前提。

ALTER TABLE service_order
  ADD INDEX ix_order_status_id (status, id);
