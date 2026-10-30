param(
    [Parameter(Mandatory = $true)][string]$MainBackup,
    [Parameter(Mandatory = $true)][string]$PaymentBackup,
    [switch]$ConfirmRestore
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')
$root = Split-Path -Parent $PSScriptRoot
if (!$ConfirmRestore) { throw '恢复会覆盖目标数据库中的同名表数据；请确认备份路径后显式指定 -ConfirmRestore。' }
$settings = Read-DotEnvFile -Path (Join-Path $root '.env')
$mysqlPort = Get-MySqlPort -Settings $settings
foreach ($name in @('DB_MIGRATION_USER','DB_MIGRATION_PASSWORD')) {
    if (-not $settings.Contains($name) -or [string]::IsNullOrWhiteSpace($settings[$name])) { throw "请填写 .env 中的 $name。" }
}
if ($settings['DB_MIGRATION_USER'] -eq 'root' -or $settings['DB_MIGRATION_USER'] -notmatch '^[A-Za-z0-9_]+$') { throw 'DB_MIGRATION_USER 必须是独立迁移账号，不能使用 root。' }
$mysql = (Get-Command mysql -ErrorAction Stop).Source
$files = @(
    [PSCustomObject]@{ Database = 'eldercare'; Path = (Resolve-Path -LiteralPath $MainBackup -ErrorAction Stop).Path },
    [PSCustomObject]@{ Database = 'eldercare_payment'; Path = (Resolve-Path -LiteralPath $PaymentBackup -ErrorAction Stop).Path }
)
foreach ($entry in $files) { if ((Get-Item -LiteralPath $entry.Path).Length -eq 0) { throw "备份文件为空：$($entry.Path)" } }

$previousPassword = $env:MYSQL_PWD
$previousLocation = Get-Location
$env:MYSQL_PWD = $settings['DB_MIGRATION_PASSWORD']
try {
    Set-Location -LiteralPath $root
    foreach ($entry in $files) {
        # 本项目备份脚本生成的路径位于 .run/backups，保持相对路径以兼容中文仓库目录。
        $relative = Resolve-Path -LiteralPath $entry.Path -Relative
        $databaseOption = '--database=' + $entry.Database
        & $mysql ('--user=' + $settings['DB_MIGRATION_USER']) --host=127.0.0.1 --port=$mysqlPort $databaseOption --default-character-set=utf8mb4 --execute="source $relative"
        if ($LASTEXITCODE -ne 0) { throw "恢复失败：$($entry.Path)" }
        Write-Host "已恢复：$($entry.Database)"
    }
} finally {
    Set-Location -LiteralPath $previousLocation
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
