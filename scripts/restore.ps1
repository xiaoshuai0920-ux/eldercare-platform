param(
    [Parameter(Mandatory=$true)][string]$MainBackup,
    [Parameter(Mandatory=$true)][string]$PaymentBackup,
    [switch]$ConfirmRestore
)
$ErrorActionPreference = 'Stop'
$mysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3306' }
if ($mysqlPort -notmatch '^\d{1,5}$' -or [int]$mysqlPort -lt 1 -or [int]$mysqlPort -gt 65535) { throw 'MYSQL_PORT 必须是 1 到 65535 的端口号。' }
if (!$ConfirmRestore) { throw '恢复会覆盖目标数据库中的同名表数据；请确认备份路径后显式指定 -ConfirmRestore。' }
if ([string]::IsNullOrWhiteSpace($env:MYSQL_ADMIN_USER) -or [string]::IsNullOrWhiteSpace($env:MYSQL_ADMIN_PASSWORD)) {
    throw '请先设置 MYSQL_ADMIN_USER 与 MYSQL_ADMIN_PASSWORD。'
}
$mysql = (Get-Command mysql -ErrorAction Stop).Source
$files = @((Resolve-Path -LiteralPath $MainBackup -ErrorAction Stop).Path,(Resolve-Path -LiteralPath $PaymentBackup -ErrorAction Stop).Path)
foreach ($file in $files) { if ((Get-Item -LiteralPath $file).Length -eq 0) { throw "备份文件为空：$file" } }
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:MYSQL_ADMIN_PASSWORD
try {
    foreach ($file in $files) {
        $source = $file.Replace('\','/')
        & $mysql --user=$env:MYSQL_ADMIN_USER --host=127.0.0.1 --port=$mysqlPort --default-character-set=utf8mb4 --execute="source $source"
        if ($LASTEXITCODE -ne 0) { throw "恢复失败：$file" }
        Write-Host "已恢复：$file"
    }
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
