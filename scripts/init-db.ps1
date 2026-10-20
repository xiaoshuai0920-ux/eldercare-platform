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

$migrations = @(
    @('eldercare', 'sql/main/V000__migration.sql'),
    @('eldercare', 'sql/main/V001__identity.sql'),
    @('eldercare', 'sql/main/V002__catalog_and_orders.sql'),
    @('eldercare', 'sql/main/V003__settlement.sql'),
    @('eldercare', 'sql/main/V004__operations.sql'),
    @('eldercare', 'sql/main/V005__notifications.sql'),
    @('eldercare', 'sql/main/V006__recommendation_tags.sql'),
    @('eldercare', 'sql/main/V007__family_coordination.sql'),
    @('eldercare', 'sql/main/V008__username_login.sql'),
    @('eldercare', 'sql/main/V009__demo_seed.sql'),
    @('eldercare_payment', 'sql/payment/V001__transactions.sql')
)

$previousPassword = $env:MYSQL_PWD
$previousLocation = Get-Location
$env:MYSQL_PWD = $dbPassword
try {
    # 切到项目根目录，后续 source 使用相对 ASCII 路径，避免中文路径在命令行参数中被转码
    Set-Location -LiteralPath $root
    & $mysql --user=$adminUser --host=127.0.0.1 --port=$mysqlPort --default-character-set=utf8mb4 --execute='CREATE DATABASE IF NOT EXISTS eldercare CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS eldercare_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'
    if ($LASTEXITCODE -ne 0) { throw '创建数据库失败。' }

    $migrationTable = & $mysql --user=$adminUser --host=127.0.0.1 --port=$mysqlPort --database=eldercare --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='eldercare' AND TABLE_NAME='schema_migration'"
    if ($LASTEXITCODE -ne 0) { throw '检查迁移登记表失败。' }

    foreach ($pair in $migrations) {
        $database = $pair[0]
        $relative = $pair[1]
        $scriptName = Split-Path -Leaf $relative
        if ($database -eq 'eldercare' -and "$migrationTable".Trim() -eq '1') {
            $applied = & $mysql --user=$adminUser --host=127.0.0.1 --port=$mysqlPort --database=eldercare --batch --skip-column-names --execute="SELECT COUNT(*) FROM schema_migration WHERE script_name='$scriptName'"
            if ($LASTEXITCODE -ne 0) { throw "查询迁移状态失败：$scriptName" }
            if ("$applied".Trim() -eq '1') { continue }
        }
        $databaseOption = '--database=' + $database
        & $mysql --user=$adminUser --host=127.0.0.1 --port=$mysqlPort $databaseOption --default-character-set=utf8mb4 --execute="source $relative"
        if ($LASTEXITCODE -ne 0) { throw "初始化 $database 失败：$relative" }
        if ($database -eq 'eldercare') {
            & $mysql --user=$adminUser --host=127.0.0.1 --port=$mysqlPort --database=eldercare --execute="INSERT IGNORE INTO schema_migration(script_name) VALUES('$scriptName')"
            if ($LASTEXITCODE -ne 0) { throw "登记迁移失败：$scriptName" }
        }
    }
    Write-Host '两个数据库的表结构初始化完成。'
} finally {
    Set-Location -LiteralPath $previousLocation
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
