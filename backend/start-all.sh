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
export MINIO_SECRET_KEY=minioadmin
export ES_HOSTS=localhost:9200

# Service definitions: name:port:jar
SERVICES=(
    "gateway-service:8080"
    "auth-service:8081"
    "user-service:8082"
    "datasource-service:8083"
    "etl-service:8084"
    "model-service:8085"
    "analysis-service:8086"
    "dashboard-service:8087"
    "screen-service:8088"
    "collab-service:8089"
    "alert-service:8090"
    "ai-service:8091"
    "openapi-service:8092"
    "admin-service:8093"
    "file-service:8094"
    "schedule-service:8095"
    "monitor-service:8096"
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
