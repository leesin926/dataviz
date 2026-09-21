FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="dataviz-team"

ARG JAR_FILE=target/*.jar
ARG SERVICE_PORT=8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVICE_PORT=${SERVICE_PORT}

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY ${JAR_FILE} app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:${SERVICE_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${SERVICE_PORT} -jar app.jar"]
