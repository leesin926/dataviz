# PI1 · 按新端口段起服务（17080-17096）：gateway + auth + user + datasource + openapi + file(minio 模式)
# 每个进程的 env 都在这里设，设完立刻清掉，免得下一个进程继承上一个的变量。
$ErrorActionPreference = 'Stop'
$java = 'E:\jdk\bin\java.exe'
$logDir = 'E:\wowowo\devlog\_pi'

function Start-Svc([string]$name, [string]$jar, [hashtable]$envMap) {
    foreach ($k in $envMap.Keys) { [Environment]::SetEnvironmentVariable($k, $envMap[$k], 'Process') }
    Start-Process -FilePath $java `
        -ArgumentList @('-Xms96m', '-Xmx256m', '-jar', $jar) `
        -WorkingDirectory (Split-Path (Split-Path $jar)) `
        -WindowStyle Hidden `
        -RedirectStandardOutput "$logDir\$name.log" `
        -RedirectStandardError "$logDir\$name.err.log" | Out-Null
    foreach ($k in $envMap.Keys) { [Environment]::SetEnvironmentVariable($k, $null, 'Process') }
    Write-Output "launched $name"
    Start-Sleep -Seconds 2
}

Start-Svc 'gateway'    'E:\wowowo\backend\gateway-service\target\gateway-service-1.0.0-SNAPSHOT.jar' @{}
Start-Svc 'user'       'E:\wowowo\backend\user-service\target\user-service-1.0.0-SNAPSHOT.jar' @{}
Start-Svc 'auth'       'E:\wowowo\backend\auth-service\target\auth-service-1.0.0-SNAPSHOT.jar' @{}
Start-Svc 'datasource' 'E:\wowowo\backend\datasource-service\target\datasource-service-1.0.0-SNAPSHOT.jar' @{}
Start-Svc 'openapi'    'E:\wowowo\backend\openapi-service\target\openapi-service-1.0.0-SNAPSHOT.jar' @{}
Start-Svc 'file'       'E:\wowowo\backend\file-service\target\file-service-1.0.0-SNAPSHOT.jar' @{ FILE_STORAGE_TYPE = 'minio' }
