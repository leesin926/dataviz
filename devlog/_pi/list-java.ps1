Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
  $pid_ = $_.ProcessId
  $cmd = $_.CommandLine
  if ($null -eq $cmd) { "{0} <no-command-line>" -f $pid_; return }
  if ($cmd -match '-jar\s+(\S+\.jar)') {
    "{0} JAR {1}" -f $pid_, $matches[1]
  } elseif ($cmd -match '-cp\s+(\S{0,60})') {
    "{0} CP {1}" -f $pid_, $matches[1]
  } else {
    "{0} OTHER" -f $pid_
  }
}
