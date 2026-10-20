param(
    [Parameter(Mandatory = $true)][string]$MainBackup,
    [Parameter(Mandatory = $true)][string]$PaymentBackup,
    [switch]$ConfirmRestore
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$mysqlPort = '3306'
if (!$ConfirmRestore) { throw '恢复会覆盖目标数据库中的同名表数据；请确认备份路径后显式指定 -ConfirmRestore。' }
$null = Import-DotEnv -Path (Join-Path $root '.env') -Required @('DB_PASSWORD')
$mysql = (Get-Command mysql -ErrorAction Stop).Source
$files = @((Resolve-Path -LiteralPath $MainBackup -ErrorAction Stop).Path, (Resolve-Path -LiteralPath $PaymentBackup -ErrorAction Stop).Path)
foreach ($file in $files) { if ((Get-Item -LiteralPath $file).Length -eq 0) { throw "备份文件为空：$file" } }

$previousPassword = $env:MYSQL_PWD
$previousLocation = Get-Location
$env:MYSQL_PWD = $env:DB_PASSWORD
try {
    Set-Location -LiteralPath $root
    foreach ($file in $files) {
        # 使用相对 ASCII 路径，避免中文路径在 mysql 命令行参数中被转码
        $relative = Resolve-Path -LiteralPath $file -Relative
        & $mysql --user=root --host=127.0.0.1 --port=$mysqlPort --default-character-set=utf8mb4 --execute="source $relative"
        if ($LASTEXITCODE -ne 0) { throw "恢复失败：$file" }
        Write-Host "已恢复：$file"
    }
} finally {
    Set-Location -LiteralPath $previousLocation
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
