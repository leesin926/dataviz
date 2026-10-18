$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
  CommandLine      = 'cmd /c E:\wowowo\devlog\_ay\start-8087.bat'
  CurrentDirectory = 'E:\wowowo'
}
Write-Output ("ReturnValue=" + $r.ReturnValue + " PID=" + $r.ProcessId)
