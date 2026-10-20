$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $root '.run/backups'
$mysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3306' }
if ($mysqlPort -notmatch '^\d{1,5}$' -or [int]$mysqlPort -lt 1 -or [int]$mysqlPort -gt 65535) { throw 'MYSQL_PORT 必须是 1 到 65535 的端口号。' }
if ([string]::IsNullOrWhiteSpace($env:MYSQL_ADMIN_USER) -or [string]::IsNullOrWhiteSpace($env:MYSQL_ADMIN_PASSWORD)) {
    throw '请先设置 MYSQL_ADMIN_USER 与 MYSQL_ADMIN_PASSWORD。'
}
$dump = (Get-Command mysqldump -ErrorAction Stop).Source
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:MYSQL_ADMIN_PASSWORD
try {
    foreach ($database in @('eldercare','eldercare_payment')) {
        $target = Join-Path $backupDir "$database-$stamp.sql"
        $process = Start-Process -FilePath $dump -ArgumentList @('--host=127.0.0.1',"--port=$mysqlPort","--user=$($env:MYSQL_ADMIN_USER)",'--single-transaction','--set-gtid-purged=OFF','--default-character-set=utf8mb4','--databases',$database) -WindowStyle Hidden -RedirectStandardOutput $target -Wait -PassThru
        if ($process.ExitCode -ne 0 -or (Get-Item -LiteralPath $target).Length -eq 0) { throw "备份 $database 失败；请检查 MySQL 权限与备份文件。" }
        Write-Host "已创建：$target"
    }
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
