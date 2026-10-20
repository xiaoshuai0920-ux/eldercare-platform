# 领域与数据基线

## 冻结规则

- 登录账号和老人档案独立；一名老人可授权多个家属，撤销立即阻止后续访问。
- 现场助餐、保洁、陪诊共用服务项目模型；预约写入项目、规则、单价、时长、金额、地址和区域快照。
- 订单、派单、履约、争议、结算、单次模拟交易和通知分别建模；金额为 `DECIMAL` / `BigDecimal`。
- 模拟服务使用独立库，主业务库只保存支付尝试与经查询核实的状态；两库无跨库事务。

## 核心关系

`account` 1:N `elder_profile`（仅创建关系）以及 M:N `elder_profile`（通过 `family_grant`）；老人本人身份由 `elder_identity_claim` 人工核验，家庭争议记录在 `family_dispute`。`elder_profile` 1:N `service_order`、`life_reminder`；`service_item` 1:N `service_order`；`service_order` 1:N `assignment`、`fulfillment`、`order_event`、`service_incident`、`order_dispute`、`order_action_handover`，且 1:0..1 `settlement`、`service_review`。`settlement` 1:N `payment_attempt`。`worker_capability` 连接人员、项目与街道；`worker_schedule`、`worker_leave`、`worker_occupancy` 参与派单冲突判断。`outbox_event` 与 `order_event` 一一对应，产生 `notification`；高优先级待办由订单与人员当前状态查询，不另建第二套状态。

数据库约束覆盖账号邮箱、邀请令牌、活跃派单、订单唯一结算、订单唯一评价、支付尝试外部标识和消息投递去重键。订单历史不可因项目变更而重写。目前没有预置个人演示数据；主库七份 SQL 与模拟交易库一份 SQL 已在隔离 MySQL 8.0.29 执行，历史订单快照在项目修改后保持不变的场景已实测。
