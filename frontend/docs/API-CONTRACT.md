# 前后端联调：接口清单与契约

- 后端：`eldercare-platform/backend`，Spring Boot 3.5 + MySQL 8，默认 `http://127.0.0.1:8080`
- 前端：`eldercare-platform/frontend`（Vue 3 + Vite），dev server 代理 `/api` → `127.0.0.1:8080`
- 认证：**HttpSession Cookie**（`JSESSIONID`，HttpOnly + SameSite=Lax），不是 JWT
- 认证之外的写请求需带 **`X-CSRF-TOKEN`**；下单还需 **`Idempotency-Key`**
- 统一错误结构：`{ code, message, details }`，`message` 已是中文，前端直接展示
- 登录失败限流：同一来源 IP 5 分钟内失败 15 次后返回 429 `LOGIN_RATE_LIMITED`（进程内计数，不信任 `X-Forwarded-For`）；账户维度仍有 5 次锁定 15 分钟
- 列表分页（2026-10-22 起统一）：`?page=1&size=20`，返回 `{ items, page, size, total }`；`page` 从 1 开始，`size` 限 1–100，越界返回 400 `INVALID_PAGE_SIZE` / `INVALID_PAGE`。适配范围：`/admin/orders`（另支持 `status`、`keyword` 服务端筛选）、`/admin/workers`、`/admin/incidents`、`/admin/disputes`、`/admin/reviews`、`/admin/identity-claims`、`/admin/family-disputes`、`/notifications`（另支持 `kind=order|system`）


---

## 一、认证与安全

| 步骤 | 请求 | 说明 |
|---|---|---|
| 1 | `GET /api/auth/csrf` | 返回 `{ token }`；同时建立会话，前端缓存 token |
| 2 | `POST /api/auth/login` `{ username, password }` + `X-CSRF-TOKEN` | 返回 `{ id, username, name, role }`；成功后 Cookie 生效 |
| 3 | 其它请求 | `credentials: same-origin`；写请求自动带 token |
| 4 | `POST /api/auth/logout` | 清会话；前端同时清 token 缓存 |

前端实现（`src/api/client.ts`）：

- CSRF token 缓存；收到 **403** 时清缓存并重取一次，避免会话轮换后全部写操作失败
- **401** → 清会话 + 跳 `/login?redirect=<当前地址>`；`/auth/me` 例外（用于判断是否已登录）
- **超时 10s**（AbortController）；网络失败与超时分别给出中文提示
- `403/404/5xx` 统一转成 `ApiError`，携带 `code` 与 `details`，页面只展示 `message`

登录后按角色跳转：`USER → /user`、`WORKER → /worker`、`ADMIN → /admin`；路由守卫二次校验 `meta.role`，角色不符回各自工作台。

---

## 二、接口清单

> 路径省略 `/api` 前缀；“消费方”指前端 store / 页面。

### 1. 用户端

| 方法 | 路径 | 请求参数 | 响应要点 | 消费方 |
|---|---|---|---|---|
| GET | `/user/dashboard` | — | `waiting/active/completed/recentOrders[]`（订单行含 `item_name_snapshot/start_at/status/payable_amount`） | 首页看板 |
| GET | `/elders` | — | `[{ id, fullName, birthDate }]` | 家庭档案、预约对象 |
| GET | `/elders/{id}/permissions` | — | `canViewService/canBook/canCancel/canConfirm/canReview/canConfirmFee/canManageReminder/canManageFamily` | 家庭页权限矩阵 |
| GET | `/elders/{id}/identity-claims` | — | 身份核验记录（`status/requested_at/review_note`） | 家庭页核验 |
| GET | `/elders/{id}/family-disputes` | — | 家庭争议（`reason/status/reporter_name`） | 家庭页争议 |
| GET | `/catalog/items` | — | 服务项目（`category/pricing_model/unit_price/min_minutes/max_minutes/rule_text/available_*`） | 首页、预约页 |
| GET | `/catalog/areas` | — | 行政区划（`level/name/full_name`） | 预留 |
| GET | `/catalog/items/{id}/areas` | — | 该项目可服务街道 `[{ id, full_name }]` | 预约弹层街道下拉 |
| POST | `/orders` | `{ elderId, itemId, streetId, address, startAt, durationMinutes }` + `Idempotency-Key` | `{ id }` | 分步预约提交 |
| GET | `/orders?page&size` | 分页 | `{ items[], page, size, total }`；行含 `elder_id/category_snapshot/payable_amount/status` | 我的订单 |
| GET | `/orders/{id}` | — | 订单全字段（地址、规则、时长、金额、创建时间） | 订单详情抽屉 |
| POST | `/orders/{id}/cancel` | — | 204 | 取消订单 |
| POST | `/orders/{id}/confirm` | — | 204；确认后生成结算单 | 确认完成 |
| GET | `/notifications?size=50` | — | `[{ id, title, body, target_path, priority, read_at, created_at }]` | 消息页 |
| POST | `/notifications/{id}/read` | — | 204 | 标记已读 |
| GET | `/reminders` | — | `[{ id, elder_name, title, recurrence, next_fire_at, status }]` | 生活提醒 |
| POST | `/reminders` | `{ elderId, title, recurrence, firstAt }` | 201 | 新增提醒 |
| POST | `/reminders/{id}/stop` | — | 204 | 停用提醒 |

