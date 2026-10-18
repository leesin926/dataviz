-- BD3：演示数据包的「数据源 + ETL 设计」配置
-- 由 devlog/_ay/bd3-gen-etl.mjs 生成；不要手改本文件，改脚本后重新生成。
-- 前置：已执行 2026-10-18-bd-demo-data.sql（业务库三层数据 + db_analysis 分层表）
-- 回滚：见文件末尾的 rollback 注释块

SET NAMES utf8mb4;

-- ============ 1. 数据源：把话说清楚 ============
UPDATE db_datasource.datasource SET description = '分析库（数据源 901）：承载 ODS/DWD/DWS/ADS 四层演示表，ETL 目标端与所有数据集查询都走它' WHERE id = 901;
UPDATE db_datasource.datasource SET description = '订单业务库（数据源 902）：demo_orders / demo_stores / demo_members 三张源表，ETL 起点' WHERE id = 902;
-- ClickHouse 驱动没打进 datasource-service 的包（classpath 里只有 mysql-connector），
-- 挂着「可用」只会让人反复点测试连接然后怀疑自己。按实际能力置为不可用。
UPDATE db_datasource.datasource SET status = 0, description = '行为日志集群（示例，未启用）：平台当前只内置 MySQL JDBC 驱动，该类型测试连接必然失败；接入需先给 datasource-service 引入 clickhouse-jdbc 并重新打包' WHERE id = 903;
UPDATE db_datasource.datasource SET description = '第三方指标开放接口（示例，未启用）：非 JDBC 类型不参与 SQL 取数，仅作列表形态展示' WHERE id = 904;

-- ============ 2. 旧任务：按当前引擎能力标注，不假装能跑 ============
-- 引擎只实现 append，分组字段只认普通列，非 JDBC 源不能取数 —— 三条旧任务分别踩了这三种写法。
-- CONCAT 追加而不是覆盖：原说明是这批演示数据自带的，不该被我改掉；NOT LIKE 让补丁可重复执行而不重复追加。
-- 停摆与补文案分两条：状态要无条件收口到 STOPPED，说明只在没标注过时追加。
UPDATE db_etl.etl_task SET status = 'STOPPED' WHERE id = 901;
UPDATE db_etl.etl_task SET description = CONCAT(COALESCE(description, ''), ' 【旧任务对照】写入方式 upsert 当前引擎不支持（只实现 append），点执行会明确报错；等价的可执行版见 912。')
  WHERE id = 901 AND description NOT LIKE '%旧任务对照%';
UPDATE db_etl.etl_task SET status = 'STOPPED' WHERE id = 902;
UPDATE db_etl.etl_task SET description = CONCAT(COALESCE(description, ''), ' 【旧任务对照】源端与目标端同表，append 跑一次翻一倍；仅作画布示例，别点执行。')
  WHERE id = 902 AND description NOT LIKE '%旧任务对照%';
UPDATE db_etl.etl_task SET status = 'STOPPED' WHERE id = 903;
UPDATE db_etl.etl_task SET description = CONCAT(COALESCE(description, ''), ' 【旧任务对照】源端 904 是 api 型数据源（非 JDBC，已停用），且 overwrite 未实现；等 ClickHouse/API 驱动接入后再启用。')
  WHERE id = 903 AND description NOT LIKE '%旧任务对照%';

-- ============ 3. 可执行的演示 DAG（911~918） ============
-- 状态一律 STOPPED：调度字段只是设计意图，etl-service 里没有 @Scheduled 自动触发，
-- 跑一次靠界面上的「执行」，跑完 last_run_status 由引擎自己写。
-- 历史（etl_task_log/instance）这里刻意不预填：这 8 条任务还没被引擎跑过，
-- 造一条成功日志等于把「全流程打通」写成假账。

