# 代码审查与完善建议

审查日期：2026-10-22　范围：`eldercare-platform`（backend / frontend / sql / scripts / docs）

## 一、审查方法与基线

不是凭印象判断，逐项读了代码并验算：

- 后端：37 个 Java 源文件、16 个控制器、109 个端点、14 个测试文件（31 个 `@Test`）、13 个 SQL 迁移；21 个文件直接用 `JdbcTemplate` 写 SQL，41 处 `@Transactional`。
- 前端：68 个源文件、23 个 ui 组件、10 个 store、15 个视图、3 个 spec（17 例）。
- 文档：`docs/` 下 6 篇（`api-contract` 13 行、`state-machines` 14 行、`permission-matrix` 18 行），`AGENTS.md` 99 行。
- 已实测：类型检查、lint、格式、单测、构建、三角色浏览器验证、写操作链路（见 `docs/STATUS.md` 2026-10-22 条）。

结论：**架构与安全基线是扎实的**（CSRF + HttpSession、BCrypt 12、登录锁定、账户状态过滤、命令式状态机、幂等键、测试库守卫）。问题集中在"演示数据残留到真实逻辑""列表规模上限""用户端功能入口缺口""异常兜底不全""工程化门禁缺位"五处。

---

## 二、P0：会直接影响正确性与验收

### 1. 前端写死演示日期（最高优先）

> **状态：已修复（2026-10-22）**，验证方式见文末「P0 修复记录」。

| 位置 | 现状 |
|---|---|
| `frontend/src/views/user/ServicesView.vue:43` | `date: '2026-10-24'` 作为预约默认日期 |
| `frontend/src/views/user/RemindersView.vue:25,38` | `firstAt: '2026-10-24T09:00'` |
| `frontend/src/views/worker/LeaveView.vue:19` | 请假默认 `2026-10-28` |
| `frontend/src/utils/format.ts:8` | `today()` 固定返回 `2026 年 10 月 21 日` |

风险：真实使用时默认日期是过去时刻，预约与提醒会被后端规则拒绝或写入错误时段；侧栏顶部日期永远停在 10 月 21 日。答辩现场一旦被要求"现在下一单"，第一屏就会暴露。

建议：默认值改为「当前时间 + 该项目 `advanceMinutes`」，`today()` 用真实时间；提交前增加"不得早于现在"的本地校验，错误定位到字段。

### 2. 全局异常兜底缺失

> **状态：已修复（2026-10-22）**，验证方式见文末「P0 修复记录」。

`backend/.../common/ApiExceptionHandler.java` 只处理 `ApiException` 与 `MethodArgumentNotValidException`。

未覆盖：请求体 JSON 格式错误（`HttpMessageNotReadableException`）、路径参数类型错误（`MethodArgumentTypeMismatchException`）、405/415、以及任何未捕获异常。这些会落到 Spring 默认错误体 `{timestamp,status,error,path}`，与约定的 `{code,message,details}` 不一致；前端 `toApiError` 只能退化到「请求失败（400）」或「服务暂时不可用」，丢掉可定位信息。

建议：补 4 个具体 handler + 一个 `Exception.class` 兜底（500 固定文案、堆栈只进日志、响应带 traceId）。

### 3. `skip-link` 只有样式没有 DOM

> **状态：已修复（2026-10-22）**，验证方式见文末「P0 修复记录」。

`frontend/src/styles/app.css:85` 定义 `.skip`，但全站没有任何 `<a class="skip">`（grep「跳到主内容」为 0）。需求 §6 明确要求"提供跳到主内容"，属未达标项。

建议：三个 layout 顶部各加一处，主内容区 `id="main" tabindex="-1"`；键盘 Tab 第一下即可见。

### 4. 无全局渲染异常兜底

> **状态：已修复（2026-10-22）**，验证方式见文末「P0 修复记录」。

`frontend/src/main.ts` 没有 `app.config.errorHandler`，任一组件 render 抛错直接白屏，没有任何用户可见提示（本轮就踩到过一次 `slice` 空值）。

建议：注册 `errorHandler` + `onErrorCaptured`，落到顶部 ErrorBanner 并保留"重新加载"动作。

---

## 三、P1：功能完整性与可靠性

### 5. 列表上限静默截断，分页不统一

> **状态：已修复（2026-10-22）**，8 个列表接口统一 `{items,page,size,total}`，前端接分页条。

