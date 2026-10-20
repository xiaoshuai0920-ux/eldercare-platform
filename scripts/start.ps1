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
foreach ($name in @('SERVER__PORT', 'SERVER__HOST')) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root '.run'
if (Test-Path (Join-Path $runDir 'pids.json')) { throw '检测到已有启动记录；请先运行 scripts/stop.ps1 或检查进程。' }

$requiredKeys = @('DB_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD', 'DEMO_ACCOUNT_PASSWORD', 'PAYMENT_SERVICE_TOKEN', 'PAYMENT_CONSOLE_TOKEN')
$null = Import-DotEnv -Path (Join-Path $root '.env') -Required $requiredKeys

$backendJar = Join-Path $root 'backend/target/eldercare-backend-0.1.0.jar'
$paymentJar = Join-Path $root 'mock-payment/target/eldercare-mock-payment-0.1.0.jar'
$frontModules = Join-Path $root 'frontend/node_modules'

if (!$SkipBuild) {
    if (!(Test-Path $paymentJar)) {
        Write-Host '未找到模拟支付服务 JAR，开始构建…'
        Push-Location (Join-Path $root 'mock-payment')
        try { & mvn -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw '模拟支付服务构建失败，请查看上方 Maven 输出。' } }
        finally { Pop-Location }
    }
    if (!(Test-Path $backendJar)) {
        Write-Host '未找到主服务 JAR，开始构建…'
        Push-Location (Join-Path $root 'backend')
        try { & mvn -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw '主服务构建失败，请查看上方 Maven 输出。' } }
        finally { Pop-Location }
    }
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

New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$java = (Get-Command java -ErrorAction Stop).Source
$node = (Get-Command node.exe -ErrorAction Stop).Source
$specs = @(
    @{ Name = 'mock-payment'; File = $java; Args = @('-jar', ('"' + $paymentJar + '"'), '--server.address=127.0.0.1', '--server.port=8081'); Dir = (Join-Path $root 'mock-payment') },
    @{ Name = 'backend'; File = $java; Args = @('-jar', ('"' + $backendJar + '"'), '--server.address=127.0.0.1', '--server.port=8080'); Dir = (Join-Path $root 'backend') },
    @{ Name = 'frontend'; File = $node; Args = @('node_modules/vite/bin/vite.js', '--host', '127.0.0.1'); Dir = (Join-Path $root 'frontend') }
)
$started = @()
try {
    foreach ($spec in $specs) {
        $out = Join-Path $runDir ($spec.Name + '.out.log')
        $err = Join-Path $runDir ($spec.Name + '.err.log')
        $process = Start-Process -FilePath $spec.File -ArgumentList $spec.Args -WorkingDirectory $spec.Dir -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
        $started += [PSCustomObject]@{ name = $spec.Name; pid = $process.Id; startTimeTicks = $process.StartTime.Ticks }
        Write-Host "已启动 $($spec.Name)，PID $($process.Id)"
    }
    foreach ($endpoint in @('http://127.0.0.1:8081/ready', 'http://127.0.0.1:8080/api/ready', 'http://127.0.0.1:5173/login', 'http://127.0.0.1:5173/src/main.ts')) {
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
    Write-Host '本地入口：http://127.0.0.1:5173；三个服务均已通过就绪检查。'
} catch {
    foreach ($entry in $started) {
        $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
        if ($null -ne $process -and $process.StartTime.Ticks -eq $entry.startTimeTicks) { Stop-Process -Id $entry.pid -ErrorAction SilentlyContinue }
    }
    throw
}
