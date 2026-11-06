# PI1 · 把 17 个服务在新端口段（17080-17096）拉起来：已在听的端口跳过，不重复起、不动别人的进程。
# 为什么是 .ps1 而不是直接跑 start-all.sh：从工具会话里 nohup 起来的 JVM 会被回收（2026-10-18 实测 3.5 分钟就没了），
# Start-Process 起来的进程独立于本 shell，是这台机器上验证过能活下来的起法。
$ErrorActionPreference = 'Stop'
$java = 'E:\jdk\bin\java.exe'
$logDir = 'E:\wowowo\devlog\_pi'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

$svc = @(
    @{ n = 'gateway-service'; p = 17080 }, @{ n = 'auth-service'; p = 17081 },
    @{ n = 'user-service'; p = 17082 }, @{ n = 'datasource-service'; p = 17083 },
    @{ n = 'etl-service'; p = 17084 }, @{ n = 'model-service'; p = 17085 },
    @{ n = 'analysis-service'; p = 17086 }, @{ n = 'dashboard-service'; p = 17087 },
    @{ n = 'screen-service'; p = 17088 }, @{ n = 'collab-service'; p = 17089 },
    @{ n = 'alert-service'; p = 17090 }, @{ n = 'ai-service'; p = 17091 },
    @{ n = 'openapi-service'; p = 17092 }, @{ n = 'admin-service'; p = 17093 },
    @{ n = 'file-service'; p = 17094 }, @{ n = 'schedule-service'; p = 17095 },
    @{ n = 'monitor-service'; p = 17096 }
)

foreach ($s in $svc) {
    $jar = "E:\wowowo\backend\$($s.n)\target\$($s.n)-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { Write-Output "SKIP(jar) $($s.n)"; continue }
    if (Get-NetTCPConnection -LocalPort $s.p -State Listen -ErrorAction SilentlyContinue) {
        Write-Output "SKIP(up)  $($s.n) :$($s.p)"; continue
    }
    if ($s.n -eq 'file-service') { [Environment]::SetEnvironmentVariable('FILE_STORAGE_TYPE', 'minio', 'Process') }
    Start-Process -FilePath $java `
        -ArgumentList @('-Xms96m', '-Xmx256m', '-jar', $jar) `
        -WorkingDirectory "E:\wowowo\backend\$($s.n)" `
        -WindowStyle Hidden `
        -RedirectStandardOutput "$logDir\$($s.n).log" `
        -RedirectStandardError "$logDir\$($s.n).err.log" | Out-Null
    if ($s.n -eq 'file-service') { [Environment]::SetEnvironmentVariable('FILE_STORAGE_TYPE', $null, 'Process') }
    Write-Output "launched  $($s.n) :$($s.p)"
    Start-Sleep -Seconds 2
}