- `docs/api-contract.md` 自认："用户订单列表使用 `{items,page,size,total}`；**其他运营列表当前为有上限的数组，分页尚未统一**"。
- 实测：`DashboardController:74`（异常列表）、`FamilyCoordinationController:69/117`（核验、争议）均 `LIMIT 100`；`NotificationController` 支持 size 但无 offset；前端 `OrdersView` 一次取 50 条、无"加载更多"。
- 后果：超过 100 条后管理员看不到更早的单据，且界面没有任何"仅显示最近 100 条"的提示——这是答辩最容易被追问的点。

建议：后端统一 `page/size` 返回 `{items,page,size,total}`；前端列表底部加"加载更多"（不必做页码器）。过渡方案：先在界面显式标注上限。

### 6. 用户端三个功能入口空缺

> **状态：部分修复（2026-10-22）**：评价提交、家庭邀请/撤销/核验/争议已完成；结算仍按首期范围不开放。

| 能力 | 后端 | 前端 |
|---|---|---|
| 提交评价 | `POST /orders/{id}/review` 存在 | **0 处调用**（只有管理端"评价治理"读列表） |
| 家庭管理写操作（邀请/撤回/争议） | `/elders/{id}/...` 齐备 | `FamilyView` 仅只读，按钮只弹提示 |
| 结算/支付 | `/orders/{id}/settlement/*` 齐备 | 无入口，停在 `WAITING_METHOD` |

建议：评价与家庭争议属于"接口现成、成本低"，应补齐；结算按首期范围，在订单详情明示"本期不含在线支付"，避免看起来像漏做。

### 7. 动态 SQL 列名拼接（暂无注入面，但有隐患）

> **状态：已修复（2026-10-23）**：改为 `PermissionColumn` 枚举，编译期即不可能传入未知列名。

`FamilyService.java:161` `g."+column+"=1`、`FamilyCoordinationController.java:150` `g."+permission+"=1`。

当前 8 处调用全部传字符串字面量（`can_view_service` 等），`OrderActionAccess.permission(action)` 也是白名单映射，所以**当前不可注入**。但方法签名允许任意字符串，未来接入新调用点即成风险。

建议：改为枚举 `PermissionColumn`，未知值直接抛 400；加一条单测断言"未知列名被拒"。

### 8. 测试覆盖的结构性缺口

> **状态：已修复（2026-10-23）**：新增 4 个组件测试文件（18 例）与 E2E 冒烟（9 项）。

现有：后端 31 个 `@Test`（含集成测试、测试库守卫、锁定、订单规则、支付失败），前端 17 例（client / status / session）。

缺：
- 前端组件级测试 0 个——`AppDialog` 焦点归还与 Esc、`DataTable` 窄屏卡片化、`SeniorToggle` 的 `aria-pressed` 都无自动化保护；
- E2E 未入库——本轮验证用的 Playwright 脚本跑在临时目录，下次没人能复跑；
- 无覆盖率统计（后端无 jacoco，前端无 vitest coverage），无法判断"哪块最薄"。

建议：先加 4–6 个组件测试 + 1 条 E2E 冒烟（登录→预约→派单→接单→提交→确认），挂 `npm run test:e2e`；覆盖率先只统计不设阈值。

### 9. 登录锁定可被用于 DoS

> **状态：已修复（2026-10-23）**：新增按来源 IP 的滑动窗口限流。

`AuthService.java:78` 按账户累计失败 5 次锁 15 分钟。攻击者知道用户名即可反复触发，把正常用户锁在门外；无 IP / 会话维度节流。

建议：账户维度保留（防爆破），另加 IP 维度令牌桶（简单内存实现即可），并把锁定事件写审计。

---

## 四、P2：工程化与长期可维护性

### 10. 无 CI，质量门依赖手工

`.github` 不存在，`scripts/test.ps1` 需人工执行；本轮我改了 100+ 文件，靠逐条命令自查。建议加最小流水线（`mvn -o test` + `npm run lint / test:run / build`）或 pre-push hook。

### 11. SQL 分散、无 repository 层

21 个文件直接 `JdbcTemplate` 拼 SQL，`AGENTS.md` 声明的 MyBatis-Plus 实际未使用。订单域有 `OrderService` 收口，但 `DashboardController`（187 行）、`FamilyCoordinationController` 里仍有大量长 SQL。