-- 911 ODS 订单明细入仓：demo_orders -> ods_order_detail
DELETE FROM db_etl.etl_task WHERE id = 911;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (911, 1, 'ODS 订单明细入仓', '业务库有效订单关联门店/会员后落到分析库 ODS。取数用自定义 SQL（一次 JOIN 搞定维度退化），有效口径 = 状态 PAID/SHIPPED。目标表主键 order_id：重跑前要先清空 ods_order_detail，否则会报主键冲突而不是悄悄翻倍。', 902, 901,
  'STOPPED', '30 1 * * *', 'demo_orders', 'ods_order_detail', '{"nodes":[{"id":"i1","type":"input","name":"订单明细(业务库)","x":60,"y":140,"config":{"datasourceId":902,"sql":"SELECT o.order_id AS order_id, DATE(o.created_at) AS stat_date, HOUR(o.created_at) AS stat_hour,        o.region AS region, s.city AS city, o.store_id AS store_id, s.store_name AS store_name,        o.channel AS channel, o.category AS category, o.user_id AS member_id,        IFNULL(m.level, ''散客'') AS member_level, o.pay_amount AS pay_amount, o.profit AS profit,        o.qty AS qty, o.status AS order_status, o.created_at AS created_at   FROM demo_orders o   JOIN demo_stores s ON s.store_id = o.store_id   LEFT JOIN demo_members m ON m.member_id = o.user_id  WHERE o.status IN (''PAID'', ''SHIPPED'')"}},{"id":"o2","type":"output","name":"写入 ods_order_detail","x":380,"y":140,"config":{"datasourceId":901,"tableName":"ods_order_detail","writeMode":"append","columns":["order_id","stat_date","stat_hour","region","city","store_id","store_name","channel","category","member_id","member_level","pay_amount","profit","qty","order_status","created_at"]}}],"edges":[{"id":"e1","source":"i1","target":"o2"}]}', 0);

-- 912 DWD 日销售汇总：ods_order_detail -> dwd_sales_daily
DELETE FROM db_etl.etl_task WHERE id = 912;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (912, 1, 'DWD 日销售汇总', 'ODS 明细按 日期×大区×品类×渠道 汇总。分组字段只能是普通列，所以 stat_date 在 ODS 阶段就投影好了；这段 DAG 与装载 dwd_sales_daily 的 INSERT..SELECT 语义一致，点执行可复算同一批数。', 901, 901,
  'STOPPED', '0 2 * * *', 'ods_order_detail', 'dwd_sales_daily', '{"nodes":[{"id":"i3","type":"input","name":"ODS 明细","x":60,"y":140,"config":{"datasourceId":901,"tableName":"ods_order_detail","columns":["stat_date","region","category","channel","pay_amount","profit","order_id","qty"]}},{"id":"a4","type":"aggregate","name":"按日期/大区/品类/渠道汇总","x":300,"y":140,"config":{"groupBy":["stat_date","region","category","channel"],"aggregations":[{"field":"pay_amount","function":"sum","alias":"amount"},{"field":"profit","function":"sum","alias":"profit"},{"field":"order_id","function":"count","alias":"order_count"},{"field":"qty","function":"sum","alias":"qty"}]}},{"id":"o5","type":"output","name":"写入 dwd_sales_daily","x":560,"y":140,"config":{"datasourceId":901,"tableName":"dwd_sales_daily","writeMode":"append","columns":["stat_date","region","category","channel","amount","profit","order_count","qty"]}}],"edges":[{"id":"e1","source":"i3","target":"a4"},{"id":"e2","source":"a4","target":"o5"}]}', 0);

-- 913 DWS 门店销售汇总：ods_order_detail -> dws_store_sales
DELETE FROM db_etl.etl_task WHERE id = 913;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (913, 1, 'DWS 门店销售汇总', 'ODS 明细按门店汇总，产出金额/毛利/单量/客单，供门店排行与绩效层复用。', 901, 901,
  'STOPPED', '10 2 * * *', 'ods_order_detail', 'dws_store_sales', '{"nodes":[{"id":"i6","type":"input","name":"ODS 明细","x":60,"y":140,"config":{"datasourceId":901,"tableName":"ods_order_detail","columns":["store_id","store_name","region","city","pay_amount","profit","order_id"]}},{"id":"a7","type":"aggregate","name":"按门店汇总","x":300,"y":140,"config":{"groupBy":["store_id","store_name","region","city"],"aggregations":[{"field":"pay_amount","function":"sum","alias":"amount"},{"field":"profit","function":"sum","alias":"profit"},{"field":"order_id","function":"count","alias":"order_count"},{"field":"pay_amount","function":"avg","alias":"avg_pay_amount"}]}},{"id":"o8","type":"output","name":"写入 dws_store_sales","x":560,"y":140,"config":{"datasourceId":901,"tableName":"dws_store_sales","writeMode":"append","columns":["store_id","store_name","region","city","amount","profit","order_count","avg_pay_amount"]}}],"edges":[{"id":"e1","source":"i6","target":"a7"},{"id":"e2","source":"a7","target":"o8"}]}', 0);

