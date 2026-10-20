$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root '.run/pids.json'
if (!(Test-Path $pidFile)) { Write-Host '没有本脚本的启动记录。'; return }
$entries = @(Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json)
foreach ($entry in $entries) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($null -eq $process) { continue }
    if ($process.StartTime.Ticks -ne $entry.startTimeTicks) { Write-Warning "PID $($entry.pid) 已被其他进程复用，未终止。"; continue }
    Stop-Process -Id $entry.pid -ErrorAction Stop
    Write-Host "已停止 $($entry.name)，PID $($entry.pid)"
}
Remove-Item -LiteralPath $pidFile
