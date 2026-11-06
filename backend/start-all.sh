#!/bin/bash
# DataViz Backend Services Startup Script
# Usage: bash start-all.sh

BACKEND_DIR="/e/wowowo/backend"
LOG_DIR="$BACKEND_DIR/logs"
mkdir -p "$LOG_DIR"

# Common environment
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=redis123456
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_USER=root
export MYSQL_PASSWORD=root123456
export KAFKA_SERVERS=localhost:9092
export NACOS_ADDR=localhost:8848
export NACOS_NAMESPACE=
export MINIO_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY=minioadmin
# 与 docker-compose.yml 的 MINIO_ROOT_PASSWORD 一致（compose 里是 minioadmin123）。
# 这里原来写 minioadmin：脚本一 export 就会盖掉 file-service yml 的正确默认值，
# 于是"照 start-all.sh 起的栈"里 MinIO 模式必然探测失败——与 API-153 同一颗坑，两处都要堵。
export MINIO_SECRET_KEY=minioadmin123
export ES_HOSTS=localhost:9200

# Service definitions: name:port:jar
SERVICES=(
    "gateway-service:17080"
    "auth-service:17081"
    "user-service:17082"
    "datasource-service:17083"
    "etl-service:17084"
    "model-service:17085"
    "analysis-service:17086"
    "dashboard-service:17087"
    "screen-service:17088"
    "collab-service:17089"
    "alert-service:17090"
    "ai-service:17091"
    "openapi-service:17092"
    "admin-service:17093"
    "file-service:17094"
    "schedule-service:17095"
    "monitor-service:17096"
)

echo "=========================================="
echo "  DataViz Backend Services Startup"
echo "=========================================="

for entry in "${SERVICES[@]}"; do
    IFS=':' read -r name port <<< "$entry"
    jar_file="$BACKEND_DIR/$name/target/${name}-1.0.0-SNAPSHOT.jar"
    log_file="$LOG_DIR/${name}.log"

    if [ ! -f "$jar_file" ]; then
        echo "[SKIP] $name - jar not found: $jar_file"
        continue
    fi

    # Check if port is already in use
    if netstat -an 2>/dev/null | grep -q ":${port} " | grep -q "LISTEN"; then
        echo "[SKIP] $name - port $port already in use"
        continue
    fi

    echo "[START] $name on port $port ..."
    java -jar "$jar_file" > "$log_file" 2>&1 &
    pid=$!
    echo "$pid" > "$LOG_DIR/${name}.pid"
    echo "[OK] $name started (PID: $pid), log: $log_file"
    sleep 2
done

echo ""
echo "=========================================="
echo "  All services started!"
echo "  Logs directory: $LOG_DIR"
echo "=========================================="
echo ""
echo "Check status: bash $0 status"
echo "Stop all:     bash $0 stop"

if [ "$1" = "status" ]; then
    echo ""
    echo "Service status:"
    for entry in "${SERVICES[@]}"; do
        IFS=':' read -r name port <<< "$entry"
        pid_file="$LOG_DIR/${name}.pid"
        if [ -f "$pid_file" ]; then
            pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                echo "  [RUNNING] $name (PID: $pid, port: $port)"
            else
                echo "  [STOPPED] $name (port: $port)"
            fi
        else
            echo "  [UNKNOWN] $name (port: $port)"
        fi
    done
fi
