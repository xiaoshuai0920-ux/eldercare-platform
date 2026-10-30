# 项目现状

截至 2026-10-30：PC 核心业务链已在隔离数据库、真实后端与独立模拟支付服务上完成本机浏览器闭环验收；仍**不标记为完整交付**，管理端配置与异常处理等其余写操作还需逐项核查。管理 Web 使用已确认的后台视觉方案和真实业务 API。用户最新决定是先完成 PC 三角色业务，暂缓微信小程序运行问题。

| 当前已核实 | 仍在整改 | 未验收 / 限制 |
| --- | --- | --- |
| **PC Web**：`npm run lint`、`format:check`、`typecheck`、`test:run`（14 文件 / 58 项）、`build` 均通过。真实浏览器烟测 9/9，覆盖三角色登录、管理员分页与七页签、用户订单/家庭/消息、人员接单开关切换并还原、未登录路由跳转。另在隔离库通过本轮真实写链：用户偏好新增/删除、提醒修改、预约；管理员派单、停用创建人并分别交接确认/评价/结算操作；服务人员接单/开始/提交完成；接收家属确认、模拟支付、独立支付服务登记成功并同步结算、提交评价；管理员撤销三项交接并恢复账号。新增订单为隔离验收数据，结束后已恢复数据库快照。<br><br>**后端与支付模块**：主服务 22 个测试套件 / 68 项、模拟支付 2 个套件 / 10 项均通过；测试连接专门新建的隔离 MySQL 测试库 `eldercare_test`，未触碰原有 `eldercare_test` 数据。<br><br>**Windows 本地运行**：`init-db.ps1` 在新建隔离 MySQL 8.0.29（3307）执行主库 V000–V011、支付库 V000–V002；`start.ps1` 三服务就绪、`stop.ps1` 与 8080/8081/5177 端口释放已验证。备份恢复脚本恢复 39 张表，两个库逐表行数一致。真实 PC 配置文件默认仍以 3306 为默认值；3307/5177 是本次临时验收值。<br><br>**独立 UI 模板**：`node qa/check.cjs` 122 项通过，浏览器运行错误 0；其数据为构造演示数据。 | 继续逐项核查 PC 管理端的业务写操作和需求覆盖，重点包括服务目录/区域维护、人员及排班、请假审批、服务异常与争议、家庭邀请与授权、线下结算和管理消息处理。当前核心订单链虽已通过，仍不能据此宣称所有 PC 功能均已验收。 | **微信小程序**：按用户最新决定暂缓运行排查；此前 `mp-weixin` 编译记录不代表当前能在微信开发者工具中运行，本阶段不把它作为 PC 验收条件。真机与正式发布也未验收。<br><br>**原有本机数据库**：原 ProgramData MySQL 数据目录及 3306 库未访问或修改。本轮 `.env` 使用隔离验收库的本地配置；不应将其端口/口令当作原数据库的有效配置。<br><br>**真实支付/邮件**：只验收本机独立模拟支付服务，不代表真实资金结算；真实 SMTP 未配置。 |

以下 2026-10-21 起的记录保留为历史证据；其中“本轮”“当前”等措辞只描述各自记录日期，不代表 2026-10-30 的现状。

