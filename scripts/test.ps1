$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$results = @()
$failed = 0

function Invoke-Step {
    param([string]$Name, [string]$Directory, [scriptblock]$Action)
    $started = Get-Date
    Push-Location $Directory
    try {
        & $Action
        if ($LASTEXITCODE -ne 0) { throw "$Name 退出码 $LASTEXITCODE" }
        $script:results += [PSCustomObject]@{ Step = $Name; Result = 'PASS'; Seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1) }
    } catch {
        $script:failed++
        $script:results += [PSCustomObject]@{ Step = $Name; Result = 'FAIL'; Seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1) }
        Write-Host "[FAIL] $Name：$($_.Exception.Message)" -ForegroundColor Red
    } finally {
        Pop-Location
    }
}

Write-Host '开始验收：后端单测 → 模拟服务单测 → 前端单测 → 前端类型检查与构建'
Write-Host '集成测试目标库为 eldercare_test；本脚本不会连接业务库 eldercare 执行写操作。'

Invoke-Step -Name 'backend mvn test' -Directory (Join-Path $root 'backend') -Action { & mvn -o -q test }
Invoke-Step -Name 'mock-payment mvn test' -Directory (Join-Path $root 'mock-payment') -Action { & mvn -o -q test }
Invoke-Step -Name 'frontend vitest' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run test:run }
Invoke-Step -Name 'frontend build' -Directory (Join-Path $root 'frontend') -Action { & npm.cmd run build }

$results | Format-Table -AutoSize | Out-String | Write-Host
if ($failed -gt 0) { throw "验收未通过：$failed 个步骤失败。" }
Write-Host '全部检查通过。' -ForegroundColor Green
