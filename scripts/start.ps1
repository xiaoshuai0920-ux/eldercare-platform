param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')

# Windows PowerShell 5.1 的 Start-Process 在环境变量存在大小写重复项时会抛字典冲突
# （例如同时存在 HTTPS_PROXY 与 https_proxy），这里先按不区分大小写归一化。
function Remove-DuplicateEnvKeys {
    $names = @([Environment]::GetEnvironmentVariables().Keys)
    foreach ($group in ($names | Group-Object { $_.ToLowerInvariant() })) {
        if ($group.Count -le 1) { continue }
        $value = [Environment]::GetEnvironmentVariable($group.Group[0], 'Process')
        foreach ($name in $group.Group) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
        [Environment]::SetEnvironmentVariable($group.Group[0].ToUpperInvariant(), $value, 'Process')
    }
}

Remove-DuplicateEnvKeys
# 宿主环境可能注入 SERVER__PORT / SERVER__HOST，Spring 会把它们绑定成 server.port，导致服务起在随机端口
foreach ($name in @('SERVER_PORT', 'SERVER_ADDRESS', 'SERVER__PORT', 'SERVER__HOST')) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root '.run'
if (Test-Path (Join-Path $runDir 'pids.json')) { throw '检测到已有启动记录；请先运行 scripts/stop.ps1 或检查进程。' }
$adminResetForThisStart = [Environment]::GetEnvironmentVariable('BOOTSTRAP_ADMIN_RESET', 'Process')
if (![string]::IsNullOrWhiteSpace($adminResetForThisStart) -and $adminResetForThisStart -notin @('true','false')) {
    throw 'BOOTSTRAP_ADMIN_RESET 仅接受 true 或 false；请在当前 PowerShell 会话中设置，勿写入 .env。'
}
[Environment]::SetEnvironmentVariable('BOOTSTRAP_ADMIN_RESET', $null, 'Process')

$settings = Read-DotEnvFile -Path (Join-Path $root '.env')
$null = Get-MySqlPort -Settings $settings
$vitePort = Get-ConfiguredPort -Settings $settings -Name 'VITE_PORT' -Default '5173'
if ($settings.Contains('BOOTSTRAP_ADMIN_RESET')) { throw '请从 .env 删除 BOOTSTRAP_ADMIN_RESET；重置只能通过当前 PowerShell 会话临时设置。' }
$requiredKeys = @('DB_USER', 'DB_PASSWORD', 'PAYMENT_DB_USER', 'PAYMENT_DB_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD', 'DEMO_ACCOUNT_PASSWORD', 'PAYMENT_SERVICE_TOKEN', 'PAYMENT_CONSOLE_TOKEN')
foreach ($name in $requiredKeys) {
    if (-not $settings.Contains($name) -or [string]::IsNullOrWhiteSpace($settings[$name])) { throw "请填写 .env 中的 $name。" }
}
if ($settings['DB_USER'] -eq 'root' -or $settings['PAYMENT_DB_USER'] -eq 'root') {
    throw '两个服务必须使用各自的库级运行账号，不能使用 MySQL root。'
}

$backendJar = Join-Path $root 'backend/target/eldercare-backend-0.1.0.jar'
$paymentJar = Join-Path $root 'mock-payment/target/eldercare-mock-payment-0.1.0.jar'
$frontModules = Join-Path $root 'frontend/node_modules'

if (!$SkipBuild) {
    Write-Host '从当前源码构建模拟支付服务…'
    Push-Location (Join-Path $root 'mock-payment')
    try { & mvn -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw '模拟支付服务构建失败，请查看上方 Maven 输出。' } }
    finally { Pop-Location }
    Write-Host '从当前源码构建主服务…'
    Push-Location (Join-Path $root 'backend')
    try { & mvn -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw '主服务构建失败，请查看上方 Maven 输出。' } }
    finally { Pop-Location }
    if (!(Test-Path $frontModules)) {
        Write-Host '未找到前端依赖，开始安装…'
        Push-Location (Join-Path $root 'frontend')
        try { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败，请查看上方 npm 输出。' } }
        finally { Pop-Location }
    }
}

if (!(Test-Path $backendJar) -or !(Test-Path $paymentJar) -or !(Test-Path $frontModules)) {
    throw '构建产物不完整：请去掉 -SkipBuild 重新运行，或手动执行 mvn package 与 npm ci。'
}
foreach ($port in @(8081, 8080, $vitePort)) {
    $probe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $port)
    $probe.Server.ExclusiveAddressUse = $true
    try { $probe.Start() } catch { throw "端口 $port 已占用或不可用；请先停止旧服务或为本机配置空闲端口。" }
    finally { $probe.Stop() }
}