| 已验收（日期与证据） | 进行中 | 未验证 |
| --- | --- | --- |
| **2026-10-21，主服务定向测试**：`mvn -o -q -Dtest=AdminBootstrapTest,TestDatabaseGuardTest,SettlementPaymentFailureTest test`，退出码 0；覆盖管理员新建/不重置/显式重置日志、测试库 URL 白名单，以及支付查询连接失败、超时、远端错误和恢复后重试。<br><br>**2026-10-21，模拟支付测试**：`mvn -o -q -Dtest=TransactionControllerTest test`，退出码 0；覆盖重复标识、同订单版本交易复用、金额冲突、失败后重试、终态限制、404、令牌错误和配置缺失。<br><br>**2026-10-21，前端检查**：`npm run test:run`（14 项通过）、`npm run build`、`npm run lint:admin`、`npm run format:check:admin`、`npm run typecheck` 均退出码 0；主 JS 包 326.84 kB。<br><br>**2026-10-21，Windows 脚本语法**：`scripts/` 下 8 个 PowerShell 文件均通过解析；`git diff --check` 通过。 | 需要用隔离 MySQL 验证迁移记录保护、测试账号权限与真实集成测试；需要用用户确认的本机数据库配置复核启动链路。前端页面拆分和新 Profile 行为还需真实登录态浏览器验收。 | **数据库**：未连接 127.0.0.1:3306；未检查现有库内容、Navicat 导入后的 `schema_migration`、运行/迁移账号权限，也未执行 `init-db.ps1`、备份或恢复。<br><br>**集成与 Windows 运行**：未执行完整 `scripts/test.ps1`；未用本轮代码实测三个程序启动、健康检查、停止和端口释放。此前在中文工作树路径启动临时 MySQL 8.0.29 失败，故没有可用的隔离测试实例。<br><br>**Profile 与浏览器**：`local`/`prod` 的实际应用启动、生产 Cookie、模拟支付入口关闭，以及三端业务浏览器流程均未在本轮实测。 |

本轮 Maven 命令的退出码均为 0；进程同时输出非致命的 `Access is denied.` 文本。完整集成验收需在允许的隔离数据库环境重新执行后再更新本表。

## 2026-10-22 前端重写与迁移

`frontend/` 已由 `D:/论文/eldercare-ui-vue` 全量迁移替换，旧实现归档在 `D:/论文/_backup/frontend-legacy-20261022`（源码 + 配置，不含 `node_modules`）。

| 项 | 证据 | 结果 |
| --- | --- | --- |
| 类型检查 | `npm run typecheck` | 退出码 0（strict + noUnused*） |
| Lint（全量） | `npm run lint` | 退出码 0；脚本由 `lint:admin` 扩为 `eslint src` |
| 格式（全量） | `npm run format:check` | 退出码 0；脚本由 `format:check:admin` 扩为 `prettier --check src` |
| 单测 | `npm run test:run` | 3 文件 / 17 例通过（client 7、status 6、session 4） |
| 构建 | `npm run build` | 退出码 0；JS 257.53 kB（gzip 87.23）、CSS 44.79 kB（gzip 8.28） |
| 运行时 | Playwright + 本机 Chrome，`family01` / `worker01` / `admin` 登录 | 14 个受保护路由渲染真实数据；`/user/notifications` 兼容重定向生效；404 兜底、`?senior=1` 生效 |
| 写操作 | 人员工作台接单开关 | `POST /api/worker/accepting` 成功，状态与提示一致，测试后已还原 |

变更要点：`/` 仍重定向 `/login`，旧 `/notifications` 路径保留为重定向，设计稿索引与规范页移到 `/dev`、`/dev/spec`；`@` 别名在 vite / tsconfig / vitest 三处一致；`package-lock.json` 补全了原先缺失的 eslint / prettier 依赖树（0 处版本变更）；Element Plus 依赖保留但新页面不引用。细节见 `frontend/README.md`。

## 2026-10-22 P0 缺陷修复

针对 `docs/code-review-2026-10-22.md` 的四项 P0：

| 项 | 结果 |
| --- | --- |
| 前端写死演示日期 | `utils/format.ts` 改为真实时间并提供 `todayIso/localDateTime/addDays/isPast/nowMs`；预约、提醒、请假默认值与 `min` 属性改为动态，提交前拦截过去时间 |
| 后端异常兜底 | `ApiExceptionHandler` 补齐 400/404/405/415/500 七类 handler + `Exception` 兜底，500 返回 `traceId` 且堆栈只进日志 |
| skip-link | 三个布局补 `<a class="skip" href="#main">` 与 `#main tabindex="-1"`，`StaffLayout` 内容容器改为语义 `main` |
| 全局渲染异常 | `main.ts` 注册 `errorHandler` 与 `unhandledrejection`，统一轻提示并去重 |

