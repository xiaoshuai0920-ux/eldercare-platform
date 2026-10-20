$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$mysql = (Get-Command mysql -ErrorAction Stop).Source
$mysqlPort = '3306'
$adminUser = 'root'
$envFile = Join-Path $root '.env'
if (!(Test-Path -LiteralPath $envFile -PathType Leaf)) { throw '缺少项目根目录 .env；请先按 README 配置。' }
$passwordLines = @(Get-Content -LiteralPath $envFile -Encoding utf8 | Where-Object { $_ -match '^DB_PASSWORD=' })
if ($passwordLines.Count -ne 1) { throw '.env 中须且只能有一行 DB_PASSWORD。' }
$dbPassword = $passwordLines[0].Substring('DB_PASSWORD='.Length)
if ([string]::IsNullOrWhiteSpace($dbPassword)) { throw '请填写 .env 中的 DB_PASSWORD。' }
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $dbPassword
try {
    & $mysql --user=$adminUser --host=127.0.0.1 --port=$mysqlPort --default-character-set=utf8mb4 --execute='CREATE DATABASE IF NOT EXISTS eldercare CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS eldercare_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'
    if ($LASTEXITCODE -ne 0) { throw '创建数据库失败。' }
    foreach ($pair in @(@('eldercare','sql/main/V001__identity.sql'), @('eldercare','sql/main/V002__catalog_and_orders.sql'), @('eldercare','sql/main/V003__settlement.sql'), @('eldercare','sql/main/V004__operations.sql'), @('eldercare','sql/main/V005__notifications.sql'), @('eldercare','sql/main/V006__recommendation_tags.sql'), @('eldercare','sql/main/V007__family_coordination.sql'), @('eldercare_payment','sql/payment/V001__transactions.sql'))) {
        $scriptPath = (Join-Path $root $pair[1]).Replace('\','/')
        $databaseOption = '--database=' + $pair[0]
        & $mysql --user=$adminUser --host=127.0.0.1 --port=$mysqlPort $databaseOption --default-character-set=utf8mb4 --execute="source $scriptPath"
        if ($LASTEXITCODE -ne 0) { throw "初始化 $($pair[0]) 失败：$scriptPath" }
    }
    Write-Host '两个数据库的表结构初始化完成。'
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
