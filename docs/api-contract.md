# API 基线

- 同源入口 `/api`，JSON UTF-8；Session Cookie 为浏览器认证依据，修改请求提交 CSRF Token；不在浏览器持久存储认证秘密。
- 成功返回资源或命令结果，失败使用正确 HTTP 状态及 `{code,message,details}`。用户订单列表使用 `{items,page,size,total}`；其他运营列表当前为有上限的数组，分页尚未统一。
- 资源查询使用 `GET /api/...`；状态变化采用 `POST /api/orders/{id}/assign|accept|reject|start|submit-completion|confirm|cancel|interrupt` 等语义命令。不得提供通用 `PUT status`。
- 创建订单强制使用 `Idempotency-Key`；接单、开始服务、提交完成和交易同步对已达目标状态的重复请求返回当前结果，其他命令按状态冲突拒绝重复执行。版本冲突返回 409，未认证 401、无权限 403、无资源 404、参数错误 400。
- 模拟支付独立服务只接受受限控制台写入交易状态；主服务经带服务端凭证的查询接口按尝试标识核实金额、版本和状态，查询不可用与交易失败分开表示。

精确字段和接口清单随实现增量补足，需保持此处与代码和测试一致。

主要命令入口：`POST /api/orders`（预约）、`/api/orders/{id}/assign|accept|reject|start|submit-completion|confirm|cancel|interrupt`、`/api/admin/orders/{id}/interrupt`、`/api/orders/{id}/fee-adjustments`、`/api/orders/{id}/settlement/payment-attempts|sync|offline`、`/api/worker/leaves`、`/api/admin/leaves/{id}/approve|reject`。目录增加 `GET /api/catalog/items/{id}/areas`，预约页仅提供该项目实际覆盖街道。身份与家庭协调使用 `POST /api/elders/{id}/identity-claims`、`POST /api/elders/{id}/family-disputes` 及对应管理员审核接口；受控交接使用 `/api/admin/orders/{id}/handovers` 并限定指定操作与接收人。管理员订单历史为 `GET /api/admin/orders/{id}`，当前人员任务详情为 `GET /api/worker/tasks/{id}`。模拟支付服务使用独立的 `/api/transactions` 与 `/api/transactions/{ref}/status`；查询接口为 `GET /api/transactions/{ref}`。主要分支的实测范围与缺口见 `implementation-plan.md`。
