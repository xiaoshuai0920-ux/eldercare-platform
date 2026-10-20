# 居家养老智能生活服务平台

Windows 本地项目：Vue 3 前端、Spring Boot 主业务服务、独立模拟支付服务、MySQL 8。工程规则见 `AGENTS.md`；已验证范围和未完成事项见 `docs/implementation-plan.md`。正常订单、主要异常与结算流程已在隔离 MySQL 中实测，真实浏览器已走通预约至模拟结算；正常 3306 与真实邮件仍未验收，目前不应作为正式业务系统使用。

## 准备环境

需要 JDK 21、Maven 3.9+、Node.js 20+、PowerShell 7、MySQL Server 8.x 与 MySQL 命令行工具。先运行 `./scripts/check-env.ps1`。两个服务的 `application.yml` 固定连接本机 `127.0.0.1:3306`，均以 `root` 登录；业务时区为 `Asia/Shanghai`。如果 MySQL 使用其他端口，需要同时修改两份配置及 `scripts/init-db.ps1` 中的端口。

项目根目录的 `.env` 是本机私密配置，已被 `.gitignore` 排除；首次使用请复制 `.env.example` 为 `.env`。必填五项：`DB_PASSWORD`（MySQL root 密码）、`BOOTSTRAP_ADMIN_PASSWORD`（管理员初始密码）、`DEMO_ACCOUNT_PASSWORD`（演示账号统一密码）、`PAYMENT_SERVICE_TOKEN`、`PAYMENT_CONSOLE_TOKEN`。`start.ps1`、`init-db.ps1`、`backup.ps1`、`restore.ps1` 都会读取该文件；直接运行 JAR 不会自动读取。`check-env.ps1` 会检查五项是否齐全。

首次启动时会按 `app.bootstrap-admin-username`（默认 `admin`）创建或重置管理员账号，密码取 `.env` 的 `BOOTSTRAP_ADMIN_PASSWORD`。登录使用**登录账号 + 密码**，邮箱只是可选联系方式，注册不再需要邮箱验证码；服务人员账号由管理员创建时直接设定初始密码，创建后即可登录。当前不发送任何真实邮件。

## 初始化与启动

先确认本机 MySQL 8 已在 3306 监听。以下命令在项目根目录的 PowerShell 7 执行。`init-db.ps1` 会按版本号顺序创建 `eldercare` 和 `eldercare_payment` 两个库并导入全部表结构；若已通过 Navicat 完成导入，跳过该命令。对已有同名库执行初始化前，先确认目标并备份。

```powershell
./scripts/init-db.ps1
./scripts/start.ps1
```

`start.ps1` 会自动检查并补齐构建产物：缺少 JAR 时执行 `mvn -DskipTests package`，缺少 `node_modules` 时执行 `npm ci`；若已手动构建，可加 `-SkipBuild` 跳过。`init-db.ps1` 按 `schema_migration` 登记表逐版本执行 SQL，重复运行只会补做未应用的版本。

打开 `http://127.0.0.1:5173`；模拟支付测试控制台为 `http://127.0.0.1:8081/console.html`。控制台使用 `.env` 中的 `PAYMENT_CONSOLE_TOKEN`，主服务使用 `PAYMENT_SERVICE_TOKEN` 查询核实交易。模拟服务绑定本机回环地址，不涉及真实资金。支付流程：用户确认服务完成后创建支付尝试 → 在控制台用尝试标识、订单号、版本、金额登记交易并设置结果 → 用户或管理员在主系统点击同步。SQL 只有表结构，没有预置服务项目；首次登录后需在管理员端添加区域、项目和人员。

停止服务：`./scripts/stop.ps1`。脚本运行日志保存在被 Git 忽略的 `.run/`。若启动失败，查看该目录中的错误日志。

## 备份与恢复

备份与恢复脚本直接读取 `.env` 的 `DB_PASSWORD`，不再需要手工设置其他凭据变量；执行 `./scripts/backup.ps1` 后，备份文件保存在被忽略的 `.run/backups/`，其中可能含个人数据，需自行安全保存。恢复前先停止应用并确认目标数据库；执行：

```powershell
./scripts/restore.ps1 -MainBackup '业务库备份文件路径' -PaymentBackup '交易库备份文件路径' -ConfirmRestore
```

恢复会覆盖同名表的数据。上述备份恢复脚本曾在隔离 MySQL 3307 实测；正常 3306 环境仍待用户本机验证，详见 `docs/implementation-plan.md`。
