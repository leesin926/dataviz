# BH 阶段起服（第三批）：openapi-service(8090) —— 回调 HMAC 链路要在这上面实测
$ErrorActionPreference = 'Stop'
$java = 'E:\jdk\bin\java.exe'
Start-Process -FilePath $java `
    -ArgumentList @('-Xms96m', '-Xmx256m', '-jar', 'E:\wowowo\backend\openapi-service\target\openapi-service-1.0.0-SNAPSHOT.jar') `
    -WorkingDirectory 'E:\wowowo\backend\openapi-service' `
    -WindowStyle Hidden `
    -RedirectStandardOutput 'E:\wowowo\devlog\_bh\openapi-service.log' `
    -RedirectStandardError 'E:\wowowo\devlog\_bh\openapi-service.err.log' | Out-Null
Write-Output 'launched openapi-service'
