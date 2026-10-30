# 居家养老智能生活服务平台 · 前端（frontend）

本目录已按「静态高保真稿 → Vue 工程 → 后端联调」的既定路线完成重写，并取代原 `frontend` 实现。
重写来源为 `D:/论文/eldercare-ui-vue`（**已弃用**，源码与截图归档在 `D:/论文/_backup/eldercare-ui-vue-legacy-20261022`），旧 `frontend` 实现归档在 `D:/论文/_backup/frontend-legacy-20261022`。

- 技术栈：**Vue 3.5 + TypeScript + Vite 6 + Vue Router 4 + Pinia 3**（与 `package.json` 既有版本一致，未升降级）
- 依赖：运行时仍只有 `vue / vue-router / pinia / element-plus`（Element Plus 按技术栈保留，新页面不引用，产物不含它）
- 视觉：`src/styles/tokens.css`（唯一令牌源）+ `src/styles/app.css`（组件类、页面类、响应式、适老降级）
- 数据：全部走真实后端（Spring Boot `127.0.0.1:8080`），HttpSession Cookie + `X-CSRF-TOKEN`
- 接口清单：`docs/API-CONTRACT.md`

---

## 一、运行方式

**前置条件**

1. MySQL 8 已启动，`eldercare` 库已初始化并有演示数据；
2. 后端已启动：`GET http://127.0.0.1:8080/api/health` 返回 `PROCESS_UP`（启动方式见仓库根 `scripts/start.ps1`）。

**命令**

```bash
cd eldercare-platform/frontend
npm install          # 首次或依赖变动后
npm run dev          # http://127.0.0.1:5173，/api 已代理到 8080
npm run build        # vue-tsc --noEmit + vite build，产物在 dist/
npm run preview      # 预览构建产物
npm run typecheck    # 只跑类型检查
npm run test         # vitest watch
npm run test:run     # vitest 单次（CI / scripts/test.ps1 使用）
npm run test:e2e     # 端到端冒烟（需先启动后端与 dev server）
npm run lint         # eslint src，全量
npm run format:check # prettier --check src，全量
npm run format       # prettier --write src
```

**演示账号**（密码在仓库根 `.env`：管理员用 `BOOTSTRAP_ADMIN_PASSWORD`，其余用 `DEMO_ACCOUNT_PASSWORD`）

| 角色 | 账号 | 登录后落点 |
|---|---|---|
| 家属 | `family01` | `/user` |
| 服务人员 | `worker01` | `/worker` |
| 管理员 | `admin` | `/admin` |

未登录访问受保护路由会跳 `/login?redirect=…`；角色不符会回到各自工作台。

**路由表**

| 地址 | 说明 |
|---|---|
| `/` | 重定向到 `/login`（沿用旧契约） |
| `/login` · `/register` | 认证页 |
| `/user` | 用户端首页（顶栏六分区） |
| `/user/services` | 服务预约（分步弹层） |
| `/user/orders` | 我的订单（分组 + 详情抽屉） |
| `/user/family` · `/user/reminders` · `/user/messages` | 家庭管理 / 生活提醒 / 消息 |
| `/worker` · `/worker/leave` · `/worker/messages` | 人员工作台 / 请假 / 消息 |
| `/admin` · `/admin/operations` · `/admin/messages` | 管理端工作台 / 服务运营六页签 / 消息 |
| `/user/notifications` · `/worker/notifications` · `/admin/notifications` | 旧路径，308 重定向到对应 `/messages` |
| `/dev` · `/dev/spec` | 已从产品路由移除；对应设计参考源文件未删除，不参与运行入口 |
| 任意未知路径 | 404 兜底页（带「返回工作台」） |

查询参数：`?open=booking` 直接打开预约弹层；`?panel=catalog|workers|exceptions|reviews|coordination` 直接切服务运营页签；`?senior=1` 以适老模式打开并写入本地偏好。

---

## 二、目录结构

