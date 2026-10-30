$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $root '.run/backups'
$settings = Read-DotEnvFile -Path (Join-Path $root '.env')
$mysqlPort = Get-MySqlPort -Settings $settings
foreach ($name in @('DB_MIGRATION_USER','DB_MIGRATION_PASSWORD')) {
    if (-not $settings.Contains($name) -or [string]::IsNullOrWhiteSpace($settings[$name])) { throw "请填写 .env 中的 $name。" }
}
if ($settings['DB_MIGRATION_USER'] -eq 'root' -or $settings['DB_MIGRATION_USER'] -notmatch '^[A-Za-z0-9_]+$') { throw 'DB_MIGRATION_USER 必须是独立迁移账号，不能使用 root。' }
$dump = (Get-Command mysqldump -ErrorAction Stop).Source
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $settings['DB_MIGRATION_PASSWORD']
try {
    foreach ($database in @('eldercare', 'eldercare_payment')) {
        $target = Join-Path $backupDir "$database-$stamp.sql"
        $process = Start-Process -FilePath $dump -ArgumentList @('--host=127.0.0.1', "--port=$mysqlPort", ('--user=' + $settings['DB_MIGRATION_USER']), '--single-transaction', '--skip-add-locks', '--set-gtid-purged=OFF', '--no-create-db', '--default-character-set=utf8mb4', $database) -WindowStyle Hidden -RedirectStandardOutput $target -Wait -PassThru
        if ($process.ExitCode -ne 0 -or (Get-Item -LiteralPath $target).Length -eq 0) { throw "备份 $database 失败；请检查迁移账号的库级权限。" }
        Write-Host "已创建：$target"
    }
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
