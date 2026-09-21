-- ============================================================================
-- 2026-09-22  让"告警真取数 + 真通知"在本地能真跑起来（阶段 AA5）
-- 依赖：阶段 AA 的代码已生效 ⇒ datasource-service(8083) 与 alert-service(8090) 需已重启
-- 执行方式：docker exec -i dataviz-mysql mysql -uroot -proot123456 \
--             --default-character-set=utf8mb4 < deploy/sql/patch/2026-09-22-alert-live-demo.sql
-- 幂等：全部按主键 UPDATE / INSERT ... ON DUPLICATE KEY UPDATE，可重复执行
-- 回退：见本文件末尾「回退」段（只回退本次新增/改动的行，不动 218 行演示事件）
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1) 数据源可达性修正
--    现状：901/902 的 config.host = 172.18.0.1（容器视角的宿主网关），而 datasource-service
--    跑在 Windows 宿主上 ⇒ 该地址不可达；且 config 里根本没有 password 字段
--    （DataSourceConnectionFactory.createDataSource 只读 config.username / config.password，
--      不看表的 username/password 两列 ⇒ 必须写进 config JSON 才有效）
-- ----------------------------------------------------------------------------
UPDATE db_datasource.datasource
SET config = '{"host":"localhost","port":3306,"database":"db_analysis","username":"root","password":"root123456","maxPoolSize":4,"minIdle":1,"queryTimeoutSeconds":30}',
    host = 'localhost', port = 3306, database_name = 'db_analysis',
    username = 'root', password = 'root123456',
    update_time = NOW()
WHERE id = 901;

UPDATE db_datasource.datasource
SET config = '{"host":"localhost","port":3306,"database":"db_model","username":"root","password":"root123456","maxPoolSize":4,"minIdle":1}',
    host = 'localhost', port = 3306, database_name = 'db_model',
    username = 'root', password = 'root123456',
    update_time = NOW()
WHERE id = 902;

-- ----------------------------------------------------------------------------
-- 2) 停用 5 条"裸指标名"演示规则（不删行，页面展示不受影响）
--    原因：alert_rule.metric_expression 现按 D43 解释为"返回单值的只读 SQL"，
--    而这 5 条存的是 cpu_usage_percent / http_5xx_ratio / sum(amount) 一类指标名；
--    其中 903=DERIVATIVE、904=COMPOSITE 更是 AlertEvaluator 显式拒绝的类型。
--    留着 enabled=1 ⇒ 定时扫描每分钟打一条 error（ClickHouse 903 还会真的去连）
-- ----------------------------------------------------------------------------
UPDATE db_alert.alert_rule
SET enabled = 0, update_time = NOW()
WHERE id BETWEEN 901 AND 905;

-- ----------------------------------------------------------------------------
-- 3) 停用钉钉渠道（防第三方副作用）
--    902 的 access_token 是假串，但 oapi.dingtalk.com 是**真域名** ⇒ 一旦拨开 alert 开关
--    就会每分钟向第三方发一次 HTTP。要看"errcode 非 0 判失败"的路径时手动改回 enabled=1
-- ----------------------------------------------------------------------------
UPDATE db_alert.notify_channel
SET enabled = 0, update_time = NOW()
WHERE id = 902 AND type = 'DINGTALK';

-- ----------------------------------------------------------------------------
-- 4) 新增本地 webhook 收件渠道（零外部副作用的派发证据口）
--    需先起一个收件口：node deploy/sql/patch/hooksink.mjs  （监听 127.0.0.1:18099）
--    没起也不影响验证：WebhookNotifier 会记 FAILED + "无法访问" 到 alert_notify_log
-- ----------------------------------------------------------------------------
INSERT INTO db_alert.notify_channel (id, name, type, config, enabled, tenant_id, create_time, update_time)
VALUES (911, '本地收件口（测试）', 'WEBHOOK', '{"url":"http://127.0.0.1:18099/hook"}', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE config = VALUES(config), enabled = 1, update_time = NOW();

-- ----------------------------------------------------------------------------
-- 5) 三条真 SQL 规则（对 db_analysis 的 3 个 demo_* 视图；数据源 901 已指到该库）
--    实测基数：demo_orders 125 行 / demo_user_behavior 500 行 / SUM(demo_sales_daily.amount)=3956715.00
--
--    911 立即触发（duration=0）        → 期望 alert_event +1，notify_log：WEBHOOK SUCCESS、EMAIL FAILED
--    912 边界值 + 持续时长（duration=120）→ 前两轮只记"越界但未达持续时长"，第三轮才建事件
--    913 恒不触发（阈值抬高）           → 期望无事件，且 Redis alert:breach:913 被清掉
-- ----------------------------------------------------------------------------
INSERT INTO db_alert.alert_rule
    (id, name, description, type, datasource_id, metric_expression, `condition`, threshold, duration,
     severity, notify_channels, enabled, tenant_id, create_by, create_time, update_time, deleted)
