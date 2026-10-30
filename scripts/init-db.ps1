$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$mysql = (Get-Command mysql -ErrorAction Stop).Source
$envFile = Join-Path $root '.env'
$settings = Read-DotEnvFile -Path $envFile
$mysqlPort = Get-MySqlPort -Settings $settings
$migrationUser = $settings['DB_MIGRATION_USER']
$migrationPassword = $settings['DB_MIGRATION_PASSWORD']
if ([string]::IsNullOrWhiteSpace($migrationUser) -or [string]::IsNullOrWhiteSpace($migrationPassword)) {
    throw '请填写 .env 中的 DB_MIGRATION_USER 和 DB_MIGRATION_PASSWORD；初始化脚本不使用运行账号或 root。'
}
if ($migrationUser -notmatch '^[A-Za-z0-9_]+$' -or $migrationUser -eq 'root') { throw 'DB_MIGRATION_USER 必须是独立迁移账号，不能使用 root。' }

$mainMigrations = @(
    [PSCustomObject]@{ Name = 'V000__migration.sql'; File = 'sql/main/V000__migration.sql'; Table = ''; Column = '' },
    [PSCustomObject]@{ Name = 'V001__identity.sql'; File = 'sql/main/V001__identity.sql'; Table = 'account'; Column = '' },
    [PSCustomObject]@{ Name = 'V002__catalog_and_orders.sql'; File = 'sql/main/V002__catalog_and_orders.sql'; Table = 'service_item'; Column = '' },
    [PSCustomObject]@{ Name = 'V003__settlement.sql'; File = 'sql/main/V003__settlement.sql'; Table = 'settlement'; Column = '' },
    [PSCustomObject]@{ Name = 'V004__operations.sql'; File = 'sql/main/V004__operations.sql'; Table = 'service_incident'; Column = '' },
    [PSCustomObject]@{ Name = 'V005__notifications.sql'; File = 'sql/main/V005__notifications.sql'; Table = 'outbox_event'; Column = '' },
    [PSCustomObject]@{ Name = 'V006__recommendation_tags.sql'; File = 'sql/main/V006__recommendation_tags.sql'; Table = 'service_item_tag'; Column = '' },
    [PSCustomObject]@{ Name = 'V007__family_coordination.sql'; File = 'sql/main/V007__family_coordination.sql'; Table = 'elder_identity_claim'; Column = '' },
    [PSCustomObject]@{ Name = 'V008__username_login.sql'; File = 'sql/main/V008__username_login.sql'; Table = 'account'; Column = 'username' },
    [PSCustomObject]@{ Name = 'V009__demo_seed.sql'; File = 'sql/main/V009__demo_seed.sql'; Table = 'demo_seed_record'; Column = '' },
    [PSCustomObject]@{ Name = 'V010__audit_context.sql'; File = 'sql/main/V010__audit_context.sql'; Table = 'audit_event'; Column = 'ip' },
    [PSCustomObject]@{ Name = 'V011__order_status_index.sql'; File = 'sql/main/V011__order_status_index.sql'; Table = ''; Column = '' }
)
$paymentMigrations = @(
    [PSCustomObject]@{ Name = 'V000__migration.sql'; File = 'sql/payment/V000__migration.sql'; Table = ''; Column = '' },
    [PSCustomObject]@{ Name = 'V001__transactions.sql'; File = 'sql/payment/V001__transactions.sql'; Table = 'simulated_transaction'; Column = '' },
    [PSCustomObject]@{ Name = 'V002__order_version_idempotency.sql'; File = 'sql/payment/V002__order_version_idempotency.sql'; Table = 'simulated_transaction'; Column = 'active_order_version' }
)

