$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')
$root = Split-Path -Parent $PSScriptRoot
Write-Host '居家养老平台 Windows 环境检查'
foreach ($tool in @('java', 'mvn', 'node', 'npm', 'mysql')) {
    $command = Get-Command $tool -ErrorAction SilentlyContinue
    if ($null -eq $command) { Write-Host "[缺失] $tool"; continue }
    Write-Host "[找到] $tool : $($command.Source)"
}
if (@('java', 'mvn', 'node', 'npm', 'mysql') | Where-Object { -not (Get-Command $_ -ErrorAction SilentlyContinue) }) { throw '必要工具缺失，请先安装后再继续。' }
Write-Host '版本：'
& java -version
& mvn -version
& node --version
& npm --version
& mysql --version

$requiredKeys = @('DB_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD', 'DEMO_ACCOUNT_PASSWORD', 'PAYMENT_SERVICE_TOKEN', 'PAYMENT_CONSOLE_TOKEN')
$settings = Read-DotEnvFile -Path (Join-Path $root '.env')
$missing = @($requiredKeys | Where-Object { -not $settings.Contains($_) -or [string]::IsNullOrWhiteSpace($settings[$_]) })
if ($missing.Count -gt 0) { throw "以下 .env 键未填写：$($missing -join '、')。可参考 .env.example。" }
Write-Host '.env 必填键齐全；数据库连接请确认 MySQL 8 在 127.0.0.1:3306 可达（本检查不登录数据库）。'
