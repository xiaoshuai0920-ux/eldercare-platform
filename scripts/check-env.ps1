$ErrorActionPreference = 'Stop'
Write-Host '居家养老平台 Windows 环境检查'
foreach ($tool in @('java','mvn','node','npm','mysql')) {
    $command = Get-Command $tool -ErrorAction SilentlyContinue
    if ($null -eq $command) { Write-Host "[缺失] $tool"; continue }
    Write-Host "[找到] $tool : $($command.Source)"
}
if (@('java','mvn','node','npm','mysql') | Where-Object { -not (Get-Command $_ -ErrorAction SilentlyContinue) }) { throw '必要工具缺失，请先安装后再继续。' }
Write-Host '版本：'
& java -version
& mvn -version
& node --version
& npm --version
& mysql --version
Write-Host '数据库连接请先填写项目根目录 .env 的 DB_PASSWORD，并确认 MySQL 8 在 127.0.0.1:3306 可达；此环境检查不测试数据库登录。'
