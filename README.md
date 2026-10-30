# 居家养老智能生活服务平台

本项目仍在整改中：PC 三角色界面、隔离 MySQL、后端和独立模拟支付服务已完成本机集成验收；真实浏览器已通过预约、派单、履约、家庭授权交接、用户确认、模拟支付结算和评价的核心闭环，并验证偏好维护、提醒修改与账号停用恢复。管理端其余目录、排班、异常和消息处理等写操作仍需逐项验收。微信小程序运行问题按用户最新决定暂缓，不作为 PC 验收前置条件。项目不标记为完整交付；当前进度与验证范围见 [docs/STATUS.md](docs/STATUS.md)。

## 技术栈与模块

- PC Web：`frontend/` 使用 Vue 3、TypeScript、Vite、Vue Router、Pinia，提供登录及用户、服务人员、管理员业务工作台。
- 微信小程序 / 移动 H5：`mobile/` 使用 uni-app、Vue 3 与 TypeScript，独立于 PC Web 编译；用户与服务人员页面通过同一 REST API 和会话认证访问平台数据。
- 主服务：Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus、JdbcTemplate。
- 独立模拟支付服务：Spring Boot；只在 `local` Profile 开放模拟交易 API 与控制台，不处理真实资金。
- 数据库：MySQL 8；账号认证使用 MyBatis-Plus，其余业务主要使用 `JdbcTemplate`。

平台包含用户、服务人员、管理员角色。PC Web 与 uni-app 使用同一 Spring Boot API；业务权限和数据归属由后端校验。登录与业务数据取自当前配置的服务端和数据库，测试桩只用于自动化测试。模拟支付用于本机演示流程，不代表真实支付能力。演示数据库不得录入真实老人资料。

## 环境与私密配置

需要 JDK 21、Maven 3.9+、Node.js 20.19+、22.13+ 或 24+、npm、PowerShell 与 MySQL Server 8.x 及命令行工具。复制 `.env.example` 为项目根目录 `.env`，填写主服务与模拟支付的运行账号、管理员初始密码、演示账号密码和两个支付令牌；初始化、备份与恢复另需迁移账号，集成测试另需测试账号。`MYSQL_PORT` 和 `VITE_PORT` 默认分别为 `3306`、`5173`，仅在本机端口被占用或保留时修改。`.env` 已被 Git 忽略，禁止提交。

运行服务的数据库用户不使用 MySQL `root`，每个服务只拥有自己数据库的业务读写权限。可在 Navicat 以数据库管理员执行下列语句；先把密码替换为各自独立的强口令，再执行：

```sql
CREATE DATABASE IF NOT EXISTS eldercare CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS eldercare_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS eldercare_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS eldercare_payment_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'eldercare_app'@'127.0.0.1' IDENTIFIED BY '替换为主服务独立强口令';
GRANT SELECT,INSERT,UPDATE,DELETE ON eldercare.* TO 'eldercare_app'@'127.0.0.1';

CREATE USER 'eldercare_payment_app'@'127.0.0.1' IDENTIFIED BY '替换为支付服务独立强口令';
GRANT SELECT,INSERT,UPDATE,DELETE ON eldercare_payment.* TO 'eldercare_payment_app'@'127.0.0.1';

CREATE USER 'eldercare_migrator'@'127.0.0.1' IDENTIFIED BY '替换为迁移账号独立强口令';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,DROP,INDEX,REFERENCES ON eldercare.* TO 'eldercare_migrator'@'127.0.0.1';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,DROP,INDEX,REFERENCES ON eldercare_payment.* TO 'eldercare_migrator'@'127.0.0.1';

CREATE USER 'eldercare_test_runner'@'127.0.0.1' IDENTIFIED BY '替换为测试账号独立强口令';
GRANT ALL PRIVILEGES ON eldercare_test.* TO 'eldercare_test_runner'@'127.0.0.1';
GRANT ALL PRIVILEGES ON eldercare_payment_test.* TO 'eldercare_test_runner'@'127.0.0.1';
```