验证：`typecheck`、`lint`、`format:check`、`test:run`（17 例）、`build`（JS 259.28 kB / gzip 87.83）通过；后端 `mvn -o -q -DskipTests package` 通过，`SqlValuesTest` + `TestDatabaseGuardTest` 通过；curl 实测五类错误均返回 `{code,message,details}`；浏览器实测预约默认 `2026-10-23`、提醒 `2026-10-22T23:00`、请假 `2026-10-23 08:00–17:00`，skip-link 为 Tab 首站并成功跳转，未处理拒绝触发提示且页面不白屏。

未执行：`scripts/test.ps1` 全链路（需要 `TEST_DB_URL` 指向隔离测试库，当前 `.env` 未提供）。

## 2026-10-22 集成测试环境打通

- 建隔离库 `eldercare_test`（utf8mb4）与专用账号 `eldercare_test_app@127.0.0.1`，仅授予 `eldercare_test.*` 全部权限；已实测该账号查询 `eldercare.account` 被拒，权限边界正确。口令存 `.run/test-db.pass`，`.env` 写入 `TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD`。
- 关键坑：`TEST_DB_URL` 必须带 `allowMultiQueries=true`，否则 JDBC 不能一次执行多语句迁移，`IntegrationTestBase` 会在 `V001__identity.sql` 处抛 `ExceptionInInitializerError`，表现为 13 个集成测试「NoClassDefFoundError」。已补入 `.env` 并写进 README。
- `scripts/test.ps1` 首次全绿：backend mvn test 31 例（0 失败/0 错误，29.4s）、mock-payment 6.0s、frontend vitest 7.1s、frontend build 11.8s。
- 测试只作用于 `eldercare_test`；业务库 `eldercare` 复核仍为 6 个账号、16 条订单，未被清理。

## 2026-10-22 P1 第一批：分页统一 + 用户端入口

**后端分页契约**：新增 `common/PageQuery`、`common/PageResult`，8 个列表接口改为 `{items,page,size,total}`——`/admin/orders`（新增 `status`、`keyword` 服务端筛选）、`/admin/workers`、`/admin/incidents`、`/admin/disputes`、`/admin/reviews`、`/admin/identity-claims`、`/admin/family-disputes`、`/notifications`（新增 `kind=order|system`）。`size` 越界 400 `INVALID_PAGE_SIZE`，`page<1` 返回 400 `INVALID_PAGE`。

**前端**：新增 `PaginationBar`（首尾 + 当前邻域 + 省略号，`aria-current`），管理端 6 个页签与消息页接入；订单筛选从本地过滤改为服务端筛选（关键字 300ms 防抖），页签角标改用 `total`；异常/争议、核验/家庭争议拆成各自独立分页区块。

**用户端入口**：订单列表对 `COMPLETED` 订单提供「评价服务」，已评价显示只读结果（`POST /orders/{id}/review`）；家庭页新增「邀请家人」「撤销授权」「申请本人核验」「提交争议」，并对无 `can_manage_family` 权限的账号给出明确提示而不是空列表。

**修复**：`stores/family.ts` 的 `ageOf` 仍在用写死的 2026-10-21 计算年龄，改为真实当天。

**验证**：`curl` 实测订单第 1/2 页（total 16，页码与条目递增正确）、人员 `size=2` 分页；浏览器实测管理端「共 16 条 · 第 1/2 页」翻页后行内容变化、评价提交成功并出现在评价治理（order 61，5 星）、邀请弹层与 8 项权限勾选正常；后端 35 例测试（新增 `PaginationContractTest` 4 例）通过。

## 2026-10-23 P1 收尾：测试补齐、登录限流、列名枚举