### 2. 服务人员端

| 方法 | 路径 | 请求参数 | 响应要点 | 消费方 |
|---|---|---|---|---|
| GET | `/worker/dashboard` | — | `{ accepting, pending, active, tasks[] }`；任务含 `order_id/item_name_snapshot/start_at/end_at/status/street_name_snapshot` | 人员工作台 |
| GET | `/worker/tasks` | — | 任务列表 | 预留 |
| GET | `/worker/tasks/{orderId}` | — | 追加 `elder_name/address_snapshot/rule_snapshot/duration_minutes/assignment_status` | 任务详情抽屉 |
| POST | `/worker/accepting` | `{ accepting }` | 204 | 接单开关 |
| POST | `/orders/{id}/accept` | — | 204：待接单 → 待服务 | 接单 |
| POST | `/orders/{id}/reject` | `{ reason }` | 204：退回待派单 | 拒单 |
| POST | `/orders/{id}/start` | — | 204：待服务 → 服务中 | 开始服务 |
| POST | `/orders/{id}/submit-completion` | — | 204：服务中 → 待确认 | 提交完成 |
| POST | `/orders/{id}/interrupt` | `{ reason }` | 204：进入异常协调 | 上报中断 |
| GET | `/worker/leaves` | — | `[{ id, start_at, end_at, reason, status, decided_at }]` | 请假记录 |
| POST | `/worker/leaves` | `{ startAt, endAt, reason }` | 201 | 提交请假 |

### 3. 管理端

| 方法 | 路径 | 请求参数 | 响应要点 | 消费方 |
|---|---|---|---|---|
| GET | `/admin/dashboard` | — | `waitingAssignment/waitingConfirmation/inService/completed/activeWorkers/recentOrders[]` | 管理工作台 |
| GET | `/admin/todos` | — | `[{ orderId, title, reason, priority }]` | 高优先级待办 |
| GET | `/admin/orders?size=100` | 仅 size（**无 status 参数**） | 订单行含 `elder_name/street_name_snapshot/payable_amount` | 订单表（状态筛选在前端做） |
| GET | `/admin/orders/{id}` | — | `{ order, events[], assignments[], fulfillments[], supplements[], incidents[], feeAdjustments[], disputes[] }` | 详情抽屉 + 时间轴 |
| GET | `/admin/orders/{id}/candidates` | — | `[{ id, display_name }]`；已按资格/街道/排班/请假/占用过滤，无候选返回 `[]` | 派单弹层 |
| POST | `/orders/{id}/assign` | `{ workerId }` | 204：待派单 → 待接单 | 派单 |
| GET | `/admin/workers` | — | `[{ id, username, display_name, status, accepting }]` | 服务人员页签 |
| GET | `/admin/incidents` | — | 服务中断（`order_id/reason/status/order_status/elder_name`） | 异常与争议页签 |
| GET | `/admin/disputes` | — | 订单争议（`order_id/reason/status/opened_by_name`） | 同上 |
| GET | `/admin/reviews` | — | `[{ id, order_id, stars, content, visibility, author_name }]` | 评价治理页签 |
| POST | `/admin/reviews/{id}/hide` | `{ hidden }` | 204 | 隐藏/恢复评价 |
| GET | `/admin/identity-claims` | — | 身份核验（`elder_name/account_name/status`） | 家庭协调页签 |
| GET | `/admin/family-disputes` | — | 家庭争议 | 同上 |
| GET | `/admin/leaves` | — | 人员请假（`worker_name/status`） | 预留 |
| GET | `/catalog/admin/items` | — | 服务项目（含 `active`） | 服务目录页签 |

