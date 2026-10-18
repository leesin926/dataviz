#!/bin/bash
# API-106 映射实证：三个路径各自的 HTTP 状态（8083 直连，免登可判，因为映射在鉴权之前）
for spec in "POST /api/datasource/test" "POST /api/datasource/test-connection" "POST /api/datasource/1/test-connection" "GET /api/datasource/list"; do
  m=${spec%% *}; p=${spec#* }
  code=$(curl -s -o /tmp/dsb.out -w '%{http_code}' -X "$m" "http://127.0.0.1:8083$p" \
    -H 'Content-Type: application/json' \
    -d '{"type":"mysql","config":"{\"host\":\"127.0.0.1\",\"port\":3306,\"database\":\"probe_db\",\"username\":\"probe\",\"password\":\"probe\"}"}')
  echo "$m $p -> HTTP $code | $(head -c 150 /tmp/dsb.out | tr -d '\n')"
done