| 项 | 改动 | 验证 |
| --- | --- | --- |
| 前端组件测试 | 新增 `PaginationBar.spec.ts`（7）、`AppDialog.spec.ts`（4）、`DataTable.spec.ts`（5）、`SeniorToggle.spec.ts`（2）：覆盖页码计算/禁用/`aria-current`、Esc 关闭、焦点接管与归还、`data-label` 卡片化、适老开关写入 `<html>` | `npm run test:run` 7 文件 / 35 例通过 |
| E2E 入库 | 新增 `e2e/smoke.mjs` 与 `npm run test:e2e`（`playwright-core` 仅 devDependency，驱动本机 Chrome，不下载浏览器）；覆盖三角色登录、管理端翻页、评价弹层、家庭授权区、接单开关可逆写、未登录重定向 | 9/9 项通过；默认只读，不创建订单/评价 |
| 登录限流 | 新增 `security/LoginThrottle`：按来源 IP 滑动窗口（5 分钟 15 次），超限 429 `LOGIN_RATE_LIMITED`；只用 `getRemoteAddr()`，不信任 `X-Forwarded-For`；成功登录清零 | `LoginThrottleTest` 4 例（阈值、来源隔离、清零、窗口过期） |
| 动态列名 | 新增 `family/PermissionColumn` 枚举，`FamilyService.requirePermission` 改为接收枚举，24 处调用点与 `OrderActionAccess.permission` 全部收敛，去掉运行时字符串白名单 | `PermissionColumnTest` 3 例；后端合计 42 例通过 |

后端测试 42 例（35 + 7）全过，`scripts/test.ps1` 四步全绿。`playwright-core` 为唯一新增依赖，属开发期测试工具，不进入运行时产物。

## 2026-10-23 P2 第一批：CI 与生产配置

| 项 | 内容 | 验证 |
| --- | --- | --- |
| 本地 CI | 新增 `scripts/ci.ps1`：后端测试、模拟支付测试、前端 lint / 格式 / 类型 / 单测 / 构建，共 7 步 | 7/7 PASS，总耗时 75 秒 |
| 远端流水线 | 新增 `.github/workflows/ci.yml`：backend job 用 MySQL 8 服务容器提供 `eldercare_test` 并生成 CI 专用 `.env`；frontend job 走 `npm ci` + 五项检查 | YAML 结构人工核对；触发后以 GitHub 结果为准 |
| 推送前拦截 | `.githooks/pre-push` + `scripts/pre-push.ps1` + `scripts/install-hooks.ps1`；已设置 `core.hooksPath=.githooks` | 5 项检查全 PASS，约 57 秒；`git hook run pre-push` 可复现 |
| 生产监听 | `application-prod.yml` 增加 `server.address` / `server.port`，由 `SERVER_ADDRESS`、`SERVER_PORT` 注入，默认仍只监听本机 | 配置项就位，prod 实际启动未验证 |
| 文档索引 | README 增加「持续集成与提交前检查」与「文档索引」两节，串联 8 份文档 | — |

**踩坑记录**：Windows PowerShell 5.1 会把无 BOM 的 UTF-8 脚本按 GBK 解码，中文注释的字节错位会吃掉引号与换行，脚本表现为「把源码打印出来」而不执行。用 Write 生成的 `.ps1` 必须补 UTF-8 BOM（仓库原有脚本均有 BOM）；`.githooks/pre-push` 这类 sh 脚本则必须**不能**带 BOM，否则 shebang 失效。

## 2026-10-23 P2 第二批：审计覆盖、待办慢查询、SQL 收敛

### 1. 安全审计补齐（迁移 V010）

原来只有家庭协作、人员启停、注册改密写 `audit_event`，**登录链路完全没有留痕**，事后无法回答「谁在什么时候从哪里试过」。新增 `common/AuditService` 作为唯一入口，并给表补 `ip`、`result` 两列：

| 覆盖点 | 事件 | 说明 |
| --- | --- | --- |
| 登录成功 | `LOGIN` / SUCCESS | 带来源 IP |
| 口令错误 | `LOGIN` / FAILURE | 带 actor 与 IP |
| 账号不存在 | `LOGIN` / FAILURE | actor 为空但不丢事件 |
| 账号锁定期 | `LOGIN` / BLOCKED | — |
| 来源被限流 | `LOGIN` / BLOCKED | 与 `LoginThrottle` 同一 IP 口径 |
| 争议处理、费用裁决、服务中断、补服务安排、请假审批、接单能力、排班、接单开关 | 各自独立 action | 原先这些高危操作无任何审计 |

`AuditService` 统一截断 `summary`（列宽 500）与 `ip`（45），超限直接截断而不是让业务事务回滚。

### 2. 待办查询慢查询

`/admin/todos` 原是一条 `SELECT DISTINCT` + 7 个 OR + 5 个 LEFT JOIN，执行计划实测：

