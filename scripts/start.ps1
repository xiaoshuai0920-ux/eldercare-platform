$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root '.run'
if (Test-Path (Join-Path $runDir 'pids.json')) { throw '检测到已有启动记录；请先运行 scripts/stop.ps1 或检查进程。' }
$envFile = Join-Path $root '.env'
if (!(Test-Path -LiteralPath $envFile -PathType Leaf)) { throw '缺少项目根目录 .env；请先按 README 配置。' }
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile -Encoding utf8) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw '.env 格式错误：每行须为 KEY=VALUE。' }
    if ($settings.ContainsKey($Matches[1])) { throw ".env 中重复设置 $($Matches[1])。" }
    $settings[$Matches[1]] = $Matches[2]
}
foreach ($name in @('DB_PASSWORD','BOOTSTRAP_ADMIN_PASSWORD','PAYMENT_SERVICE_TOKEN','PAYMENT_CONSOLE_TOKEN')) {
    if (-not $settings.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($settings[$name])) { throw "请填写 .env 中的 $name。" }
    [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
}
$backendJar = Join-Path $root 'backend/target/eldercare-backend-0.1.0.jar'
$paymentJar = Join-Path $root 'mock-payment/target/eldercare-mock-payment-0.1.0.jar'
$frontPackage = Join-Path $root 'frontend/node_modules'
if (!(Test-Path $backendJar) -or !(Test-Path $paymentJar) -or !(Test-Path $frontPackage)) { throw '请先执行 backend 与 mock-payment 的 mvn package，以及 frontend 的 npm install。' }
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$java = (Get-Command java -ErrorAction Stop).Source
$node = (Get-Command node.exe -ErrorAction Stop).Source
$specs = @(
    @{ Name='mock-payment'; File=$java; Args=@('-jar',('"'+$paymentJar+'"')); Dir=(Join-Path $root 'mock-payment') },
    @{ Name='backend'; File=$java; Args=@('-jar',('"'+$backendJar+'"')); Dir=(Join-Path $root 'backend') },
    @{ Name='frontend'; File=$node; Args=@('node_modules/vite/bin/vite.js','--host','127.0.0.1'); Dir=(Join-Path $root 'frontend') }
)
$started = @()
try {
    foreach ($spec in $specs) {
        $out = Join-Path $runDir ($spec.Name + '.out.log')
        $err = Join-Path $runDir ($spec.Name + '.err.log')
        $process = Start-Process -FilePath $spec.File -ArgumentList $spec.Args -WorkingDirectory $spec.Dir -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
        $started += [PSCustomObject]@{ name=$spec.Name; pid=$process.Id; startTimeTicks=$process.StartTime.Ticks }
        Write-Host "已启动 $($spec.Name)，PID $($process.Id)"
    }
    foreach ($endpoint in @('http://127.0.0.1:8081/ready','http://127.0.0.1:8080/api/ready','http://127.0.0.1:5173/login','http://127.0.0.1:5173/src/main.ts')) {
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            try {
                $response = Invoke-WebRequest -Uri $endpoint -UseBasicParsing -TimeoutSec 2
                if ($response.StatusCode -eq 200) { $ready = $true; break }
            } catch { Start-Sleep -Milliseconds 800 }
        }
        if (!$ready) { throw "本地服务未就绪：$endpoint。请检查 .run/ 中的错误日志与数据库配置。" }
    }
    $started | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runDir 'pids.json') -Encoding utf8
    Write-Host '本地入口：http://127.0.0.1:5173；三个服务均已通过就绪检查。'
} catch {
    foreach ($entry in $started) {
        $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
        if ($null -ne $process -and $process.StartTime.Ticks -eq $entry.startTimeTicks) { Stop-Process -Id $entry.pid -ErrorAction SilentlyContinue }
    }
    throw
}
