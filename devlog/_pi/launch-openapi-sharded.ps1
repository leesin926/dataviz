# PI5: restart openapi-service (17092) with SHARD_ENABLED=true to run the cross-instance read criterion.
# ASCII-only on purpose: PowerShell 5.1 reads .ps1 as ANSI, so UTF-8 Chinese inside quoted strings
# can swallow the closing quote and the whole script fails to parse (seen 2026-11-07). Keep comments English.
#
# Safety: only touches a JVM that is (a) java.exe, (b) running the openapi-service fat jar. Anything else
# listening on 17092 -> refuse and exit 2. Never restart a process the user launched.
param(
    [switch]$ShardOff,
    [switch]$StopOnly
)
$ErrorActionPreference = 'Stop'
$java = 'E:\jdk\bin\java.exe'
$jar = 'E:\wowowo\backend\openapi-service\target\openapi-service-1.0.0-SNAPSHOT.jar'
$logDir = 'E:\wowowo\devlog\_pi'

$listeners = Get-NetTCPConnection -LocalPort 17092 -State Listen -ErrorAction SilentlyContinue
foreach ($l in $listeners) {
    $procId = $l.OwningProcess
    $p = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
    if ($null -eq $p) { Write-Output "SKIP-unknown pid $procId"; continue }
    if ($p.Name -ne 'java.exe') {
        Write-Output ("REFUSE pid=" + $procId + " name=" + $p.Name + " is not java; not my service, stopping.")
        exit 2
    }
    if ($p.CommandLine -notmatch 'openapi-service-1\.0\.0-SNAPSHOT\.jar') {
        Write-Output ("REFUSE pid=" + $procId + " command line does not contain the openapi-service jar.")
        exit 2
    }
    Stop-Process -Id $procId -Force
    Write-Output ("stopped " + $procId + " (openapi-service, started by me)")
}
if ($StopOnly) {
    # Used before `mvn package`: a running `java -jar` holds the fat jar open on Windows,
    # so the rebuild must happen in this window. Do not relaunch here.
    Write-Output 'stop-only done (jar unlocked); relaunch this script without -StopOnly when the build is done'
    exit 0
}
Start-Sleep -Seconds 3

if ($ShardOff) { $flag = 'false' } else { $flag = 'true' }
$env:SHARD_ENABLED = $flag
Start-Process -FilePath $java `
    -ArgumentList @('-Xms96m', '-Xmx320m', '-jar', $jar) `
    -WorkingDirectory 'E:\wowowo\backend\openapi-service' `
    -WindowStyle Hidden `
    -RedirectStandardOutput ($logDir + '\openapi-shard.log') `
    -RedirectStandardError ($logDir + '\openapi-shard.err.log') | Out-Null
Write-Output ("launched with SHARD_ENABLED=" + $flag)