`.env` 中的 `DB_USER`/`DB_PASSWORD`、`PAYMENT_DB_USER`/`PAYMENT_DB_PASSWORD`、`DB_MIGRATION_USER`/`DB_MIGRATION_PASSWORD` 和 `TEST_DB_USER`/`TEST_DB_PASSWORD` 分别对应上述账号。迁移与测试账号不得用于应用运行；测试账号只应获授两个 `_test` 库权限。以上 SQL 是账号权限模板，不要把示例文字当成密码执行。

## 数据库初始化

`scripts/init-db.ps1` 使用迁移账号，只处理已由管理员预建的 `eldercare` 与 `eldercare_payment`，不会创建数据库，也不使用 `root`。新建的空库可按版本顺序初始化：

```powershell
./scripts/init-db.ps1
```

如果已通过 Navicat 导入过 SQL，先完整备份并核对 `schema_migration` 与实际表结构，再运行初始化脚本。脚本发现迁移记录为空/不连续/未知、或某个版本对应的结构已经存在但未登记时会停止，不会盲目重放迁移。此时不要重复导入 SQL，也不要直接补写版本号；先逐项确认数据库结构确实与相应版本完全一致，再由数据库管理员按已核对的版本人工修复迁移记录。V008 会改动账号和家庭邀请表，支付库 V002 会增加交易唯一键，尤其不能靠猜测做基线登记。

## 本机启动

在项目根目录的 PowerShell 运行：

```powershell
./scripts/check-env.ps1
./scripts/start.ps1
```

`start.ps1` 显式使用 `local` Profile，只把每个服务需要的运行凭证传给对应进程；迁移/测试凭证不会传给应用，数据库密码也不会传给前端。默认从当前源码重新构建两个服务，缺少前端依赖时执行 `npm ci`；只有显式传入 `-SkipBuild` 才复用已有 JAR。前端入口为 `http://127.0.0.1:<VITE_PORT>`（默认 `5173`），模拟支付控制台为 `http://127.0.0.1:8081/console.html`，仅限本机使用。启动脚本会检查端口占用，并验证新启动进程存活，避免误用其他进程的健康响应。停止服务：

```powershell
./scripts/stop.ps1
```

PC Web 与移动端分别运行；移动 H5 使用独立的 5174 端口，PC Web 使用 5173：

```powershell
cd frontend
npm ci
npm run dev
```

另开终端运行 uni-app H5（首次在 `mobile/` 执行 `npm ci`）：

```powershell
cd mobile
npm ci
npm run dev:h5
npm run build:h5
```

微信小程序单独构建：

```powershell
cd mobile
npm run build:mp-weixin
```

小程序输出位于 `mobile/dist/build/mp-weixin`，可在微信开发者工具导入该目录。`mobile/src/manifest.json` 需填写开发者的 AppID；默认调试 API 地址为 `http://127.0.0.1:8080/api`，只适合本机开发者工具调试。使用远程环境时，在构建终端设置 `VITE_API_BASE_URL=https://<已配置域名>/api` 后执行小程序构建；手机预览和发布还需在微信公众平台配置 request 合法域名。当前已验证源代码类型检查与 H5/小程序构建，微信开发者工具导入、真实服务接口操作、真机与发布尚未验收。

直接运行 JAR 时需自行向进程提供该服务的环境变量，并显式指定 Profile，例如 `java -jar <jar> --spring.profiles.active=local`。部署配置使用 `prod`；生产会话 Cookie 设置 `Secure`、`HttpOnly` 和 `SameSite=Lax`，演示数据及模拟支付关闭。生产支付令牌没有默认值，也不能把本地模拟支付当作真实支付能力。

默认不会重置已有管理员密码。只有需要主动重置时，才在当前 PowerShell 会话临时设置开关后启动；`start.ps1` 会消费并清除该开关：

```powershell
$env:BOOTSTRAP_ADMIN_RESET = 'true'
./scripts/start.ps1
```

管理员初始密码必须自行设置。演示账号统一密码由 `DEMO_ACCOUNT_PASSWORD` 自行设置，不在仓库中提供默认口令；演示数据通过本地 Profile 写入，关闭后不会由默认配置写入。

## 测试

