# BH 阶段起服：user-service(8082) + auth-service(8081) + file-service(8094, minio 模式)
# 为什么落成一个脚本：三条 Start-Process 的 env 必须在同一个进程里设、设完立刻清掉，
# 在 bash 里用双引号内联会被 $ 吃掉变量名（本轮踩过），PS 侧的报错又只在控制台闪一下。
$ErrorActionPreference = 'Stop'
$java = 'E:\jdk\bin\java.exe'
$logDir = 'E:\wowowo\devlog\_bh'

function Start-Svc([string]$name, [string]$jar, [hashtable]$env) {
    foreach ($k in $env.Keys) { [Environment]::SetEnvironmentVariable($k, $env[$k], 'Process') }
    Start-Process -FilePath $java `
        -ArgumentList @('-Xms96m', '-Xmx256m', '-jar', $jar) `
        -WorkingDirectory (Split-Path (Split-Path $jar)) `
        -WindowStyle Hidden `
        -RedirectStandardOutput "$logDir\$name.log" `
        -RedirectStandardError "$logDir\$name.err.log" | Out-Null
    foreach ($k in $env.Keys) { [Environment]::SetEnvironmentVariable($k, $null, 'Process') }
    Write-Output "launched $name"
}

Start-Svc 'user-service' 'E:\wowowo\backend\user-service\target\user-service-1.0.0-SNAPSHOT.jar' @{}
Start-Svc 'auth-service' 'E:\wowowo\backend\auth-service\target\auth-service-1.0.0-SNAPSHOT.jar' @{}
Start-Svc 'file-service' 'E:\wowowo\backend\file-service\target\file-service-1.0.0-SNAPSHOT.jar' @{ FILE_STORAGE_TYPE = 'minio' }
