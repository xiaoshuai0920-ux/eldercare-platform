$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
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

Write-Host '开始验收：后端测试 → 模拟服务测试 → 前端测试 → 前端类型检查与构建'
Write-Host '后端集成测试目标库为 eldercare_test；本脚本不会连接业务库 eldercare 执行写操作。'

Invoke-Step -Name 'backend mvn test' -Directory (Join-Path $root 'backend') -Action { & mvn -o -q test }
Invoke-Step -Name 'mock-payment mvn test' -Directory (Join-Path $root 'mock-payment') -Action { & mvn -o -q test }
Invoke-Step -Name 'frontend vitest' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run test:run }
Invoke-Step -Name 'frontend build' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run build }

$results | Format-Table -AutoSize | Out-String | Write-Host
if ($failed -gt 0) { throw "验收未通过：$failed 个步骤失败。" }
Write-Host '全部检查通过。' -ForegroundColor Green