```
frontend/
├─ index.html                 # #app 挂载点，theme-color 已改暖白 #faf9f7
├─ vite.config.ts             # vue 插件 + @→src 别名 + /api 代理 + 5173
├─ vitest.config.ts           # happy-dom + @→真实 src 路径
├─ tsconfig.json              # strict + bundler + paths(@/*) + noUnused*
├─ eslint.config.js           # flat config（保留旧配置）
├─ .prettierrc.json           # 保留旧规范：单引号、无分号、printWidth 110
├─ package.json               # 依赖不变，脚本扩为全量 lint/format
├─ docs/API-CONTRACT.md       # 接口清单（请求参数 / 响应结构 / 差异说明）
└─ src/
   ├─ main.ts                 # createApp + Pinia + Router，挂载前初始化适老模式与 401 拦截
   ├─ App.vue                 # <RouterView /> + 全局轻提示
   ├─ env.d.ts
   ├─ api/                    # client.ts（CSRF/超时/401/错误归一）· endpoints.ts · types.ts
   ├─ router/index.ts         # 25 条路由记录 + 404 兜底 + 角色守卫
   ├─ layouts/                # UserLayout · StaffLayout（按 role 生成侧栏）· AuthLayout
   ├─ views/                  # 15 个视图：只组合组件 + 读 store
   │  ├─ auth/ user/ worker/ admin/
   │  └─ IndexView · SpecView · NotFoundView
   ├─ components/ui/          # 23 个可复用组件（见第三节）
   ├─ stores/                 # 10 个 Pinia store（见第四节）
   ├─ constants/status.ts     # 状态中文名与 tone 的唯一来源
   ├─ types.ts                # 领域类型
   ├─ utils/format.ts         # 金额 / 日期 / 时长 / 首字
   ├─ styles/                 # tokens.css（令牌）· app.css（组件与页面）
   └─ test/setup.ts           # vitest 全局清理
```

分层规则：**layout 管外壳，view 管组合，component 管呈现，store 管状态**。view 中不出现十六进制色值、固定字号，也不出现 `querySelector` / `addEventListener`。

---

## 三、组件划分（`src/components/ui`，23 个）

| 分组 | 组件 | 关键能力 |
|---|---|---|
| 操作 | `AppButton` | variant(primary/secondary/ghost/danger/outline-danger) × size × block/compact/loading/disabled；`to` 时渲染 RouterLink |
| | `AppSwitch` | `button[aria-pressed]`，颜色 + 文案双编码，可键盘操作 |
| | `SeniorToggle` | 适老开关，直接读写 `ui` store |
| 表单 | `AppField` | label + 控件 + 帮助 + 错误四段式，`useId()` 生成 `aria-describedby` |
| | `AppInput` / `AppSelect` / `AppTextarea` | 受控控件，透传原生属性与 `invalid` |
| 数据 | `DataTable` | 泛型 `T`、表头 sticky、数字右对齐、`data-label` 驱动的窄屏/适老卡片化 |
| | `StatCard` | KPI 卡，`tone=action` 时左侧橙条 |
| | `StatusTag` / `AppTag` | 六本状态字典统一取文取色 |
| 状态 | `EmptyState` / `ErrorBanner` / `SkeletonBlock` | 空 / 错误 / 加载三态，全站唯一写法 |
| 覆盖层 | `AppDialog` / `AppDrawer` | Teleport、遮罩、Esc、焦点接管与归还 |
| | `AppTabs` / `AppSegmented` | `role=tablist` + 方向键；分段筛选 `aria-pressed` |
| 结构 | `PageHeader` / `SectionCard` | 页头、带标题卡片，均含 `#actions` 插槽 |
| 品牌 | `BrandMark` / `AppIcon` | 屋顶 + 人形 + 橙色核心；28 个内联 SVG（路径表在 `icons.ts`） |
| 反馈 | `AppToast` | 写操作轻提示（3 秒自动消失） |

---

## 四、状态管理（`src/stores`，10 个）

