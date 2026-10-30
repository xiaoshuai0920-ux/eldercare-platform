$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot '_env.ps1')
$settings = Read-DotEnvFile -Path (Join-Path $root '.env')
foreach ($name in @('TEST_DB_URL','TEST_DB_USER','TEST_DB_PASSWORD')) {
    if (-not $settings.Contains($name) -or [string]::IsNullOrWhiteSpace($settings[$name])) { throw "请填写 .env 中的 $name；集成测试不会使用业务库账号。" }
}
if ($settings['TEST_DB_USER'] -notmatch '^[A-Za-z0-9_]{1,32}$' -or $settings['TEST_DB_USER'] -eq 'root') {
    throw '拒绝运行集成测试：TEST_DB_USER 必须是专用测试账号，不能使用 root。'
}
if ($settings['TEST_DB_URL'] -notmatch '^jdbc:mysql://127\.0\.0\.1(?::\d+)?/eldercare_test(?:[?;].*)?$') {
    throw '拒绝运行集成测试：TEST_DB_URL 必须指向本机 eldercare_test。'
}
$results = @()
$failed = 0

function Invoke-Step {
    param([string]$Name, [string]$Directory, [scriptblock]$Action)
    $started = Get-Date
    $logDir = Join-Path $root '.run'
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    $log = Join-Path $logDir ("test-" + ($Name -replace '[^A-Za-z0-9]+', '-') + ".log")
    Push-Location $Directory
    # 原生命令（mvn/npm）会向 stderr 输出进度与警告，PowerShell 5.1 在 Stop 模式下会把它当成终止错误
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $code = 0
    try {
        & $Action *> $log
        $code = $LASTEXITCODE
    } catch {
        $code = 1
        Write-Host "[ERROR] $Name：$($_.Exception.Message)" -ForegroundColor Red
    } finally {
        $ErrorActionPreference = $previous
        Pop-Location
    }
    if ($null -eq $code) { $code = 0 }
    if ($code -ne 0) {
        $script:failed++
        $script:results += [PSCustomObject]@{ Step = $Name; Result = 'FAIL'; Seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1) }
        Write-Host "[FAIL] $Name：退出码 $code，日志末尾如下" -ForegroundColor Red
        Get-Content -LiteralPath $log -Tail 25 | Out-Host
    } else {
        $script:results += [PSCustomObject]@{ Step = $Name; Result = 'PASS'; Seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1) }
        Write-Host "[PASS] $Name（日志：$log）"
    }
}

Write-Host '开始验收：后端与模拟服务测试 → 管理 Web 检查 → 跨端客户端检查与构建'
Write-Host '后端集成测试只允许连接 eldercare_test；数据库用户仍需在 MySQL 中限制为 *_test 库权限。'

Invoke-Step -Name 'backend mvn test' -Directory (Join-Path $root 'backend') -Action { & mvn -o -q test }
Invoke-Step -Name 'mock-payment mvn test' -Directory (Join-Path $root 'mock-payment') -Action { & mvn -o -q test }
Invoke-Step -Name 'frontend vitest' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run test:run }
Invoke-Step -Name 'frontend build' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run build }
Invoke-Step -Name 'mobile tests' -Directory (Join-Path $root 'mobile') -Action { & npm.cmd test }
Invoke-Step -Name 'mobile typecheck' -Directory (Join-Path $root 'mobile') -Action { & npm.cmd run typecheck }
Invoke-Step -Name 'mobile h5 build' -Directory (Join-Path $root 'mobile') -Action { & npm.cmd run build:h5 }
Invoke-Step -Name 'mobile weixin build' -Directory (Join-Path $root 'mobile') -Action { & npm.cmd run build:mp-weixin }

$results | Format-Table -AutoSize | Out-String | Write-Host
if ($failed -gt 0) { throw "验收未通过：$failed 个步骤失败。" }
Write-Host '全部检查通过。' -ForegroundColor Green