VALUES
    (911, '演示-订单总量超阈值', '真取数试跑：立即触发（AA 验收）', 'THRESHOLD', 901,
     'SELECT COUNT(*) FROM demo_orders', 'GT', 100.0000, 0,
     'WARNING', '["WEBHOOK","EMAIL"]', 1, 1, NULL, NOW(), NOW(), 0),
    (912, '演示-埋点行数边界+持续时长', '真取数试跑：COUNT=500 命中 LTE 500，需持续 120s（AA 验收）', 'THRESHOLD', 901,
     'SELECT COUNT(*) FROM demo_user_behavior', 'LTE', 500.0000, 120,
     'INFO', '["WEBHOOK"]', 1, 1, NULL, NOW(), NOW(), 0),
    (913, '演示-销售额未达高位', '真取数试跑：恒不触发，用于验证越界键被清除（AA 验收）', 'THRESHOLD', 901,
     'SELECT ROUND(SUM(amount),2) FROM demo_sales_daily', 'GT', 99999999.0000, 0,
     'CRITICAL', '["WEBHOOK"]', 1, 1, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    metric_expression = VALUES(metric_expression),
    `condition` = VALUES(`condition`),
    threshold = VALUES(threshold),
    duration = VALUES(duration),
    notify_channels = VALUES(notify_channels),
    enabled = 1,
    update_time = NOW();

-- ----------------------------------------------------------------------------
-- 6) 抑制逻辑的前置清理：把 901~905 上那 214 行 PENDING 演示事件改成 ACKNOWLEDGED
--    原因：checkRule 里"同规则仍有 PENDING 事件 ⇒ 抑制新告警"（D43），
--    若不清，新链路真的判定命中也**永远不会**建新事件，就会被误读成"代码没生效"。
--    只改 status，一行不删（218 行演示数据继续给告警列表页用）
-- ----------------------------------------------------------------------------
UPDATE db_alert.alert_event
SET status = 'ACKNOWLEDGED', resolved_at = NOW()
WHERE rule_id BETWEEN 901 AND 905 AND status = 'PENDING';

-- ----------------------------------------------------------------------------
-- 校验（执行后逐条看）
-- ----------------------------------------------------------------------------
SELECT '1-数据源可达' AS chk, id, name, host, database_name,
       LOCATE('"password"', config) > 0 AS cfg_has_pwd
FROM db_datasource.datasource WHERE id IN (901, 902);

SELECT '2-规则调度状态' AS chk, id, name, type, datasource_id, `condition`, threshold, duration, enabled
FROM db_alert.alert_rule ORDER BY id;

SELECT '3-渠道' AS chk, id, name, type, enabled FROM db_alert.notify_channel ORDER BY id;

SELECT '4-抑制前置（应为 0）' AS chk, COUNT(*) AS pending_on_demo_rules
FROM db_alert.alert_event WHERE rule_id BETWEEN 901 AND 905 AND status = 'PENDING';

-- ----------------------------------------------------------------------------
-- 回退（需要时手工执行；不影响 init 脚本与第一批/第二批测试数据）
-- ----------------------------------------------------------------------------
-- UPDATE db_alert.alert_rule SET enabled = 1, update_time = NOW() WHERE id BETWEEN 901 AND 905;
-- UPDATE db_alert.notify_channel SET enabled = 1, update_time = NOW() WHERE id = 902;
-- DELETE FROM db_alert.alert_rule WHERE id IN (911, 912, 913);
-- DELETE FROM db_alert.notify_channel WHERE id = 911;
-- DELETE FROM db_alert.alert_event WHERE rule_id IN (911, 912, 913);
-- DELETE FROM db_alert.alert_notify_log WHERE event_id IN (SELECT id FROM db_alert.alert_event WHERE rule_id IN (911,912,913));
-- UPDATE db_datasource.datasource SET config = '{"host":"172.18.0.1","port":3306,"database":"db_analysis","username":"root","maxPoolSize":10,"minIdle":2,"queryTimeoutSeconds":30}' WHERE id = 901;
-- UPDATE db_datasource.datasource SET config = '{"host":"172.18.0.1","port":3306,"database":"db_model","username":"root","maxPoolSize":8,"minIdle":1}' WHERE id = 902;
-- ⚠️ 第 6 步的 status 改成了 ACKNOWLEDGED 且无原值备份（演示事件本就是随机造的，无业务含义）；
--    若要恢复"列表页一片待处理"的观感：UPDATE db_alert.alert_event SET status='PENDING' WHERE rule_id BETWEEN 901 AND 905 AND resolved_at IS NOT NULL;
