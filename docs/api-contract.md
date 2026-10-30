# API 基线

- 同源入口 `/api`，JSON UTF-8；Session Cookie 为浏览器认证依据，修改请求提交 CSRF Token；不在浏览器持久存储认证秘密。
- 认证以登录账号 `username` 为唯一凭证（4–32 位小写字母、数字或下划线），`email` 为可选联系方式；密码长度 6–72 位。`POST /api/auth/register` 直接创建可用账号，不依赖邮箱验证码；服务人员账号由管理员在 `POST /api/admin/workers` 中直接设定初始密码并置为 `ACTIVE`。
- 登录失败累计 5 次锁定 15 分钟，返回 429 `ACCOUNT_LOCKED`；登录成功清零。`POST /api/auth/password` 供已登录账号修改密码。
- 成功返回资源或命令结果，失败使用正确 HTTP 状态及 `{code,message,details}`。用户订单列表使用 `{items,page,size,total}`；其他运营列表当前为有上限的数组，分页尚未统一。
- 资源查询使用 `GET /api/...`；状态变化采用 `POST /api/orders/{id}/assign|accept|reject|start|submit-completion|confirm|cancel|interrupt` 等语义命令。不得提供通用 `PUT status`。
- 创建订单强制使用 `Idempotency-Key`；接单、开始服务、提交完成和交易同步对已达目标状态的重复请求返回当前结果，其他命令按状态冲突拒绝重复执行。版本冲突返回 409，未认证 401、无权限 403、无资源 404、参数错误 400。
- 模拟支付独立服务只接受受限控制台写入交易状态；主服务经带服务端凭证的查询接口按尝试标识核实金额、版本和状态，查询不可用与交易失败分开表示。
- 生活提醒支持 `PUT /api/reminders/{id}`，请求体为 `title`、`recurrence`（`ONCE`、`DAILY`、`WEEKLY`）及 `nextFireAt`（业务时区 `Asia/Shanghai` 的未来本地时间）。调用方需具有该老人当前有效的提醒管理权限；只能修改 `ACTIVE` 提醒，已停止或结束返回 409。修改与投递共用提醒记录行锁，历史投递记录保留。
- 老人资料支持 `PUT /api/elders/{id}`，请求体为 `fullName`（非空，最多 80 字符）和 `birthDate`（ISO 日期或 null；省略时同样清空），返回修改后的老人资料。仅当前有效的家庭管理授权可修改，已撤销授权或档案不处于 ACTIVE 时返回 403；不修改家庭关系。
- 管理员排班支持 `POST /api/admin/workers/{workerId}/schedules` 新增、`PUT /api/admin/workers/{workerId}/schedules/{scheduleId}` 修改、`DELETE /api/admin/workers/{workerId}/schedules/{scheduleId}` 删除；星期须为 1–7，结束时间须晚于开始时间。同一人员同一星期的时段不得重叠；目标不存在返回 404，时段冲突返回 409。三种写操作都先锁定服务人员记录，以串行化同一人员的排班变更；修改排除当前时段自身后检查重叠。

精确字段和接口清单随实现增量补足，需保持此处与代码和测试一致。

主要命令入口：`POST /api/orders`（预约）、`/api/orders/{id}/assign|accept|reject|start|submit-completion|confirm|cancel|interrupt`、`/api/admin/orders/{id}/interrupt`、`/api/orders/{id}/fee-adjustments`、`/api/orders/{id}/settlement/payment-attempts|sync|offline`、`/api/worker/leaves`、`/api/admin/leaves/{id}/approve|reject`。目录增加 `GET /api/catalog/items/{id}/areas`，预约页仅提供该项目实际覆盖街道。身份与家庭协调使用 `POST /api/elders/{id}/identity-claims`、`POST /api/elders/{id}/family-disputes` 及对应管理员审核接口；受控交接使用 `/api/admin/orders/{id}/handovers` 并限定指定操作与接收人。管理员订单历史为 `GET /api/admin/orders/{id}`，当前人员任务详情为 `GET /api/worker/tasks/{id}`。模拟支付服务使用独立的 `/api/transactions` 与 `/api/transactions/{ref}/status`；查询接口为 `GET /api/transactions/{ref}`。主要分支的实测范围与缺口见 `implementation-plan.md`。
