-- 上门服务联系电话：下单时可选填写，随订单快照保存，供服务人员接单后联系。
ALTER TABLE service_order
  ADD COLUMN contact_phone VARCHAR(32) NULL AFTER address_snapshot;