-- 914 DWS 会员等级汇总：ods_order_detail -> dws_member_level
DELETE FROM db_etl.etl_task WHERE id = 914;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (914, 1, 'DWS 会员等级汇总', '散客（member_id=0）先用过滤节点剔掉，再按会员等级汇总 —— 演示 filter→aggregate 两段串联，过滤条件写在 SQL 里还是节点里效果一样，放节点里是为了在画布上看出口径。', 901, 901,
  'STOPPED', '20 2 * * *', 'ods_order_detail', 'dws_member_level', '{"nodes":[{"id":"i9","type":"input","name":"ODS 明细","x":60,"y":140,"config":{"datasourceId":901,"tableName":"ods_order_detail","columns":["member_id","member_level","pay_amount","order_id"]}},{"id":"f10","type":"filter","name":"剔除散客","x":300,"y":140,"config":{"filters":[{"field":"member_id","operator":"gt","value":0}]}},{"id":"a11","type":"aggregate","name":"按会员等级汇总","x":540,"y":140,"config":{"groupBy":["member_level"],"aggregations":[{"field":"pay_amount","function":"sum","alias":"amount"},{"field":"order_id","function":"count","alias":"order_count"},{"field":"pay_amount","function":"avg","alias":"avg_pay_amount"}]}},{"id":"o12","type":"output","name":"写入 dws_member_level","x":780,"y":140,"config":{"datasourceId":901,"tableName":"dws_member_level","writeMode":"append","columns":["member_level","amount","order_count","avg_pay_amount"]}}],"edges":[{"id":"e1","source":"i9","target":"f10"},{"id":"e2","source":"f10","target":"a11"},{"id":"e3","source":"a11","target":"o12"}]}', 0);

-- 915 ADS 门店绩效（关联月度目标）：dws_store_sales -> ads_store_performance
DELETE FROM db_etl.etl_task WHERE id = 915;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (915, 1, 'ADS 门店绩效（关联月度目标）', '门店汇总要关联业务库的月度目标才能算达成率/毛利率，跨库 JOIN 用自定义 SQL 节点最省事；达成率是现算的（amount/target_amount），不是写进演示数据的死数。', 901, 901,
  'STOPPED', '40 2 * * *', 'dws_store_sales', 'ads_store_performance', '{"nodes":[{"id":"i13","type":"input","name":"门店汇总×月度目标","x":60,"y":140,"config":{"datasourceId":901,"sql":"SELECT d.store_id AS store_id, d.store_name AS store_name, d.region AS region, d.city AS city,        d.amount AS amount, d.profit AS profit, d.order_count AS order_count,        d.avg_pay_amount AS avg_pay_amount, s.monthly_target AS target_amount,        ROUND(d.amount / NULLIF(s.monthly_target, 0) * 100, 2) AS achievement_rate,        ROUND(d.profit / NULLIF(d.amount, 0) * 100, 2) AS margin_rate   FROM dws_store_sales d   JOIN db_model.demo_stores s ON s.store_id = d.store_id"}},{"id":"o14","type":"output","name":"写入 ads_store_performance","x":400,"y":140,"config":{"datasourceId":901,"tableName":"ads_store_performance","writeMode":"append","columns":["store_id","store_name","region","city","amount","profit","order_count","avg_pay_amount","target_amount","achievement_rate","margin_rate"]}}],"edges":[{"id":"e1","source":"i13","target":"o14"}]}', 0);

-- 916 ADS 时段热力：ods_order_detail -> ads_hour_heat
DELETE FROM db_etl.etl_task WHERE id = 916;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (916, 1, 'ADS 时段热力', '按下单小时汇总，供大屏热力条/时段折线用。行数少（一天 12 个时段），聚合节点直接算完。', 901, 901,
  'STOPPED', '50 2 * * *', 'ods_order_detail', 'ads_hour_heat', '{"nodes":[{"id":"i15","type":"input","name":"ODS 明细","x":60,"y":140,"config":{"datasourceId":901,"tableName":"ods_order_detail","columns":["stat_hour","order_id","pay_amount"]}},{"id":"a16","type":"aggregate","name":"按小时汇总","x":300,"y":140,"config":{"groupBy":["stat_hour"],"aggregations":[{"field":"order_id","function":"count","alias":"orders"},{"field":"pay_amount","function":"sum","alias":"amount"},{"field":"pay_amount","function":"avg","alias":"avg_amount"}]}},{"id":"o17","type":"output","name":"写入 ads_hour_heat","x":560,"y":140,"config":{"datasourceId":901,"tableName":"ads_hour_heat","writeMode":"append","columns":["stat_hour","orders","amount","avg_amount"]}}],"edges":[{"id":"e1","source":"i15","target":"a16"},{"id":"e2","source":"a16","target":"o17"}]}', 0);