New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$java = (Get-Command java -ErrorAction Stop).Source
$node = (Get-Command node.exe -ErrorAction Stop).Source
$managedEnvKeys = @('SERVER_PORT','SERVER_ADDRESS','SERVER__PORT','SERVER__HOST','MYSQL_PORT','VITE_PORT','DB_USER','DB_PASSWORD','PAYMENT_DB_USER','PAYMENT_DB_PASSWORD','DB_MIGRATION_USER','DB_MIGRATION_PASSWORD','TEST_DB_URL','TEST_DB_USER','TEST_DB_PASSWORD','BOOTSTRAP_ADMIN_PASSWORD','BOOTSTRAP_ADMIN_RESET','DEMO_ACCOUNT_PASSWORD','PAYMENT_SERVICE_TOKEN','PAYMENT_CONSOLE_TOKEN','PAYMENT_BASE_URL')
$sensitiveEnvSnapshot = @{}
foreach ($name in $managedEnvKeys) { $sensitiveEnvSnapshot[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$startScopedProcess = {
    param($Spec, [string[]]$AllowedKeys)
    foreach ($name in $managedEnvKeys) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    foreach ($name in $AllowedKeys) {
        if ($name -eq 'BOOTSTRAP_ADMIN_RESET') { continue }
        if ($settings.Contains($name)) { [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process') }
    }
    if ($Spec.Name -eq 'backend') {
        $bootstrapReset = 'false'
        if ($adminResetForThisStart -eq 'true') { $bootstrapReset = 'true' }
        [Environment]::SetEnvironmentVariable('BOOTSTRAP_ADMIN_RESET', $bootstrapReset, 'Process')
    }
    try {
        return Start-Process -FilePath $Spec.File -ArgumentList $Spec.Args -WorkingDirectory $Spec.Dir -WindowStyle Hidden -RedirectStandardOutput $Spec.Out -RedirectStandardError $Spec.Err -PassThru
    } finally {
        foreach ($name in $managedEnvKeys) { [Environment]::SetEnvironmentVariable($name, $sensitiveEnvSnapshot[$name], 'Process') }
    }
}
$specs = @(
    @{ Name = 'mock-payment'; File = $java; Args = @('-jar', ('"' + $paymentJar + '"'), '--spring.profiles.active=local', '--server.address=127.0.0.1', '--server.port=8081'); Dir = (Join-Path $root 'mock-payment'); Env = @('MYSQL_PORT','PAYMENT_DB_USER','PAYMENT_DB_PASSWORD','PAYMENT_SERVICE_TOKEN','PAYMENT_CONSOLE_TOKEN') },
    @{ Name = 'backend'; File = $java; Args = @('-jar', ('"' + $backendJar + '"'), '--spring.profiles.active=local', '--server.address=127.0.0.1', '--server.port=8080'); Dir = (Join-Path $root 'backend'); Env = @('MYSQL_PORT','DB_USER','DB_PASSWORD','BOOTSTRAP_ADMIN_PASSWORD','DEMO_ACCOUNT_PASSWORD','PAYMENT_SERVICE_TOKEN') },
    @{ Name = 'frontend'; File = $node; Args = @('node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', $vitePort, '--strictPort'); Dir = (Join-Path $root 'frontend'); Env = @('VITE_PORT') }
)
$started = @()
$runId = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
try {
    foreach ($spec in $specs) {
        $out = Join-Path $runDir ($spec.Name + '-' + $runId + '.out.log')
        $err = Join-Path $runDir ($spec.Name + '-' + $runId + '.err.log')
        $spec.Out = $out
        $spec.Err = $err
        $process = & $startScopedProcess $spec $spec.Env
        $started += [PSCustomObject]@{ name = $spec.Name; pid = $process.Id; startTimeTicks = $process.StartTime.Ticks }
        Write-Host "已启动 $($spec.Name)，PID $($process.Id)"
    }
    foreach ($entry in $started) {
        $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
        if ($null -eq $process -or $process.StartTime.Ticks -ne $entry.startTimeTicks) {
            throw "新启动的 $($entry.name) 进程已退出；请检查本轮 .run 日志。"
        }
    }
    foreach ($endpoint in @('http://127.0.0.1:8081/ready', 'http://127.0.0.1:8080/api/ready', "http://127.0.0.1:$vitePort/login", "http://127.0.0.1:$vitePort/src/main.ts")) {
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            try {
                $response = Invoke-WebRequest -Uri $endpoint -UseBasicParsing -TimeoutSec 2
                if ($response.StatusCode -eq 200) { $ready = $true; break }
            } catch { Start-Sleep -Milliseconds 800 }
        }
        if (!$ready) { throw "本地服务未就绪：$endpoint。请检查 .run/ 中的错误日志与数据库配置。" }
    }
    $started | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runDir 'pids.json') -Encoding utf8
    Write-Host "本地入口：http://127.0.0.1:$vitePort；三个服务均已通过就绪检查。"
} catch {
    foreach ($entry in $started) {
        $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
        if ($null -ne $process -and $process.StartTime.Ticks -eq $entry.startTimeTicks) { Stop-Process -Id $entry.pid -ErrorAction SilentlyContinue }
    }
    throw
}