| 版本 | type | key | Extra |
| --- | --- | --- | --- |
| 旧 | `ALL`（service_order 全表扫） | 无 | `Using temporary; Using filesort` |
| 新（单支路） | `ref` | `ix_order_status_id` | `Backward index scan; Using index` |

改法：按条件拆成 7 条 `UNION ALL` 支路，每条支路 `ORDER BY id DESC LIMIT 100` 提前收敛；同一订单命中多条时用 `ROW_NUMBER() OVER (PARTITION BY id ORDER BY priority)` 取优先级最高的一条，「原因」的判定顺序从隐式的 OR 顺序变成显式的 priority。新增迁移 V011 补 `service_order(status,id)` 索引。

附带语义收紧：原「服务人员已停用」条件不限订单状态，会把已完成的历史订单也算成待办；现在只统计在途状态（`WAITING_ASSIGNMENT` 至 `WAITING_CONFIRMATION`），与「需立即协调任务」的文案一致。

### 3. SQL 收敛到仓储层

控制器原本自己拼 SQL，同时承担鉴权、校验、装配、数据访问四件事。本轮新增 10 个仓储类，控制器内 SQL 归零（仅 `HealthController` 保留一句 `SELECT 1` 健康探针）：

| 模块 | 新增仓储 | 控制器原有 SQL 数 |
| --- | --- | --- |
| 看板与运营 | `dashboard/DashboardRepository` | 29 |
| 服务异常 | `orders/IncidentRepository` | 29 |
| 家庭协作 | `family/FamilyCoordinationRepository` | 23 |
| 服务目录 | `catalog/CatalogRepository` | 18 |
| 结算支付 | `settlement/SettlementRepository` | 12 |
| 推荐偏好 | `catalog/RecommendationRepository` | 9 |
| 评价 | `orders/ReviewRepository` | 6 |
| 站内信 | `notifications/NotificationRepository` | 6 |
| 生活提醒 | `notifications/ReminderRepository` | 4 |
| 用户账号 | `identity/UserAdminRepository` | 3 |

顺带修掉两个潜伏缺陷：

1. **权限枚举被直接拼进 SQL**：订单操作交接校验写成 `"g." + permission + "=1"`，`PermissionColumn` 没重写 `toString()`，实际生成的是 `g.VIEW_SERVICE=1` 这类列名，MySQL 会报未知列。改用 `permission.column()`，并新增 `FamilyCoordinationRepository.creatorHolds` 承载这条校验。此前无测试覆盖该分支。
2. **待办把已完成订单算进来**（见上节语义收紧）。

### 4. 测试与验证

| 项 | 结果 |
| --- | --- |
| 后端 | 50 例全过（新增 `AuditCoverageTest` 4 例、`DashboardTodoTest` 4 例） |
| `AuditCoverageTest` | 登录成功带 IP、口令错误记 FAILURE、账号不存在仍留痕、超长 summary 截断 |
| `DashboardTodoTest` | 中断单、人员停用单、逾期单各命中正确原因；多重命中只出一条且保留高优先级原因 |
| 开发库迁移 | `eldercare` 已应用 V010、V011 并登记 `schema_migration`；`audit_event.ip`/`result` 与新索引均已确认 |

**遗留**：`OrderService`（40 处）、`WorkerAdminService`（29 处）、`FamilyService`（17 处）仍在服务层直接写 SQL。服务层持有 SQL 不违反分层，但仍是后续可继续下沉的对象；`OrderService` 以事务编排为主，拆分需配套用例先行。

## 2026-10-29 整改进行中：已批准视觉方案

### 2026-10-30 UI 与跨端阶段记录（由上方 PC 验收结果更新）