-- 917 ADS 会员价值分层：ods_order_detail -> ads_member_value
DELETE FROM db_etl.etl_task WHERE id = 917;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (917, 1, 'ADS 会员价值分层', '复购率要按会员去重再判 ≥2 单，聚合算子没有 distinct 能力，所以这段用自定义 SQL 一次算完，把「算子能做的用画布、算子做不了的用 SQL」这条实际用法留在演示里。', 901, 901,
  'STOPPED', '0 3 * * *', 'ods_order_detail', 'ads_member_value', '{"nodes":[{"id":"i18","type":"input","name":"会员分层去重","x":60,"y":140,"config":{"datasourceId":901,"sql":"SELECT t.member_level AS member_level, t.members AS members, t.amount AS amount,        ROUND(t.amount / NULLIF(t.members, 0), 2) AS avg_amount, t.order_count AS order_count,        ROUND(t.repeat_members / NULLIF(t.members, 0) * 100, 2) AS repurchase_rate   FROM (SELECT o.member_level AS member_level, COUNT(DISTINCT o.member_id) AS members,                COUNT(DISTINCT CASE WHEN o.cnt >= 2 THEN o.member_id END) AS repeat_members,                SUM(o.amount) AS amount, SUM(o.cnt) AS order_count           FROM (SELECT member_level, member_id, SUM(pay_amount) AS amount, COUNT(order_id) AS cnt                    FROM ods_order_detail WHERE member_id > 0                   GROUP BY member_level, member_id) o          GROUP BY o.member_level) t"}},{"id":"o19","type":"output","name":"写入 ads_member_value","x":400,"y":140,"config":{"datasourceId":901,"tableName":"ads_member_value","writeMode":"append","columns":["member_level","members","amount","avg_amount","order_count","repurchase_rate"]}}],"edges":[{"id":"e1","source":"i18","target":"o19"}]}', 0);

-- 918 ADS 实时成交流水：ods_order_detail -> ads_realtime_feed
DELETE FROM db_etl.etl_task WHERE id = 918;
INSERT INTO db_etl.etl_task
  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,
   status, schedule_cron, source_table, target_table, transform_config, deleted)
VALUES (918, 1, 'ADS 实时成交流水', '大屏滚动列表用的最近 40 笔。取数节点带 ORDER BY/LIMIT，目标表主键 feed_id=order_id，所以这也是一条「全量刷新」任务：重跑先清空 ads_realtime_feed。', 901, 901,
  'STOPPED', '*/10 * * * *', 'ods_order_detail', 'ads_realtime_feed', '{"nodes":[{"id":"i20","type":"input","name":"最近 40 笔成交","x":60,"y":140,"config":{"datasourceId":901,"sql":"SELECT order_id AS feed_id, created_at AS feed_time, store_name AS store_name, city AS city,        category AS category, channel AS channel, pay_amount AS amount   FROM ods_order_detail  ORDER BY created_at DESC  LIMIT 40"}},{"id":"o21","type":"output","name":"写入 ads_realtime_feed","x":400,"y":140,"config":{"datasourceId":901,"tableName":"ads_realtime_feed","writeMode":"append","columns":["feed_id","feed_time","store_name","city","category","channel","amount"]}}],"edges":[{"id":"e1","source":"i20","target":"o21"}]}', 0);

-- ============ 4. 自检 ============
SELECT '任务总数(演示 8 条 + 旧 3 条)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task WHERE id BETWEEN 901 AND 918 AND deleted = 0;
SELECT 'transform_config 非法 JSON 条数(必须 0)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task WHERE id BETWEEN 911 AND 918 AND JSON_VALID(transform_config) = 0;
SELECT '写入方式非 append 的新任务(必须 0)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task
  WHERE id BETWEEN 911 AND 918 AND transform_config LIKE '%overwrite%' AND transform_config NOT LIKE '%append%';
-- 分组字段是否只认普通列这类判据要拆 JSON，MySQL 5.7 没有 JSON_TABLE，交给 devlog/_ay/bd3-contract.cjs 逐节点核。
SELECT '每条新任务都有源端与目标节点' AS check_name,
  CONCAT(SUM(JSON_EXTRACT(transform_config, '$.nodes[0].type') = 'input'
              AND JSON_LENGTH(JSON_EXTRACT(transform_config, '$.nodes')) >= 2), '/', COUNT(*)) AS value
  FROM db_etl.etl_task WHERE id BETWEEN 911 AND 918;
SELECT '引用的数据源存在且可用' AS check_name,
  CONCAT(SUM(d.status = 1), '/', COUNT(*)) AS value
  FROM db_etl.etl_task t
  JOIN db_datasource.datasource d ON d.id IN (t.source_datasource_id, t.target_datasource_id) AND d.deleted = 0
 WHERE t.id BETWEEN 911 AND 918;
SELECT '新任务的历史条数(必须 0：还没跑过就不该有记录)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task_log WHERE task_id BETWEEN 911 AND 918;

-- 回滚（需要时手工执行）：
--   DELETE FROM db_etl.etl_task WHERE id BETWEEN 911 AND 918;
--   UPDATE db_datasource.datasource SET status = 1 WHERE id = 903;