建议：按域抽 repository（先 orders、notifications），不改接口契约；后续按改动频率推进，不必一次到位。

### 12. 审计只覆盖订单域

`audit(actorId, "FAMILY_REVOKE", ...)` 与 `order_event` 有记录，但登录成功/失败、管理员改人员、改目录无审计。建议统一 `audit_log` 表。

### 13. 文档分散，索引不完整

- `docs/api-contract.md` 只有 13 行，明确写"精确字段……随实现增量补足"；
- 真正的接口清单在 `frontend/docs/API-CONTRACT.md`，但 `docs/` 没有指向它；
- 根 `README.md` 未链接 `frontend/README.md`，新人找不到前端运行说明。

建议：`docs/api-contract.md` 吸收接口清单（或加一行指向），根 README 补一节"前端结构与运行"。

### 14. 生产配置写死本机地址

`application.yml` 里 `server.address: 127.0.0.1`，`application-prod.yml` 未覆盖 → 生产部署也只能本机访问。建议 prod 改为环境变量注入（`${SERVER_ADDRESS:0.0.0.0}`），并复核 Session Cookie 的 `secure` 已开（当前已开）。

### 15. 性能：可接受，但有已知慢查询

`DashboardController:74` 用 `SELECT DISTINCT` + 多段 `OR` 拼意外订单，`ix_order_*` 索引难以命中；工作台每次 3 条 `COUNT(*)`。当前 15 条种子数据无感，数据上量后是首个瓶颈。

建议：短期在文档标注量级假设，中期拆成"按状态分桶"的独立查询或加 30 秒缓存。

---

## 五、不建议做的事（避免过度设计）

- 不引入第三方 UI 库或状态库：现有 23 个组件已覆盖三端，替换只会引入新的视觉偏差。
- 不为演示项目拆微服务 / 上消息队列：outbox + 定时分发已足够。
- 不做 i18n：单语种交付。
- 不做虚拟滚动：列表上限 ≤100 条，收益低于复杂度。

---

## 六、P1 修复记录（2026-10-22 第一批）



| 项 | 改动 | 验证 |
|---|---|---|
| 列表分页 | 新增 `common/PageQuery` / `PageResult`；`/admin/orders`（含 status/keyword 服务端筛选）、`/admin/workers`、`/admin/incidents`、`/admin/disputes`、`/admin/reviews`、`/admin/identity-claims`、`/admin/family-disputes`、`/notifications`（含 kind 分类）统一分页 | curl：订单 total 16、第 2 页返回后续条目；人员 size=2 生效；`PaginationContractTest` 4 例（信封字段、越界页空、size 越界 400、page=0 400） |
| 前端分页 | 新增 `PaginationBar`（页码邻域 + 省略号 + `aria-current`）；六页签与消息页接入；角标改 `total`；筛选改服务端（300ms 防抖） | 浏览器：管理端显示「共 16 条 · 第 1/2 页」，翻页后行内容变化；消息「共 12 条 · 第 1/1 页」 |
| 用户端评价 | `COMPLETED` 订单出现「评价服务」，已评价回显只读结果 | 浏览器实测提交 order 61 → 5 星 + 文字，管理端评价治理出现该条（id 9，陈晓梅） |
| 家庭入口 | 邀请家人（8 项权限）、撤销授权（二次确认 + 后果说明）、申请核验、提交争议；无 `can_manage_family` 时提示而非空列表 | 浏览器实测邀请弹层与权限勾选；接口 403 时显示「无法查看成员授权」 |

回归：后端 35 例（31 + 新增 4）通过；`scripts/test.ps1` 四步全绿；前端 `typecheck / lint / format:check / test(17) / build` 通过（JS 276.78 kB，gzip 93.00）。

## 七、P1 修复记录（2026-10-23 第二批）

