# 可视化数据分析及大屏展示系统

企业级全链路数据可视化平台，覆盖数据接入、ETL 处理、数据建模、自助分析、仪表板编辑到大屏展示的完整能力。

## 项目结构

```
wowowo/
├── backend/                          # 后端微服务 (Java 17 + Spring Boot 3.2)
│   ├── common/                       # 公共模块
│   │   ├── common-core/              # 核心工具
│   │   ├── common-redis/             # Redis 封装
│   │   ├── common-mybatis/           # MyBatis-Plus 封装
│   │   ├── common-security/          # 安全认证
│   │   ├── common-log/               # 日志组件
│   │   ├── common-swagger/           # API 文档
│   │   ├── common-kafka/             # Kafka 封装
│   │   ├── common-minio/             # MinIO 封装
│   │   └── common-websocket/         # WebSocket 封装
│   ├── gateway-service/              # API 网关 :8080
│   ├── auth-service/                 # 认证授权 :8081
│   ├── user-service/                 # 用户管理 :8082
│   ├── datasource-service/           # 数据源管理 :8083
│   ├── etl-service/                  # ETL 编排 :8084
│   ├── model-service/                # 数据建模 :8085
│   ├── analysis-service/             # 自助分析 :8086
│   ├── dashboard-service/            # 仪表板 :8087
│   ├── screen-service/               # 大屏管理 :8088
│   ├── collab-service/               # 协作服务 :8089
│   ├── alert-service/                # 告警服务 :8090
│   ├── ai-service/                   # AI 服务 :8091
│   ├── openapi-service/              # 开放 API :8092
│   ├── admin-service/                # 平台管理 :8093
│   ├── file-service/                 # 文件服务 :8094
│   ├── schedule-service/             # 调度服务 :8095
│   ├── monitor-service/              # 监控服务 :8096
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                         # 前端 Monorepo (Vue 3 + TypeScript)
│   ├── packages/
│   │   ├── shared-utils/             # 工具函数
│   │   ├── shared-types/             # TypeScript 类型
│   │   ├── api-client/               # API 请求封装
│   │   ├── chart-engine/             # 图表渲染引擎
│   │   ├── screen-engine/            # 大屏渲染引擎
│   │   ├── etl-designer/             # ETL 可视化编排
│   │   ├── query-engine/             # 查询引擎
│   │   └── permission/               # 权限控制
│   ├── apps/
│   │   ├── pc-web/                   # PC 浏览器端
│   │   ├── admin/                    # 管理后台
│   │   └── screen-player/            # 大屏播放端
│   ├── Dockerfile
│   ├── pnpm-workspace.yaml
│   └── package.json
├── deploy/                           # 部署配置
│   ├── sql/init/                     # 数据库初始化脚本
│   ├── k8s/                          # Kubernetes 配置
│   ├── prometheus/                   # Prometheus 配置
│   ├── grafana/                      # Grafana 配置
│   ├── apisix/                       # APISIX 网关配置
│   └── nginx/                        # Nginx 配置
├── docs/                             # 项目文档
├── docker-compose.yml                # 开发环境 Docker Compose
├── .gitlab-ci.yml                    # CI/CD 配置
└── .gitignore
```

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- pnpm 8+
- Docker & Docker Compose
- Maven 3.9+

### 1. 启动基础设施

```bash
docker-compose up -d mysql redis nacos kafka elasticsearch minio
```

### 2. 启动后端

```bash
cd backend
mvn clean install -DskipTests
# 启动网关
cd gateway-service && mvn spring-boot:run
# 启动其他服务...
```

### 3. 启动前端

```bash
cd frontend
pnpm install
pnpm dev:pc
```

### 4. 访问

- 前端: http://localhost:5173
- API 网关: http://localhost:8080
- Nacos: http://localhost:8848/nacos
- MinIO: http://localhost:9001
- Grafana: http://localhost:3000

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.2, Spring Cloud Alibaba 2023 |
| 前端 | Vue 3, TypeScript, Vite 5, Element Plus |
| 数据库 | MySQL 8.0, Redis 7, ClickHouse, MongoDB |
| 消息 | Kafka 3.6 |
| 搜索 | Elasticsearch 8 |
| 存储 | MinIO |
| 调度 | XXL-Job |
| 部署 | Docker, Kubernetes, GitLab CI/CD |
| 监控 | Prometheus, Grafana, SkyWalking |

## 文档

- [总体技术架构文档](docs/01-总体技术架构文档.md)
- [总体功能点列表](docs/02-总体功能点列表.md)
- [PC 浏览器端开发文档](docs/03-PC浏览器端开发文档.md)
- [PC 桌面端开发文档](docs/04-PC桌面端开发文档.md)
- [小程序端开发文档](docs/05-小程序端开发文档.md)
- [手机 App 端开发文档](docs/06-手机App端开发文档.md)
- [平板 App 端开发文档](docs/07-平板App端开发文档.md)
- [大屏端开发文档](docs/08-大屏端开发文档.md)
- [后端服务开发文档](docs/09-后端服务开发文档.md)