集成测试只接受 `TEST_DB_URL` 指向 `eldercare_test`，并在该库内删除和重建表后执行迁移；它不删除数据库。运行 `scripts/test.ps1` 前，须预建隔离测试库并为测试账号授予仅限 `_test` 库的权限。脚本会先检查目标库名，Java 测试代码还会再次检查 URL、白名单与实际连接 catalog。没有合适测试账号或数据库时，不要改用 root 或正式库。

建库与授权（用管理员账号执行一次，密码自行替换）：

```sql
CREATE DATABASE IF NOT EXISTS eldercare_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS 'eldercare_test_app'@'127.0.0.1' IDENTIFIED BY '<测试账号口令>';
GRANT ALL PRIVILEGES ON eldercare_test.* TO 'eldercare_test_app'@'127.0.0.1';
```

`.env` 中对应三项，注意 URL 必须带 `allowMultiQueries=true`——迁移脚本是多条语句，JDBC 默认不允许一次执行，缺这个参数会在 `V001__identity.sql` 处报「执行迁移失败」：

```text
TEST_DB_URL=jdbc:mysql://127.0.0.1:3306/eldercare_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowMultiQueries=true
TEST_DB_USER=eldercare_test_app
TEST_DB_PASSWORD=<测试账号口令>
```

```powershell
./scripts/test.ps1
```

该脚本依次运行主服务测试、模拟支付测试、Web 测试与构建，以及跨端客户端测试、类型检查和 H5/小程序构建。真实 3306 环境需要另行确认目标库、备份并检查迁移状态；`check-env.ps1` 只检查工具与配置，不代表数据库连接或启动验收通过。

### 持续集成与提交前检查

| 场景 | 入口 | 内容 |
| --- | --- | --- |
| 本地完整校验 | `./scripts/ci.ps1` | 后端测试、模拟支付测试、前端 lint / 格式 / 类型 / 单测 / 构建 |
| 推送前拦截 | `./scripts/install-hooks.ps1`（一次性） | 前端 lint / 格式 / 类型 / 单测 + 后端编译，约 30 秒；`git push --no-verify` 可临时跳过 |
| 远端流水线 | `.github/workflows/ci.yml` | 同上，MySQL 8 服务容器提供 `eldercare_test`；前端单测与构建 |
| 端到端冒烟 | `cd frontend && npm run test:e2e` | 真实浏览器 + 真实后端，需先启动两端；默认只读，不写入订单与评价 |

端到端冒烟需要本机 Chrome（`E2E_CHROME` 可指定路径），因此只在本地运行，不进入 GitHub 流水线。

## 备份与恢复

备份和恢复使用独立迁移账号，备份保存在被 Git 忽略的 `.run/backups/`。恢复会覆盖目标库中的同名表，必须先停止服务、核实文件和目标数据库，再显式确认：

```powershell
./scripts/backup.ps1
./scripts/restore.ps1 -MainBackup '业务库备份文件路径' -PaymentBackup '交易库备份文件路径' -ConfirmRestore
```

本机 3306 的初始化、健康检查、启动停止和备份恢复目前仍待真实验收，详见 [docs/STATUS.md](docs/STATUS.md)。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/STATUS.md](docs/STATUS.md) | 当前验收状态与每次变更的证据 |
| [docs/api-contract.md](docs/api-contract.md) | 接口基线：认证、分页、错误结构、命令式状态变更 |
| [docs/state-machines.md](docs/state-machines.md) | 订单、派单、结算、通知的合法与拒绝路径 |
| [docs/permission-matrix.md](docs/permission-matrix.md) | 角色与家庭授权矩阵 |
| [docs/domain-and-data-model.md](docs/domain-and-data-model.md) | 领域对象与表结构说明 |
| [docs/code-review-2026-10-22.md](docs/code-review-2026-10-22.md) | 代码审查结论与 P0/P1 修复记录 |
| [frontend/README.md](frontend/README.md) | 前端目录结构、组件划分、运行与验证方式 |
| [frontend/docs/API-CONTRACT.md](frontend/docs/API-CONTRACT.md) | 前端视角的接口清单与请求/响应字段 |
