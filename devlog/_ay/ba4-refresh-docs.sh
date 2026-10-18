#!/bin/bash
# 刷新 16 个服务的 /v3/api-docs 快照到指定目录（直连端口，不过网关 ⇒ 无 60/min 限流）
DIR=${1:-/e/wowowo/devlog/_ay/docs-ba4}
mkdir -p "$DIR" || exit 1
cd "$DIR" || exit 1
NAMES="8081-auth-service 8082-user-service 8083-datasource-service 8084-etl-service 8085-model-service 8086-analysis-service 8087-dashboard-service 8088-screen-service 8089-collab-service 8090-alert-service 8091-ai-service 8092-openapi-service 8093-admin-service 8094-file-service 8095-schedule-service 8096-monitor-service"
for n in $NAMES; do
  port=${n:0:4}
  tmp=$(mktemp)
  code=$(curl -s -o "$tmp" -w '%{http_code}' "http://127.0.0.1:$port/v3/api-docs")
  size=$(wc -c < "$tmp")
  if [ "$code" = "200" ] && [ "$size" -gt 1000 ]; then
    mv "$tmp" "$n.json"
    echo "$n -> OK ${size} B"
  else
    rm -f "$tmp"
    echo "$n -> FAIL (HTTP $code, ${size} B)"
  fi
  sleep 0.2
done