- 已确认采用独立五页视觉方案，并将用户两张 Logo 适配资源接入预览、管理 Web 与 uni-app。管理端深色侧栏使用白色 Logo 处理，移动端浅色背景使用彩色图形标；Logo 原图保留。预览模板 `node qa/check.cjs` 本轮通过 122 项，浏览器运行错误 0。设计模板只保留在毕业设计根目录 `.tmp/` 作为视觉参考；PC Web 正式路由不包含模拟器或设计说明页。
- 管理 Web 页面已迁移到新布局与组件，保留并接入现有 API。当前核验：`npm run lint`、`npm run format:check` 通过；`npm run test:run` 14 个文件 / 56 项通过；`npm run build` 通过。
- `mobile/` uni-app 已拆为真实路由页面；登录后根据 USER / WORKER 角色请求后端并限制页面入口，管理员使用 PC Web。小程序构建默认指向本机开发服务，远程/手机运行需合法 HTTPS API 地址。尚未在真实服务与数据库上操作验收。本轮 `npm test`、`npm run typecheck`、`npm run build:h5`、`npm run build:mp-weixin` 均退出码 0。构建提示项目未配置 AppID；微信开发者工具未导入/操作。
- 移动订单页读取服务端订单与详情，可提交服务评价、读取结算状态并发起/同步模拟支付尝试；未在真实服务和数据库中验证写操作。
- 后端本轮定向测试 `mvn -o -q -Dtest=ElderUpdateTest,ReminderUpdateTest,UserOrderPaginationTest test` 退出码 0，共 10 项；它们使用 Mockito/MockMvc，不访问数据库。覆盖面不包括真实事务、安全过滤链、并发或端到端。
- 管理员人员排班现支持查看、新增、修改、删除；仅控制器/服务单测与前端交互测试覆盖，未在隔离 MySQL 中实测。异常协助等其他管理写操作及完整业务链仍需逐项核验，不能由页面构造数据代替。
- `scripts/test.ps1` 未运行。只读检查 `.env` 指向的 `eldercare_test` 与白名单一致，但该库已有 36 张表、共 409 行；集成测试会清空它。需先准备专用空隔离库并确认目标权限，再运行含数据库的完整测试。
- `start.ps1` 等交付脚本及本地配置已有代码改动，但本轮没有启动/停止服务、跑备份恢复或完整模拟支付链；Windows 本地部署仍未验收。微信开发者工具实际接口操作、真机与发布同样未验收。
- 根目录与 `eldercare-platform/AGENTS.md` 已明确新架构、材料保护、数据边界、模板确认和分阶段验收要求。原学术材料及既有未提交修改保持保护；未执行 Git 提交。

## 2026-10-29 历史进度：模板待用户确认

- 当时独立五页模板已融入两张用户提供的 Logo；视觉方案现已获确认。模板入口：毕业设计根目录 `.tmp/20261028-ui-template/site/index.html`，仅包含构造演示数据，不连接后端。
- 原图未更改。预览适配副本 `site/assets/eldercare-logo-mark.png` 用于移动首页和服务人员页，`site/assets/eldercare-logo-horizontal.png` 用于管理端侧栏；对透明留白进行了裁切，并去除极低透明度像素。名称为资源名称，未另定商业品牌。
- 重新运行 `node qa/check.cjs`：122 项断言通过，运行错误 0，包括四项图片加载检查和原有模板流程、响应式检查。已查看 390px 首页与桌面派单截图；这不是小程序、后端或真实业务验收。
- 本次未修改平台应用源码或两份 AGENTS.md。模板获确认后，再按已确认计划继续规则修订、部署与业务整改、小程序适配和验收；PC 派单表格原生单选的点击区域仍需在正式实现时改进。

## 2026-10-28 历史暂停点：独立 UI 模板与 Logo 素材

**以下为当时暂停时的记录；当前状态以上方 2026-10-29 记录为准。**

### 已确认的实施边界

- 以任务书、开题报告的 Windows 本地完整交付为基线，新增微信开发者工具演示；暂不承担公网发布、真实支付或正式小程序审核。
- 管理员保留 Vue Web；用户与服务人员共用一个 uni-app（Vue 3/TypeScript）工程，输出 H5 与微信小程序；老人自用优先，提供标准/适老显示。
- 保留独立模拟支付、Session＋CSRF、家庭授权、订单状态机、事务与并发约束。收起额外核验/争议入口但保留代码和数据，保留管理员异常交接补救。遵循 ponytail（full）与 impeccable，优先复用，最小完整改动。
- 任务书要求中的建档/资料维护、家庭邀请接受、规则推荐、预约订单可见、管理写操作、结算和提醒修改等缺口尚待整改；本次模板通过不代表原平台已完整交付。