---

## 三、状态机与状态名

订单状态码与中文名统一取自 `src/constants/status.ts`，与后端一致：

```
WAITING_ASSIGNMENT 待派单 → WAITING_ACCEPTANCE 待接单 → WAITING_SERVICE 待服务
→ IN_SERVICE 服务中 → WAITING_CONFIRMATION 待确认 → COMPLETED 已完成
旁路：CANCELLED 已取消 / INTERRUPTED 服务中断 / ABNORMAL_CLOSED 异常结案
```

前端不自行推断状态，只按接口返回值渲染；按钮可见性由状态决定，点击后重新拉取列表与详情，确保以服务端为准。

---

## 四、加载与错误处理

每个数据区都有三态，且**分区独立**，一个接口失败不影响其他区块：

| 场景 | 呈现 |
|---|---|
| 加载中 | `SkeletonBlock` 骨架，不用整页转圈 |
| 空数据 | `EmptyState`，给出下一步动作（去预约 / 新增提醒 / 调整筛选） |
| 请求失败 | `ErrorBanner` + “重新加载”，展示后端 `message` |
| 写操作 | 按钮 `loading` → 成功轻提示 → 局部刷新（重拉列表/详情） |
| 401 | 清会话 + 跳登录并保留回跳地址 |
| 403 / 404 / 5xx / 超时 / 断网 | 分别映射为“没有权限”“数据不存在”“服务不可用”“请求超时”“网络连接失败” |

管理端 store（`src/stores/admin.ts`）用 `errors` 按 key 记录分区错误（dashboard / orders / reviews…），页面只在对应页签提示。

---

## 五、已完成的联调验证

环境：MySQL 8（`eldercare` 库，15 条种子订单）+ 后端 8080 + 前端 5173。

**页面渲染**（Playwright + 本机 Chrome，三个账号分别登录）：

| 账号 | 页面 | 结果 |
|---|---|---|
| family01 | /user、/user/services、/user/orders、/user/family、/user/reminders、/user/messages | 全部渲染真实数据，无控制台异常（仅预登录 `/auth/me` 的预期 401） |
| worker01 | /worker、/worker/leave | 同上 |
| admin | /admin、/admin/operations、`?panel=catalog`、`?panel=exceptions` | 同上 |

**写链路**（订单 #61，全流程 200）：

```
family01 下单 → 待派单
admin 查候选 → [{"id":19,"display_name":"王秀兰"}]
admin 派单 → 待接单
worker01 接单 → 待服务 → 开始 → 服务中 → 提交完成 → 待确认
family01 确认 → 已完成，并生成结算单（status=WAITING_METHOD）
```

---

## 六、接口与页面的差异（如实处理，未编造）

1. **老人档案**只有 `id/fullName/birthDate`：家庭页不再展示饮食、行动能力、地址等未采集字段，改为“基本信息 + 我的权限 + 核验与争议”。
2. **用户订单列表**不含老人姓名，用 `/elders` 的 `id → fullName` 映射；地址与规则只在详情接口里，卡片上不展示。
3. **用户订单详情**没有事件时间轴（只有管理端详情有），用户侧抽屉改为订单信息 + 结算说明，不伪造时间轴。
4. **派单候选**只返回 `id/display_name`；弹层不再展示“本周单量”“空闲时段”等后端未返回的字段，改为说明筛选口径。
5. **`/admin/orders` 不支持状态过滤**：页签内的状态筛选与搜索在前端完成（数据量 100 条以内）。
6. **结算与支付**：确认后结算单状态为 `WAITING_METHOD`，模拟支付未启用（`app.mock-payment.enabled=false`），前端不展示“已支付”。
