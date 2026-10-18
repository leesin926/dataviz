-- 由 bd3-gen-etl.mjs 生成：逐节点验证源端 SQL 的返回列（LIMIT 0，不落数据）
SET NAMES utf8mb4;

USE db_model;
SELECT '@@PROBE 911 i1' AS marker;
SELECT * FROM (SELECT o.order_id AS order_id, DATE(o.created_at) AS stat_date, HOUR(o.created_at) AS stat_hour, o.region AS region, s.city AS city, o.store_id AS store_id, s.store_name AS store_name, o.channel AS channel, o.category AS category, o.user_id AS member_id, IFNULL(m.level, '散客') AS member_level, o.pay_amount AS pay_amount, o.profit AS profit, o.qty AS qty, o.status AS order_status, o.created_at AS created_at FROM demo_orders o JOIN demo_stores s ON s.store_id = o.store_id LEFT JOIN demo_members m ON m.member_id = o.user_id WHERE o.status IN ('PAID', 'SHIPPED')) probe_node LIMIT 1;
USE db_analysis;
SELECT '@@PROBE 912 i3' AS marker;
SELECT * FROM (SELECT stat_date, region, category, channel, pay_amount, profit, order_id, qty FROM ods_order_detail) probe_node LIMIT 1;
SELECT '@@PROBE 913 i6' AS marker;
SELECT * FROM (SELECT store_id, store_name, region, city, pay_amount, profit, order_id FROM ods_order_detail) probe_node LIMIT 1;
SELECT '@@PROBE 914 i9' AS marker;
SELECT * FROM (SELECT member_id, member_level, pay_amount, order_id FROM ods_order_detail) probe_node LIMIT 1;
SELECT '@@PROBE 915 i13' AS marker;
SELECT * FROM (SELECT d.store_id AS store_id, d.store_name AS store_name, d.region AS region, d.city AS city, d.amount AS amount, d.profit AS profit, d.order_count AS order_count, d.avg_pay_amount AS avg_pay_amount, s.monthly_target AS target_amount, ROUND(d.amount / NULLIF(s.monthly_target, 0) * 100, 2) AS achievement_rate, ROUND(d.profit / NULLIF(d.amount, 0) * 100, 2) AS margin_rate FROM dws_store_sales d JOIN db_model.demo_stores s ON s.store_id = d.store_id) probe_node LIMIT 1;
SELECT '@@PROBE 916 i15' AS marker;
SELECT * FROM (SELECT stat_hour, order_id, pay_amount FROM ods_order_detail) probe_node LIMIT 1;
SELECT '@@PROBE 917 i18' AS marker;
SELECT * FROM (SELECT t.member_level AS member_level, t.members AS members, t.amount AS amount, ROUND(t.amount / NULLIF(t.members, 0), 2) AS avg_amount, t.order_count AS order_count, ROUND(t.repeat_members / NULLIF(t.members, 0) * 100, 2) AS repurchase_rate FROM (SELECT o.member_level AS member_level, COUNT(DISTINCT o.member_id) AS members, COUNT(DISTINCT CASE WHEN o.cnt >= 2 THEN o.member_id END) AS repeat_members, SUM(o.amount) AS amount, SUM(o.cnt) AS order_count FROM (SELECT member_level, member_id, SUM(pay_amount) AS amount, COUNT(order_id) AS cnt FROM ods_order_detail WHERE member_id > 0 GROUP BY member_level, member_id) o GROUP BY o.member_level) t) probe_node LIMIT 1;
SELECT '@@PROBE 918 i20' AS marker;
SELECT * FROM (SELECT order_id AS feed_id, created_at AS feed_time, store_name AS store_name, city AS city, category AS category, channel AS channel, pay_amount AS amount FROM ods_order_detail ORDER BY created_at DESC LIMIT 40) probe_node LIMIT 1;
SELECT '@@PROBE done' AS marker;
