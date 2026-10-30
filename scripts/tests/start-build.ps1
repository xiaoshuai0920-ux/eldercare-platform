$ErrorActionPreference = 'Stop'
$sourceScripts = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $sourceScripts)
$fixture = Join-Path $workspaceRoot ('.tmp/start-build-test-' + [guid]::NewGuid().ToString('N'))
foreach ($dir in @('scripts', 'backend/target', 'mock-payment/target', 'frontend/node_modules')) {
    New-Item -ItemType Directory -Path (Join-Path $fixture $dir) -Force | Out-Null
}
Copy-Item -LiteralPath (Join-Path $sourceScripts 'start.ps1'), (Join-Path $sourceScripts '_env.ps1') -Destination (Join-Path $fixture 'scripts')
foreach ($jar in @('backend/target/eldercare-backend-0.1.0.jar', 'mock-payment/target/eldercare-mock-payment-0.1.0.jar')) {
    Set-Content -LiteralPath (Join-Path $fixture $jar) -Value 'fixture'
}
@'
DB_USER=fixture_app
DB_PASSWORD=fixture
PAYMENT_DB_USER=fixture_payment_app
PAYMENT_DB_PASSWORD=fixture
BOOTSTRAP_ADMIN_PASSWORD=fixture
DEMO_ACCOUNT_PASSWORD=fixture
PAYMENT_SERVICE_TOKEN=fixture
PAYMENT_CONSOLE_TOKEN=fixture
MYSQL_PORT=3307
VITE_PORT=5177
'@ | Set-Content -LiteralPath (Join-Path $fixture '.env') -Encoding UTF8

$global:startupTestBuilds = [System.Collections.Generic.List[string]]::new()
$global:startupTestPorts = [System.Collections.Generic.List[string]]::new()
function global:mvn { $global:startupTestBuilds.Add((Get-Location).Path); $global:LASTEXITCODE = 0 }
function global:Start-Process { $global:startupTestPorts.Add([Environment]::GetEnvironmentVariable('MYSQL_PORT', 'Process')); throw 'fixture prevents real process start' }
try {
    foreach ($skip in @($false, $true)) {
        $global:startupTestBuilds.Clear()
        $global:startupTestPorts.Clear()
        try {
            & (Join-Path $fixture 'scripts/start.ps1') -SkipBuild:$skip
            throw 'Expected the fixture process guard to stop startup.'
        } catch {
            if ($_.Exception.Message -ne 'fixture prevents real process start') { throw }
        }
        if (!$skip -and ($global:startupTestBuilds.Count -ne 2 -or
                (Split-Path -Leaf $global:startupTestBuilds[0]) -ne 'mock-payment' -or
                (Split-Path -Leaf $global:startupTestBuilds[1]) -ne 'backend')) {
            throw 'Default startup must rebuild both services even when JARs exist.'
        }
        if ($skip -and $global:startupTestBuilds.Count -ne 0) { throw '-SkipBuild must not invoke Maven.' }
        if ($global:startupTestPorts.Count -ne 1 -or $global:startupTestPorts[0] -ne '3307') { throw 'MYSQL_PORT must reach the local database-backed service.' }
    }
    Write-Host 'Default rebuild and explicit -SkipBuild checks passed; no service or database was started.'
} finally {
    Remove-Item Function:global:mvn, Function:global:Start-Process -ErrorAction SilentlyContinue
    Remove-Variable startupTestBuilds -Scope Global -ErrorAction SilentlyContinue
    Remove-Variable startupTestPorts -Scope Global -ErrorAction SilentlyContinue
}
