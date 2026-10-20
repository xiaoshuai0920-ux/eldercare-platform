$ErrorActionPreference = 'Stop'

# 统一的 .env 读取逻辑：所有脚本共用，避免出现第二套凭据来源。
function Read-DotEnvFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw '缺少项目根目录 .env；请先复制 .env.example 为 .env 并填写。' }
    $settings = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw '.env 格式错误：每行须为 KEY=VALUE，KEY 只能是大写字母、数字或下划线。' }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($settings.Contains($key)) { throw ".env 中重复设置 $key。" }
        $settings[$key] = $value
    }
    return $settings
}

# 校验必需键后写入当前进程环境变量，供 Spring Boot 占位符解析。
function Import-DotEnv {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string[]]$Required = @()
    )
    $settings = Read-DotEnvFile -Path $Path
    foreach ($name in $Required) {
        if (-not $settings.Contains($name) -or [string]::IsNullOrWhiteSpace($settings[$name])) {
            throw "请填写 .env 中的 $name（可参考 .env.example）。"
        }
    }
    foreach ($key in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
    }
    return $settings
}
