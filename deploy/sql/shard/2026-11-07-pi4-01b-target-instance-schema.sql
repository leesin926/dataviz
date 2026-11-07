-- PI4 · 目标实例（ds1 / 3307）的分片基表镜像 —— 结构 only，零行
--
-- 为什么需要它：pi4-02 用 CREATE TABLE ... LIKE 建月度表，LIKE 要求基表在**同一实例**上存在。
-- dataviz-mysql-2 是空实例（docker-compose.yml 里没挂 init SQL，原因写在同一处），
-- 所以不镜像基表的话 3307 上一条月度表都建不出来，落在 ds1 的租户写入即失败。
--
-- 执行顺序：pi4-01（派生 tenant_id）→ **本文件（只在 ds1 跑）** → pi4-02（两个实例各跑）→ pi4-03（回填）
--
-- ⚠️ 这里建出来的 7 张基表在 ds1 上**永远是空的**，这是设计而不是漏：
--    分片读写都会被 ShardTableNameHandler 改写成 _YYYYMM 物理表，逻辑基表只是 LIKE 的模板。
--    真要在 ds1 上查数据，查的是 db_x.t_YYYYMM，不是 db_x.t。
--
-- 生成物，别手改：重跑 node devlog/_pi/gen-shard-ddl.cjs 与 node devlog/_pi/gen-ds1-schema.cjs。
SET NAMES utf8mb4;

-- ==== db_alert（alert_event, alert_notify_log）====
CREATE DATABASE IF NOT EXISTS `db_alert` DEFAULT CHARACTER SET utf8mb4;
USE `db_alert`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `alert_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_id` bigint NOT NULL,
  `rule_name` varchar(256) DEFAULT NULL,
  `trigger_value` decimal(20,4) DEFAULT NULL,
  `severity` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `message` text,
  `notified_at` datetime DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3544 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å‘Šè­¦äº‹ä»¶è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `alert_notify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` bigint NOT NULL,
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户，由 event_id 对应的事件派生',
  `channel` varchar(32) NOT NULL,
  `recipient` varchar(256) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'SUCCESS',
  `error_message` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_event_id` (`event_id`),
  KEY `idx_tenant_time` (`tenant_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4040 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å‘Šè­¦é€šçŸ¥æ—¥å¿—';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ==== db_openapi（openapi_log）====
CREATE DATABASE IF NOT EXISTS `db_openapi` DEFAULT CHARACTER SET utf8mb4;
USE `db_openapi`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `openapi_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_id` bigint DEFAULT NULL COMMENT '客户端ID(openapi_client.id)',
  `tenant_id` bigint DEFAULT NULL COMMENT '调用方所属租户，由 client_id/app_key 派生',
  `app_name` varchar(128) DEFAULT NULL COMMENT '应用名称',
  `api_path` varchar(256) DEFAULT NULL COMMENT '被调用的接口路径',
  `method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法 GET/POST/PUT/DELETE',
  `request_params` text COMMENT '请求参数JSON',
  `response_code` int DEFAULT NULL COMMENT 'HTTP响应码',
  `execution_time` bigint DEFAULT NULL COMMENT '耗时(ms)',
  `ip` varchar(64) DEFAULT NULL COMMENT '调用方IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_client_time` (`client_id`,`create_time`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_tenant_time` (`tenant_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=918 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='开放平台调用日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ==== db_auth（sys_login_log）====
CREATE DATABASE IF NOT EXISTS `db_auth` DEFAULT CHARACTER SET utf8mb4;
USE `db_auth`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) NOT NULL,
  `login_type` tinyint NOT NULL COMMENT '1-å¯†ç  2-çŸ­ä¿¡ 3-SSO',
  `status` tinyint NOT NULL COMMENT '0-å¤±è´¥ 1-æˆåŠŸ',
  `ip` varchar(64) DEFAULT '',
  `user_agent` varchar(512) DEFAULT '',
  `message` varchar(256) DEFAULT '',
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_user` (`tenant_id`,`user_id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç™»å½•æ—¥å¿—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ==== db_analysis（query_history）====
CREATE DATABASE IF NOT EXISTS `db_analysis` DEFAULT CHARACTER SET utf8mb4;
USE `db_analysis`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `query_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `user_id` varchar(64) NOT NULL COMMENT '执行用户(用户名)',
  `dataset_id` bigint DEFAULT NULL COMMENT '数据集ID(旧模型遗留，实体不再写入)',
  `datasource_id` bigint DEFAULT NULL COMMENT '数据源ID(实体字段，历史数据为NULL)',
  `query_json` json DEFAULT NULL COMMENT '查询DSL(旧模型遗留，实体不再写入)',
  `sql_text` text,
  `sql` text COMMENT '执行的SQL(实体映射列，与 sql_text 并存)',
  `duration_ms` int DEFAULT NULL,
  `execution_time` bigint DEFAULT NULL COMMENT '执行耗时(ms，实体字段)',
  `row_count` int DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行状态(SUCCESS/FAILED)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除(0=未删,1=已删)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=954 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æŸ¥è¯¢åŽ†å²è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ==== db_admin（audit_log）====
CREATE DATABASE IF NOT EXISTS `db_admin` DEFAULT CHARACTER SET utf8mb4;
USE `db_admin`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT '',
  `module` varchar(64) DEFAULT '',
  `action` varchar(64) DEFAULT '',
  `method` varchar(16) DEFAULT '',
  `request_url` varchar(256) DEFAULT '',
  `request_params` text,
  `response_code` int DEFAULT NULL,
  `ip` varchar(64) DEFAULT '',
  `user_agent` varchar(512) DEFAULT '',
  `execution_time` bigint DEFAULT NULL COMMENT 'ms',
  `tenant_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_username` (`username`),
  KEY `idx_module_action` (`module`,`action`)
) ENGINE=InnoDB AUTO_INCREMENT=9025 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æŽ¥å£æ“ä½œå®¡è®¡';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ==== db_monitor（audit_log）====
CREATE DATABASE IF NOT EXISTS `db_monitor` DEFAULT CHARACTER SET utf8mb4;
USE `db_monitor`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT '',
  `module` varchar(64) NOT NULL,
  `action` varchar(64) NOT NULL,
  `target_type` varchar(64) DEFAULT '',
  `target_id` varchar(64) DEFAULT '',
  `detail` text,
  `ip` varchar(64) DEFAULT '',
  `user_agent` varchar(512) DEFAULT '',
  `duration` int DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_time` (`tenant_id`,`create_time`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=907 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å®¡è®¡æ—¥å¿—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- 回读自检：每张逻辑表在目标实例上应该恰好存在 1 张（结构镜像，不装数据），条数 = 7
SELECT CONCAT('@@BASE ', table_schema, '.', table_name) AS check_line
FROM information_schema.TABLES
WHERE (table_schema, table_name) IN (
  ('db_alert','alert_event'),
  ('db_alert','alert_notify_log'),
  ('db_openapi','openapi_log'),
  ('db_auth','sys_login_log'),
  ('db_analysis','query_history'),
  ('db_admin','audit_log'),
  ('db_monitor','audit_log')
)
ORDER BY 1;
