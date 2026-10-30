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

$requiredKeys = @('DB_USER', 'DB_PASSWORD', 'PAYMENT_DB_USER', 'PAYMENT_DB_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD', 'DEMO_ACCOUNT_PASSWORD', 'PAYMENT_SERVICE_TOKEN', 'PAYMENT_CONSOLE_TOKEN')
$settings = Read-DotEnvFile -Path (Join-Path $root '.env')
$null = Get-MySqlPort -Settings $settings
$null = Get-ConfiguredPort -Settings $settings -Name 'VITE_PORT' -Default '5173'
$missing = @($requiredKeys | Where-Object { -not $settings.Contains($_) -or [string]::IsNullOrWhiteSpace($settings[$_]) })
if ($missing.Count -gt 0) { throw "以下 .env 键未填写：$($missing -join '、')。可参考 .env.example。" }
if ($settings['DB_USER'] -eq 'root' -or $settings['PAYMENT_DB_USER'] -eq 'root') { throw '两个服务不能使用 MySQL root 作为运行账号。' }
Write-Host '.env 本机运行必填键齐全；本检查不登录数据库。init-db.ps1 和 test.ps1 另需迁移账号与测试账号配置。'
