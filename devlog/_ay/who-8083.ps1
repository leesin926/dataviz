$pid8083 = (Get-NetTCPConnection -LocalPort 8083 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
$p = Get-CimInstance Win32_Process -Filter "ProcessId=$pid8083"
Write-Output ("PID=" + $pid8083)
Write-Output ("NAME=" + $p.Name)
Write-Output ("CMD=" + $p.CommandLine)
Write-Output ("START=" + $p.CreationDate.ToString('yyyy-MM-dd HH:mm:ss'))