| store | 数据来源 | 关键动作 |
|---|---|---|
| `ui` | 本地偏好 | `init() set() toggle() notify()`（唯一写 `<html data-senior>` 的地方） |
| `session` | `/auth/me` `/auth/login` `/auth/logout` | `load() login() logout() clear()` |
| `dashboard` | `/user/dashboard` | `load()` |
| `catalog` | `/catalog/items` `/catalog/items/:id/areas` | `load() filter() itemAreas()` |
| `orders` | `/orders` `/orders/:id` + 写操作 | `load() group() openDetail() confirm() cancel()` |
| `family` | `/elders` `/elders/:id/permissions` 等 | `load() select()` |
| `tasks` | `/worker/*` | `load() setAccepting() act() reject() interrupt() requestLeave()` |
| `messages` | `/notifications` | `load() setFilter() markRead() markAllRead()` |
| `reminders` | `/reminders` | `load() create() stop()` |
| `admin` | `/admin/*` | `loadOverview() loadAll() loadCandidates() assign() openDetail()` |

每个 store 都有 `loading` 与 `error`，视图据此渲染「骨架 / 空态 / 错误条 + 重新加载」。所有计数与角标均由数据推导，没有写死数字。

---

## 五、迁移映射

**保留（原 `frontend` 的资源，未随重写丢失）**

| 文件 / 目录 | 处理 |
|---|---|
| `node_modules/` | 复用，仅补齐缺失包 |
| `package-lock.json` | 保留；本次补全了原先缺失的 eslint / prettier 依赖树条目（见第六节） |
| `package.json` | 依赖原样保留；脚本 `lint:admin` / `format:check:admin` 扩为全量 `lint` / `format:check`，新增 `lint:fix` / `format` |
| `vitest.config.ts` | 保留，追加 `@` 别名（测试在 Node 中运行，必须解析到真实路径） |
| `eslint.config.js` · `.prettierrc.json` | 原样保留，作为全量 lint/format 的依据 |
| `src/test/setup.ts` | 原样保留（vitest `setupFiles` 依赖它） |
| `index.html` | 保留结构，`theme-color` 由 `#f7f8f6` 改为暖白 `#faf9f7` |

新增开发依赖 `playwright-core`（约 9 MB，仅 devDependencies，不进构建产物）：端到端冒烟需要它驱动本机 Chrome，不下载浏览器二进制。若不想安装，`npm ci --omit=dev` 与 `npm run test:run` 均不受影响。

**替换**

| 旧 | 新 |
|---|---|
| `src/**`（13 个视图 + `components/common` + `composables` + `layouts` + 单 store） | `src/**`（15 个视图 + `components/ui` + 3 layouts + 10 stores + `api/`） |
| `vite.config.ts`（仅 proxy） | 追加 `@ → /src` 别名与 `server.host/port` |
| `tsconfig.json` | 追加 `paths(@/*)`、`noUnusedLocals/Parameters`、`src/**/*.d.ts` |
| `views/admin/AdminOperationsView.vue` + 6 个 `components/*Panel.vue` | `views/admin/OperationsView.vue`（单页六页签，页签内为真表格） |
| `composables/admin/useAdminOperations.ts`（`toRefs(reactive(...))`） | `stores/admin.ts`（Pinia setup store，接口调用一一对应） |
| `styles/{tokens,base,senior}.css` | `styles/{tokens,app}.css`（适老覆盖仍在同一令牌源内） |
| `types/api.ts` | `api/types.ts`（按真实后端响应重塑） |

**新增**

`api/{client,endpoints,types}.ts`、`components/ui/*`、`layouts/{User,Staff}Layout.vue`、`stores/*`、业务视图、404 路由及 `docs/API-CONTRACT.md`。`IndexView.vue` 与 `SpecView.vue` 仅为保留的设计参考源码，不注册为产品路由。

**废弃（已归档，不再参与构建）**

`src/composables/`、`src/components/common/`、`src/views/admin/components/`、旧同名 views、`styles/base.css`、`styles/senior.css`、`types/api.ts`、`views/admin/AdminOperationsView.spec.ts`（测试对象已不存在）。

---

## 六、冲突处理

