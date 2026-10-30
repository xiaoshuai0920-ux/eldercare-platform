$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot '_env.ps1')

# 本地版 CI：与 .github/workflows/ci.yml 对齐，多跑一步前端类型检查。
# 后端集成测试需要 .env 里的 TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD，
# 且目标库必须是 eldercare_test（scripts/test.ps1 会做同样的校验）。
$settings = Read-DotEnvFile -Path (Join-Path $root '.env')
foreach ($name in @('TEST_DB_URL', 'TEST_DB_USER', 'TEST_DB_PASSWORD')) {
    if (-not $settings.Contains($name) -or [string]::IsNullOrWhiteSpace($settings[$name])) {
        throw "请填写 .env 中的 $name；集成测试不会使用业务库账号。"
    }
}
if ($settings['TEST_DB_USER'] -notmatch '^[A-Za-z0-9_]{1,32}$' -or $settings['TEST_DB_USER'] -eq 'root') {
    throw '拒绝运行：TEST_DB_USER 必须是专用测试账号，不能使用 root。'
}
if ($settings['TEST_DB_URL'] -notmatch '^jdbc:mysql://127\.0\.0\.1(?::\d+)?/eldercare_test(?:[?;].*)?$') {
    throw '拒绝运行：TEST_DB_URL 必须指向本机 eldercare_test。'
}

$results = @()
$failed = 0

function Invoke-Step {
    param([string]$Name, [string]$Directory, [scriptblock]$Action)
    $started = Get-Date
    $logDir = Join-Path $root '.run'
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    $log = Join-Path $logDir ('ci-' + ($Name -replace '[^A-Za-z0-9]+', '-') + '.log')
    Push-Location $Directory
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

Write-Host 'CI 本地校验：后端测试 → 模拟支付测试 → 前端 lint/格式/类型/单测/构建'
Write-Host '端到端冒烟（npm run test:e2e）需要真实后端与浏览器，未包含在此脚本内。'

Invoke-Step -Name 'backend mvn test' -Directory (Join-Path $root 'backend') -Action { & mvn -o -q test }
Invoke-Step -Name 'mock-payment mvn test' -Directory (Join-Path $root 'mock-payment') -Action { & mvn -o -q test }
Invoke-Step -Name 'frontend lint' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run lint }
Invoke-Step -Name 'frontend format check' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run format:check }
Invoke-Step -Name 'frontend typecheck' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run typecheck }
Invoke-Step -Name 'frontend vitest' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run test:run }
Invoke-Step -Name 'frontend build' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run build }

$results | Format-Table -AutoSize | Out-String | Write-Host
if ($failed -gt 0) { throw "CI 未通过：$failed 个步骤失败。" }
Write-Host '全部检查通过。' -ForegroundColor Green