function Invoke-MySql {
    param([string]$Database, [string]$Query, [string]$Source)
    $mysqlArgs = @(('--user=' + $migrationUser), '--host=127.0.0.1', ('--port=' + $mysqlPort), '--default-character-set=utf8mb4')
    if ($Database) { $mysqlArgs += '--database=' + $Database }
    if ($Source) { $Query = 'source ' + $Source }
    $mysqlArgs += @('--batch', '--skip-column-names', ('--execute=' + $Query))
    $output = & $mysql @mysqlArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL 操作失败（目标库：$Database）。请核对库是否已预建及迁移账号权限。" }
    return (($output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function Test-TableExists {
    param([string]$Database, [string]$Table)
    $query = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$Database' AND TABLE_NAME='$Table' AND TABLE_TYPE='BASE TABLE'"
    return (Invoke-MySql -Database $Database -Query $query) -eq '1'
}

function Test-ColumnExists {
    param([string]$Database, [string]$Table, [string]$Column)
    $query = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$Database' AND TABLE_NAME='$Table' AND COLUMN_NAME='$Column'"
    return (Invoke-MySql -Database $Database -Query $query) -eq '1'
}

function Invoke-MigrationSet {
    param([string]$Database, [object[]]$Migrations)
    $ledgerExists = Test-TableExists -Database $Database -Table 'schema_migration'
    $tableOutput = Invoke-MySql -Database $Database -Query "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$Database' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME"
    $tables = @($tableOutput -split "`n" | Where-Object { $_ })
    $businessTables = @($tables | Where-Object { $_ -ne 'schema_migration' })
    if (!$ledgerExists -and $businessTables.Count -gt 0) {
        throw "$Database 已有业务表但没有 schema_migration。为避免重放手动导入的 SQL，已停止；请先核对实际表结构与版本，再按 README 的人工基线说明处理。"
    }

    $applied = @()
    if ($ledgerExists) {
        $history = Invoke-MySql -Database $Database -Query 'SELECT script_name FROM schema_migration ORDER BY script_name'
        $applied = @($history -split "`n" | Where-Object { $_ })
    }
    if ($ledgerExists -and $applied.Count -eq 0 -and $businessTables.Count -gt 0) {
        throw "$Database 有业务表但 schema_migration 没有版本记录。疑似 Navicat 手动导入；请先备份并核对版本，不会执行任何迁移。"
    }
    $knownNames = @($Migrations | ForEach-Object { $_.Name })
    foreach ($name in $applied) {
        if ($name -notin $knownNames) { throw "$Database 的 schema_migration 包含未知版本 '$name'；请先人工核对，不会继续迁移。" }
    }
    for ($index = 0; $index -lt $applied.Count; $index++) {
        if ($applied[$index] -ne $Migrations[$index].Name) {
            throw "$Database 的迁移记录缺失或顺序不连续；请核对实际表结构后人工修复版本记录。"
        }
    }

    for ($index = 0; $index -lt $Migrations.Count; $index++) {
        $migration = $Migrations[$index]
        if ($index -lt $applied.Count) { continue }
        if ($migration.Table -and (Test-TableExists -Database $Database -Table $migration.Table)) {
            $alreadyApplied = !$migration.Column -or (Test-ColumnExists -Database $Database -Table $migration.Table -Column $migration.Column)
            if ($Database -eq 'eldercare' -and $migration.Name -eq 'V008__username_login.sql') {
                $alreadyApplied = $alreadyApplied -or (Test-ColumnExists -Database $Database -Table 'family_invitation' -Column 'invitee_account_id') -or
                    !(Test-TableExists -Database $Database -Table 'worker_activation') -or !(Test-TableExists -Database $Database -Table 'email_verification')
            }
            if ($alreadyApplied) {
                throw "$Database 的 $($migration.Name) 未登记，但发现对应结构 $($migration.Table).$($migration.Column)。疑似 Navicat 手动导入；请先核对版本，不会重放迁移。"
            }
        }
        $null = Invoke-MySql -Database $Database -Query '' -Source $migration.File
        $scriptName = $migration.Name.Replace("'", "''")
        $null = Invoke-MySql -Database $Database -Query "INSERT INTO schema_migration(script_name) VALUES('$scriptName')"
        $applied += $migration.Name
        Write-Host "已应用 $Database/$($migration.Name)"
    }

    $expectedTables = if ($Database -eq 'eldercare') {
        @('account','service_area','service_item','service_order','settlement','service_incident','outbox_event','service_item_tag','elder_identity_claim','demo_seed_record','family_invitation')
    } else {
        @('simulated_transaction','simulated_transaction_event')
    }
    foreach ($table in $expectedTables) {
        if (!(Test-TableExists -Database $Database -Table $table)) { throw "$Database 缺少预期表 $table；迁移结果不完整。" }
    }
    if ($Database -eq 'eldercare') {
        if (!(Test-ColumnExists -Database $Database -Table 'account' -Column 'username') -or
            !(Test-ColumnExists -Database $Database -Table 'family_invitation' -Column 'invitee_account_id') -or
            (Test-TableExists -Database $Database -Table 'worker_activation') -or
            (Test-TableExists -Database $Database -Table 'email_verification')) {
            throw 'eldercare 的账号/家庭邀请结构与 V008 不一致；请人工核对后再迁移。'
        }
    } elseif (!(Test-ColumnExists -Database $Database -Table 'simulated_transaction' -Column 'active_order_version')) {
        throw 'eldercare_payment 缺少模拟交易幂等字段 active_order_version。'
    }
}

$previousPassword = $env:MYSQL_PWD
$previousLocation = Get-Location
$env:MYSQL_PWD = $migrationPassword
try {
    Set-Location -LiteralPath $root
    foreach ($database in @('eldercare', 'eldercare_payment')) {
        $visible = Invoke-MySql -Database '' -Query "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$database'"
        if ($visible -ne '1') { throw "数据库 $database 不存在或迁移账号无权访问。请先在 Navicat 预建数据库和最小权限账号。" }
    }
    Invoke-MigrationSet -Database 'eldercare' -Migrations $mainMigrations
    Invoke-MigrationSet -Database 'eldercare_payment' -Migrations $paymentMigrations
    Write-Host '两个数据库的迁移记录与关键表结构检查完成。'
} finally {
    Set-Location -LiteralPath $previousLocation
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
}
