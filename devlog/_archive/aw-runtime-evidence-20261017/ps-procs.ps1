Get-CimInstance Win32_Process -Filter "Name='electron.exe'" | ForEach-Object {
  $cmd = $_.CommandLine
  if ($cmd.Length -gt 200) { $cmd = $cmd.Substring(0, 200) }
  "{0} | ppid={1} | {2} | {3}" -f $_.ProcessId, $_.ParentProcessId, $_.CreationDate.ToString('MM-dd HH:mm:ss'), $cmd
}
