# BH 阶段起服（第二批）：datasource-service(8083) —— 新的 connector 路由（CK/ES HTTP、Mongo 只做可达性）要在这上面实测
$ErrorActionPreference = 'Stop'
$java = 'E:\jdk\bin\java.exe'
Start-Process -FilePath $java `
    -ArgumentList @('-Xms96m', '-Xmx256m', '-jar', 'E:\wowowo\backend\datasource-service\target\datasource-service-1.0.0-SNAPSHOT.jar') `
    -WorkingDirectory 'E:\wowowo\backend\datasource-service' `
    -WindowStyle Hidden `
    -RedirectStandardOutput 'E:\wowowo\devlog\_bh\datasource-service.log' `
    -RedirectStandardError 'E:\wowowo\devlog\_bh\datasource-service.err.log' | Out-Null
Write-Output 'launched datasource-service'
