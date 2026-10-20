$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $root '.run/backups'
$mysqlPort = '3306'
$null = Import-DotEnv -Path (Join-Path $root '.env') -Required @('DB_PASSWORD')
$dump = (Get-Command mysqldump -ErrorAction Stop).Source
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:DB_PASSWORD
try {
    foreach ($database in @('eldercare', 'eldercare_payment')) {
        $target = Join-Path $backupDir "$database-$stamp.sql"
        $process = Start-Process -FilePath $dump -ArgumentList @('--host=127.0.0.1', "--port=$mysqlPort", '--user=root', '--single-transaction', '--set-gtid-purged=OFF', '--default-character-set=utf8mb4', '--databases', $database) -WindowStyle Hidden -RedirectStandardOutput $target -Wait -PassThru
        if ($process.ExitCode -ne 0 -or (Get-Item -LiteralPath $target).Length -eq 0) { throw "备份 $database 失败；请检查 .env 中的 DB_PASSWORD 与 MySQL 权限。" }
        Write-Host "已创建：$target"
    }
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
