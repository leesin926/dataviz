# PI6: generic "restart ONE of MY fat-jar services with SHARD_ENABLED on/off".
# ASCII-only on purpose: PowerShell 5.1 reads .ps1 as ANSI, so UTF-8 Chinese inside quoted strings
# can swallow the closing quote and the whole script fails to parse (seen 2026-11-07). Keep comments English.
#
# Safety (same two-condition guard as launch-openapi-sharded.ps1, which now delegates here so the rule
# lives in exactly one place):
#   - a listener on -Port must be java.exe AND its command line must contain that service's fat jar;
#   - anything else -> refuse and exit 2. Never touch a process I did not start.
param(
    [Parameter(Mandatory=$true)][int]$Port,
    [Parameter(Mandatory=$true)][string]$Service,
    [switch]$ShardOff,
    [switch]$StopOnly
)
$ErrorActionPreference = 'Stop'
$java = 'E:\jdk\bin\java.exe'
$logDir = 'E:\wowowo\devlog\_pi'
$jar = "E:\wowowo\backend\$Service\target\$Service-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) { Write-Output "REFUSE jar not found: $jar"; exit 3 }
$jarPattern = ($Service + '-1\.0\.0-SNAPSHOT\.jar')

$listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
foreach ($l in $listeners) {
    $procId = $l.OwningProcess
    $p = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
    if ($null -eq $p) { Write-Output "SKIP-unknown pid $procId"; continue }
    if ($p.Name -ne 'java.exe') {
        Write-Output ("REFUSE pid=" + $procId + " name=" + $p.Name + " is not java; not my service, stopping.")
        exit 2
    }
    if ($p.CommandLine -notmatch $jarPattern) {
        Write-Output ("REFUSE pid=" + $procId + " command line does not contain " + $Service + " fat jar.")
        exit 2
    }
    Stop-Process -Id $procId -Force
    Write-Output ("stopped " + $procId + " (" + $Service + ", started by me)")
}

if ($StopOnly) {
    # Used before `mvn package`: a running `java -jar` holds the fat jar open on Windows,
    # so the rebuild must happen in this window. Do not relaunch here.
    Write-Output 'stop-only done (jar unlocked); rerun without -StopOnly when the build is done'
    exit 0
}
Start-Sleep -Seconds 3

if ($ShardOff) { $flag = 'false' } else { $flag = 'true' }
$env:SHARD_ENABLED = $flag
Start-Process -FilePath $java `
    -ArgumentList @('-Xms96m', '-Xmx320m', '-jar', $jar) `
    -WorkingDirectory "E:\wowowo\backend\$Service" `
    -WindowStyle Hidden `
    -RedirectStandardOutput ($logDir + '\' + $Service + '-shard.log') `
    -RedirectStandardError ($logDir + '\' + $Service + '-shard.err.log') | Out-Null
Write-Output ("launched " + $Service + " on :" + $Port + " with SHARD_ENABLED=" + $flag)