1. **路由契约**：`/` 重定向 `/login`；设计辅助页不注册到产品路由；旧的三处 `/notifications` 全部保留为 redirect 到 `/messages`，避免外部链接与书签失效。新增 `/worker/leave` 等只增不改。
2. **`@` 别名三处一致**：`vite.config.ts`（`/src`）、`tsconfig.json`（`paths`）、`vitest.config.ts`（`fileURLToPath`）。vitest 必须用真实路径，否则单测报 `Failed to resolve import "@/..."`。
3. **测试**：原 4 个 spec 中，`client` / `status` / `session` 三个纯逻辑测试按新模块重写（17 例通过）；`AdminOperationsView.spec.ts` 随被替换的视图一起退役，归档在备份目录。
4. **Element Plus**：`AGENTS.md` 把 Element Plus 列为技术栈，因此依赖与锁文件不动；新页面零引用，构建产物自然不含它（旧包 326.84 kB → 现 257.53 kB）。
5. **锁文件修复**：原 `package-lock.json` 缺少 `eslint`、`prettier` 及其依赖树条目（`node_modules` 里也确实没有），`npm run lint` 会直接报「找不到 eslint」。本次执行 `npm install` 补齐 1377 行，**0 处版本变更**（`vue` 仍 3.5.18），`npm ci` 现在能正确还原。
6. **样式三层合并为两层**：旧 `base.css` 与 `senior.css` 的规则已并入 `app.css`，令牌仍是唯一来源；`.btn` / `.field` / `.tag` / `.table` 类名保持不变，仅由新组件消费。
7. **格式统一**：新代码沿用仓库 `.prettierrc.json`（单引号、无分号、printWidth 110、`vueIndentScriptAndStyle`），迁移后对 `src` 全量 `prettier --write`，否则 `format:check` 会因 63 个文件失败。

---

## 七、验证记录（2026-10-22 迁移后实测）

| 项 | 命令 / 方式 | 结果 |
|---|---|---|
| 类型检查 | `npm run typecheck` | 通过（strict + noUnused*，0 error） |
| Lint | `npm run lint` | 通过（`eslint src --max-warnings=0`） |
| 格式 | `npm run format:check` | 通过（全量 63 个文件已格式化） |
| 单测 | `npm run test:run` | 7 个文件 / 35 例通过（含 PaginationBar 7、AppDialog 4、DataTable 5、SeniorToggle 2） |
| 端到端 | `npm run test:e2e` | 9 项全通过（三角色登录、翻页、评价弹层、家庭授权区、接单开关可逆写、未登录重定向） |
| 构建 | `npm run build` | 通过，JS 257.53 kB（gzip 87.23）、CSS 44.79 kB（gzip 8.28） |
| 启动与代理 | `npm run dev` + `GET /api/health` | 5173 返回 200，代理返回 `PROCESS_UP` |
| 三角色登录 | Playwright + 本机 Chrome，`family01` / `worker01` / `admin` | 14 个受保护路由全部渲染真实数据，无运行时报错与「加载失败」文案 |
| 旧路径兼容 | 访问 `/user/notifications` | 落到 `/user/messages`，内容正常 |
| 公开行为 | `/`、`/nope/deep`、`/login?senior=1` | 分别重定向登录、命中 404 兜底、`data-senior="on"` 且字号放大 |
| 写操作 | 人员工作台接单开关 | `POST /api/worker/accepting` 成功，按钮状态翻转、提示「已暂停接单，不再接收新派单」，随后已还原 |
| 辅助页 | `/dev`、`/dev/spec` | 索引与规范总览正常渲染 |

> 运行时仅剩两类预期内的控制台信息：登录前 `/api/auth/me` 的 401 探活、以及浏览器 favicon 请求（页面已用 `data:` 占位）。

---

## 八、回滚

```bash
# 1) 源码回滚（含配置与旧测试）
cd eldercare-platform/frontend
rm -rf src
cp -r "D:/论文/_backup/frontend-legacy-20261022/src" ./src
cp "D:/论文/_backup/frontend-legacy-20261022"/{vite.config.ts,tsconfig.json,vitest.config.ts,package.json,index.html} ./

# 2) 依赖与产物
npm install && npm run build
```

迁移前的整仓库改动快照另存于 `eldercare-platform/.run/before-frontend-migration.patch`。