### 本次已完成与尚未完成

- 独立五页交互模板已生成，位置以毕业设计根目录为基准：`.tmp/20261028-ui-template/site/index.html`；同目录 `app.css`、`app.js` 是零新增依赖的预览实现。覆盖老人首页、分步预约、订单结算、人员任务和 PC 派单，所有数据均为构造演示数据，不连接真实 API。
- 本地浏览器已打开该 HTML；直接打开文件即可预览，不依赖本地 HTTP 服务。所有预览、截图和 QA 文件保留在本任务 `.tmp/` 内，供后续审阅，不在本次暂停时删除。
- 模板 QA：`node qa/check.cjs`（工作目录 `.tmp/20261028-ui-template`）最近退出码 0，118 个断言通过，浏览器运行错误 0。覆盖演示预约到结算的交互链、支付查询超时保持处理中、线下登记不覆盖已结算、375/390/430 标准与适老显示、加载/空/错误/长文案、导航常驻与六处 44px 控件尺寸。这些是模板断言，不是后端或真实小程序验收。
- 独立评审已确认底部导航、SVG 关闭图标及六处控件尺寸修复解决；最新 `ship` 只覆盖这些修复与回归复核，不等于真实平台验收或用户已批准视觉方案。
- 临时产品事实与方向合同已保存于本任务 `PRODUCT.md` 和 `.impeccable/surfaces/preview.md`。设计规范 `DESIGN.md`/`.impeccable/design.json` 的收尾代理已按暂停指令停止，恢复时需核对完成状态；PC 表格原生单选的点击区域亦留待后续适配检查，不将小圆圈尺寸当作触控规范。
- 本次未替换平台界面，未修改应用源码或两份 AGENTS.md，未安装微信开发者工具，未更改数据库，也未运行完整后端/业务链验收。本文件仅因用户明确要求保存进度而追加此暂停点。已有未提交修改须保护。

### 用户新增的两张 Logo：仅定位，尚未处理

以下原图均在毕业设计根目录，已只读查看，未移动、重命名、裁剪或转换：

| 原文件 | 已核实素材 | 恢复后拟用途与资源名（尚未执行） |
| --- | --- | --- |
| `ChatGPT 图像 2026年10月28日 20_54_38-1.png` | PNG，1448×1086，RGBA，包含透明像素；房屋/人物图形＋“居家养老 / 智能生活服务平台”文字的横向组合标 | 管理端侧栏、较宽页头等；适配资源拟名 `eldercare-logo-horizontal.png` |
| `ChatGPT 图像 2026年10月28日 20_54_39-2.png` | PNG，1254×1254，RGBA，包含透明像素；房屋/人物独立图形标 | 移动页头、紧凑标识及后续小程序图标适配参考；适配资源拟名 `eldercare-logo-mark.png` |

原图 SHA-256：横版 `e634f528ec1972075a168d1fec7f45d97873e98ea472ecd57c847f96a7852de3`；图形版 `78bd9b033207f01a4f3b4835b144ac561e74a0249311a46fd9731eddf6b4efb0`。

- 用户要求两张 Logo 融入新 UI、命名，必要时允许另生成适配格式；本次只记录要求，等“开始”后处理。命名暂按资源文件名理解；若用户指品牌名称，恢复时再明确，不自行更改项目或平台名称。
- 已有透明通道，恢复时先检查留白、边缘杂色、深浅背景对比及小尺寸识别；优先保持原设计并适配，不直接覆盖原图，不把位图套入 SVG 冒充矢量化。
- 恢复顺序：核对此暂停点和临时文件 → 将 Logo 纳入品牌约束并给出原名/适配名/用途映射 → 按需生成适配副本并融入五页模板 → 重新检查与完成临时设计规范 → 用户确认模板 → 才继续两份 AGENTS.md、部署修复、后端补缺、uni-app 迁移及完整验收。后续正式工程状态仍统一更新本文件，不改写历史实施计划。
