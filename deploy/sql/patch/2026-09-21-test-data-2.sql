-- =============================================================
-- DataViz 测试数据补丁（第二批）  2026-09-21（Round E / Y1 补漏）
--
-- 背景：第一批补丁（2026-09-21-test-data.sql）覆盖了有前端页面的模块，
--       但 ai / openapi / schedule / analysis_report / oauth_client 五块表为空，
--       对应 ~40 个接口无法验证，故补齐。
--
-- ★ 执行方式（必须带 --default-character-set=utf8mb4，否则中文会在入库时被 latin1 连接转成 `?`）：
--     docker exec -i dataviz-mysql mysql --default-character-set=utf8mb4 -uroot -proot123456 < 本文件
--   文件内也显式 SET NAMES utf8mb4，双保险。
--
-- 幂等：全部用 9xx 段固定 id + INSERT ... ON DUPLICATE KEY UPDATE，可反复执行。
-- =============================================================
SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 1. db_ai：会话 / 消息 / 少样本 / 洞察
--    FewShotExample.sql 是 MySQL 保留字列，实体已加 @TableField("`sql`")
-- -------------------------------------------------------------
USE db_ai;

INSERT INTO ai_conversation (id, tenant_id, user_id, title, create_time, update_time) VALUES
 (901, 1, 1, '销售大区 TOP5 怎么问', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 3 HOUR),
 (902, 1, 2, '用户行为事件分布分析', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
 (903, 1, 1, '订单退款率趋势', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE title = VALUES(title), update_time = VALUES(update_time);

INSERT INTO ai_message (id, conversation_id, role, content, sql_generated, token_count, create_time) VALUES
 (901, 901, 'user', '帮我看下各区域销售额排名前 5', NULL, 12, NOW() - INTERVAL 3 HOUR),
 (902, 901, 'assistant', '按区域汇总销售额并取前 5 名：', 'SELECT region, SUM(amount) AS amount FROM demo_sales_daily GROUP BY region ORDER BY amount DESC LIMIT 5', 86, NOW() - INTERVAL 3 HOUR),
 (903, 902, 'user', '各事件类型的平均时长是多少', NULL, 14, NOW() - INTERVAL 1 DAY),
 (904, 902, 'assistant', '按事件类型求平均停留时长：', 'SELECT event_type, AVG(duration_ms) AS avg_duration FROM demo_user_behavior GROUP BY event_type', 78, NOW() - INTERVAL 1 DAY),
 (905, 903, 'user', '最近退款率有上升趋势吗', NULL, 13, NOW() - INTERVAL 2 DAY),
 (906, 903, 'assistant', '按天统计支付金额与退款金额比例：', 'SELECT created_at, SUM(pay_amount) AS amount, status FROM demo_orders GROUP BY created_at, status', 91, NOW() - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE content = VALUES(content), sql_generated = VALUES(sql_generated);

INSERT INTO few_shot_example (id, dataset_id, question, `sql`, category) VALUES
 (901, 901, '各区域销售额排名', 'SELECT region, SUM(amount) AS amount FROM demo_sales_daily GROUP BY region ORDER BY amount DESC', 'aggregate'),
 (902, 901, '每日销售额趋势', 'SELECT stat_date, SUM(amount) AS amount FROM demo_sales_daily GROUP BY stat_date ORDER BY stat_date', 'trend'),
 (903, 902, '事件类型平均时长', 'SELECT event_type, AVG(duration_ms) AS avg_duration FROM demo_user_behavior GROUP BY event_type', 'aggregate'),
 (904, 903, '订单状态分布', 'SELECT status, COUNT(*) AS cnt FROM demo_orders GROUP BY status', 'distribution')
ON DUPLICATE KEY UPDATE question = VALUES(question), `sql` = VALUES(`sql`), category = VALUES(category);

INSERT INTO ai_insight (id, tenant_id, datasource_id, dataset_id, insight_type, content, config, create_time) VALUES
 (901, 1, 901, 901, 'ANOMALY', '华东区 09-19 销售额较 7 日均值高出 42%，建议核对大额订单来源。', '{"windowDays":7,"threshold":0.3}', NOW() - INTERVAL 6 HOUR),
 (902, 1, 901, 902, 'TREND', '页面停留时长近 3 日持续下降，累计降幅 11%。', '{"metric":"duration_ms","direction":"down"}', NOW() - INTERVAL 1 DAY),
 (903, 1, 902, 903, 'SUMMARY', '待支付订单占比 18%，是履约时效的主要卡点。', '{"dimension":"status"}', NOW() - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE content = VALUES(content);

-- -------------------------------------------------------------
-- 2. db_analysis.analysis_report：分析报告（ReportController 8 个接口）
-- -------------------------------------------------------------
USE db_analysis;

INSERT INTO analysis_report (id, tenant_id, name, description, datasource_id, dataset_id, config, is_published, create_by, create_time, update_by, update_time, deleted) VALUES
 (901, 1, '区域销售结构分析', '按大区拆解销售额与订单量，定位增长主力区域。', 901, 901,
  '{"dimensions":[{"field":"region"}],"metrics":[{"field":"amount","aggFunction":"SUM","alias":"amount"}],"limit":50}', 1, 'admin', NOW() - INTERVAL 4 HOUR, 'admin', NOW() - INTERVAL 4 HOUR, 0),
 (902, 1, '用户行为漏斗概览', '事件类型分布与平均停留时长。', 901, 902,
  '{"dimensions":[{"field":"event_type"}],"metrics":[{"field":"duration_ms","aggFunction":"AVG","alias":"avg_duration"}]}', 1, 'analyst', NOW() - INTERVAL 1 DAY, 'analyst', NOW() - INTERVAL 1 DAY, 0),
 (903, 1, '订单履约草稿', '草稿状态，用于验证发布/取消发布接口。', 902, 903,
  '{"dimensions":[{"field":"status"}],"metrics":[{"field":"pay_amount","aggFunction":"SUM","alias":"pay_amount"}]}', 0, 'admin', NOW() - INTERVAL 2 DAY, 'admin', NOW() - INTERVAL 2 DAY, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), config = VALUES(config),
  is_published = VALUES(is_published), update_time = VALUES(update_time), deleted = 0;

-- -------------------------------------------------------------
-- 3. db_openapi：应用 / 客户端 / 调用日志 / Webhook（openapi-service 18 个接口）
--    app_secret / secret 均为占位值，仅用于本地联调，切勿用于生产。
-- -------------------------------------------------------------
USE db_openapi;

INSERT INTO openapi_app (id, tenant_id, app_name, app_key, app_secret, permissions, rate_limit, ip_whitelist, status, create_time, update_time) VALUES
 (901, 1, '经营看板嵌入应用', 'dvapp0001', 'dev-secret-screen-embed-0001', 'screen:read,dashboard:read', 600, '127.0.0.1', 1, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),
 (902, 1, '数据导出批处理', 'dvapp0002', 'dev-secret-export-0002', 'dataset:read,export:write', 120, NULL, 1, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY)
ON DUPLICATE KEY UPDATE app_name = VALUES(app_name), permissions = VALUES(permissions), rate_limit = VALUES(rate_limit), status = VALUES(status);

INSERT INTO openapi_client (id, tenant_id, app_name, app_key, app_secret, status, rate_limit, allowed_ips, expire_time, create_time, update_time) VALUES
 (901, 1, '经营看板嵌入应用', 'dvapp0001', 'dev-secret-screen-embed-0001', 'ACTIVE', 600, '127.0.0.1', NOW() + INTERVAL 90 DAY, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),
 (902, 1, '第三方 BI 对接', 'dvapp0003', 'dev-secret-bi-0003', 'DISABLED', 60, NULL, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE app_name = VALUES(app_name), status = VALUES(status), expire_time = VALUES(expire_time);

INSERT INTO openapi_log (id, client_id, app_name, api_path, method, request_params, response_code, execution_time, ip, create_time) VALUES
 (901, 901, '经营看板嵌入应用', '/openapi/screen/901', 'GET', '{}', 200, 34, '127.0.0.1', NOW() - INTERVAL 12 MINUTE),
 (902, 901, '经营看板嵌入应用', '/openapi/screen/list', 'GET', '{"pageNum":1,"pageSize":20}', 200, 51, '127.0.0.1', NOW() - INTERVAL 25 MINUTE),
 (903, 902, '数据导出批处理', '/openapi/dataset/901/preview', 'POST', '{"limit":100}', 403, 12, '127.0.0.1', NOW() - INTERVAL 1 HOUR),
 (904, 902, '数据导出批处理', '/openapi/dataset/list', 'GET', '{"pageNum":1,"pageSize":50}', 200, 44, '127.0.0.1', NOW() - INTERVAL 2 HOUR),
 (905, 901, '经营看板嵌入应用', '/openapi/dashboard/901', 'GET', '{}', 404, 9, '127.0.0.1', NOW() - INTERVAL 6 HOUR),
 (906, 902, '数据导出批处理', '/openapi/screen/902', 'GET', '{}', 200, 28, '127.0.0.1', NOW() - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE api_path = VALUES(api_path), response_code = VALUES(response_code);

INSERT INTO openapi_webhook (id, tenant_id, app_id, event_type, url, secret, status, create_time, update_time) VALUES
 (901, 1, 901, 'screen.published', 'http://127.0.0.1:18080/hook/published', 'dev-hook-secret-0001', 1, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),
 (902, 1, 902, 'etl.task.failed', 'http://127.0.0.1:18080/hook/etl-fail', 'dev-hook-secret-0002', 0, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE event_type = VALUES(event_type), url = VALUES(url), status = VALUES(status);

-- -------------------------------------------------------------
-- 4. db_schedule：定时任务 + 执行日志（schedule-service 11 个接口）
-- -------------------------------------------------------------
USE db_schedule;

INSERT INTO schedule_job (id, tenant_id, job_name, job_group, cron_expression, job_class, job_params, status, description, misfire_policy, create_time, update_time) VALUES
 (901, 1, '销售数据同步', 'DATA_SYNC', '0 0/30 * * * ?', 'com.dataviz.schedule.job.EtlTriggerJob', '{"taskId":901}', 'RUNNING', '每 30 分钟触发一次销售演示数据同步任务。', 'DO_NOTHING', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 30 MINUTE),
 (902, 1, '告警规则巡检', 'MONITOR', '0 0/5 * * * ?', 'com.dataviz.schedule.job.AlertScanJob', '{"batchSize":50}', 'RUNNING', '每 5 分钟扫描一次启用中的告警规则。', 'FIRE_ONCE_NOW', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 MINUTE),
 (903, 1, '审计日志归档', 'MAINTENANCE', '0 0 3 * * ?', 'com.dataviz.schedule.job.AuditArchiveJob', '{"keepDays":90}', 'STOPPED', '每日 03:00 归档 90 天前的审计日志。', 'DO_NOTHING', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE job_name = VALUES(job_name), cron_expression = VALUES(cron_expression), status = VALUES(status), description = VALUES(description);

INSERT INTO schedule_job_log (id, job_id, job_name, start_time, end_time, status, message, create_time) VALUES
 (901, 901, '销售数据同步', NOW() - INTERVAL 30 MINUTE, NOW() - INTERVAL 30 MINUTE + INTERVAL 12 SECOND, 'SUCCESS', '读取 135 行，写入 135 行', NOW() - INTERVAL 30 MINUTE),
 (902, 901, '销售数据同步', NOW() - INTERVAL 60 MINUTE, NOW() - INTERVAL 60 MINUTE + INTERVAL 15 SECOND, 'SUCCESS', '读取 135 行，写入 135 行', NOW() - INTERVAL 60 MINUTE),
 (903, 901, '销售数据同步', NOW() - INTERVAL 90 MINUTE, NOW() - INTERVAL 90 MINUTE + INTERVAL 4 SECOND, 'FAILED', '目标表 demo_sales_daily 写入超时', NOW() - INTERVAL 90 MINUTE),
 (904, 902, '告警规则巡检', NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 5 MINUTE + INTERVAL 2 SECOND, 'SUCCESS', '扫描 5 条规则，触发 3 条', NOW() - INTERVAL 5 MINUTE),
 (905, 902, '告警规则巡检', NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 10 MINUTE + INTERVAL 3 SECOND, 'SUCCESS', '扫描 5 条规则，触发 0 条', NOW() - INTERVAL 10 MINUTE),
 (906, 903, '审计日志归档', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 48 SECOND, 'SUCCESS', '归档 24 条审计日志', NOW() - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE status = VALUES(status), message = VALUES(message);

-- -------------------------------------------------------------
-- 5. db_auth.sys_oauth_client：SSO / OAuth 客户端（auth-service sso 接口）
-- -------------------------------------------------------------
USE db_auth;

INSERT INTO sys_oauth_client (id, client_id, client_secret, client_name, grant_types, redirect_uris, scopes, access_token_ttl, refresh_token_ttl, status, create_time, update_time) VALUES
 (901, 'dataviz-web', 'dev-oauth-secret-web', '设计端 Web', 'authorization_code,refresh_token', 'http://localhost:5174/callback', 'openid,profile', 7200, 604800, 1, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY),
 (902, 'dataviz-admin', 'dev-oauth-secret-admin', '管理端 Web', 'authorization_code,client_credentials', 'http://localhost:3100/admin/callback', 'openid,profile,admin', 3600, 86400, 1, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY)
ON DUPLICATE KEY UPDATE client_name = VALUES(client_name), grant_types = VALUES(grant_types), redirect_uris = VALUES(redirect_uris), status = VALUES(status);

-- -------------------------------------------------------------
-- 6. 校验
-- -------------------------------------------------------------
SELECT 'db_ai.ai_conversation' tbl, COUNT(*) n FROM db_ai.ai_conversation
UNION ALL SELECT 'db_ai.ai_message', COUNT(*) FROM db_ai.ai_message
UNION ALL SELECT 'db_ai.few_shot_example', COUNT(*) FROM db_ai.few_shot_example
UNION ALL SELECT 'db_ai.ai_insight', COUNT(*) FROM db_ai.ai_insight
UNION ALL SELECT 'db_analysis.analysis_report', COUNT(*) FROM db_analysis.analysis_report
UNION ALL SELECT 'db_openapi.openapi_app', COUNT(*) FROM db_openapi.openapi_app
UNION ALL SELECT 'db_openapi.openapi_client', COUNT(*) FROM db_openapi.openapi_client
UNION ALL SELECT 'db_openapi.openapi_log', COUNT(*) FROM db_openapi.openapi_log
UNION ALL SELECT 'db_openapi.openapi_webhook', COUNT(*) FROM db_openapi.openapi_webhook
UNION ALL SELECT 'db_schedule.schedule_job', COUNT(*) FROM db_schedule.schedule_job
UNION ALL SELECT 'db_schedule.schedule_job_log', COUNT(*) FROM db_schedule.schedule_job_log
UNION ALL SELECT 'db_auth.sys_oauth_client', COUNT(*) FROM db_auth.sys_oauth_client;

-- 中文完整性抽查（应全部返回中文，若为 ? 说明执行时连接不是 utf8mb4）
SELECT id, app_name, app_key, status FROM db_openapi.openapi_app WHERE id = 901;
SELECT id, question FROM db_ai.few_shot_example WHERE id = 901;
SELECT id, name, description FROM db_analysis.analysis_report WHERE id = 901;
SELECT id, job_name, description FROM db_schedule.schedule_job WHERE id = 901;
