# PI5: restart openapi-service (17092) with SHARD_ENABLED=true for the cross-instance read criterion.
# ASCII-only on purpose: PowerShell 5.1 reads .ps1 as ANSI, so UTF-8 Chinese inside quoted strings
# can swallow the closing quote and the whole script fails to parse (seen 2026-11-07). Keep comments English.
#
# PI6 起这条只剩转发：安全判断（"只动 java.exe + 这个 fat jar"两条同时成立才动手）现在住在
# restart-service-sharded.ps1 一处。两处各写一遍守卫，就是 PI4 那条"同一个数两处算"的同族问题。
# 保留本文件与原有参数，是因为台账与判据记录里引用的这个名字。
param(
    [switch]$ShardOff,
    [switch]$StopOnly
)
# $args 是 PowerShell 的自动变量，不能拿来当自己的数组用（会静默吞掉参数），所以叫 $forward
$forward = @('-Port', '17092', '-Service', 'openapi-service')
if ($ShardOff) { $forward += '-ShardOff' }
if ($StopOnly) { $forward += '-StopOnly' }

& (Join-Path $PSScriptRoot 'restart-service-sharded.ps1') @forward
exit $LASTEXITCODE