| 项 | 改动 | 验证 |
|---|---|---|
| 组件测试 | `PaginationBar`（7 例：总数/页码、首末页禁用、翻页事件、`aria-current` 不重复触发、省略号折叠、空数据不渲染、禁用态）、`AppDialog`（4 例：语义与内容、Esc、焦点接管与归还、关闭态 `aria-hidden`）、`DataTable`（5 例：表头行、`data-label`、数字列样式、空态、加载态）、`SeniorToggle`（2 例：写入/移除 `<html data-senior>`） | `npm run test:run`：7 文件 / 35 例 |
| E2E | `frontend/e2e/smoke.mjs` + `npm run test:e2e`；`playwright-core` 作为 devDependency 驱动本机 Chrome，不下载浏览器二进制；默认只读，写操作仅接单开关并还原 | 9/9 项通过；`scripts/test.ps1` 四步全绿 |
| 登录限流 | `security/LoginThrottle`：按 `getRemoteAddr()` 的 5 分钟滑动窗口，15 次失败后 429 `LOGIN_RATE_LIMITED`，成功清零；不信任 `X-Forwarded-For` | `LoginThrottleTest` 4 例（阈值、来源隔离、清零、窗口过期） |
| 列名枚举 | `family/PermissionColumn`（8 个列名）+ `FamilyService.requirePermission(PermissionColumn)`；24 处调用点、`OrderActionAccess.permission` 全部改为枚举，移除运行时字符串白名单 | `PermissionColumnTest` 3 例；后端 42 例全过 |

## 八、P0 修复记录（2026-10-22）

| 项 | 改动 | 验证 |
|---|---|---|
| 写死日期 | `utils/format.ts` 新增 `todayIso / localDateTime / addDays / isPast / nowMs`，`today()` 改真实时间；`ServicesView` 默认明天 09:00 + `min` 属性 + 第 2 步拦截过去时间；`RemindersView` 默认下一整点 + 过去时间校验；`LeaveView` 默认明天 08:00–17:00 + 开始时间不得早于现在 | 浏览器实测：预约默认 `2026-10-23`（min `2026-10-22`）、提醒 `2026-10-22T23:00`（min 当前）、请假 `2026-10-23T08:00`–`17:00` |
| 异常兜底 | `ApiExceptionHandler` 新增 `INVALID_BODY`(400)、`INVALID_PARAMETER`(400)、`MISSING_PARAMETER`(400)、`METHOD_NOT_ALLOWED`(405)、`UNSUPPORTED_MEDIA_TYPE`(415)、`RESOURCE_NOT_FOUND`(404)、`DATA_ACCESS_ERROR`(500) 与 `Exception` 兜底（500 带 `traceId`，堆栈只进日志） | curl 实测五类错误均返回 `{code,message,details}`：`/orders/abc`→INVALID_PARAMETER、非法 JSON→INVALID_BODY、未知路径→RESOURCE_NOT_FOUND、PUT→METHOD_NOT_ALLOWED、未登录仍为 UNAUTHENTICATED |
| skip-link | `UserLayout / StaffLayout / AuthLayout` 顶部加 `<a class="skip" href="#main">`，主内容区 `id="main" tabindex="-1"`；`StaffLayout` 内容容器由 `div` 改为语义 `main` | 浏览器实测：Tab 首站即 `.skip`，回车后 `hash=#main` 且焦点落在 `#main` |
| 全局错误兜底 | `main.ts` 注册 `app.config.errorHandler` 与 `unhandledrejection`，3 秒内去重，统一提示「页面出现异常，请刷新后重试」并保留控制台堆栈 | 浏览器内制造未处理拒绝：出现该提示，页面其余内容仍正常渲染 |

回归：`typecheck / lint / format:check / test:run(17) / build` 全通过（JS 259.28 kB，gzip 87.83）；后端 `mvn -o -q -DskipTests package` 通过，`SqlValuesTest`、`TestDatabaseGuardTest` 通过；三端页面复测无运行时报错。

## 九、建议的执行顺序

| 批次 | 内容 | 理由 | 估计 |
|---|---|---|---|
| 第一批 ✅ 已完成 | P0-1 日期、P0-2 异常兜底、P0-3 skip-link、P0-4 全局错误 | 见第六节 | 已完成 |
| 第二批 ✅ 已完成 | P1-5 分页、P1-6 评价与家庭入口 | 见第六节（结算按首期范围未开放） | 已完成 |
| 第三批 ✅ 已完成 | P1-8 测试补齐、P1-9 限流、P1-7 列名白名单 | 见第七节 | 已完成 |
| 第四批 | P2-10 CI、P2-11 repository、P2-13 文档整合 | 长期维护，可在答辩后推进 | 3–5 天 |

如需按批次执行，建议每批结束跑一次 `scripts/test.ps1` + 三角色冒烟，保持"改动—验证"闭环。
