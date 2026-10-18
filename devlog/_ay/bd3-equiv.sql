-- BD3 等价性自检：每条 DAG 的转换语义 vs 2026-10-18-bd-demo-data.sql 实际装载出来的表
-- 只读，不落数据。DAG 里的源端 SQL 走各自数据源的默认库，这里一律写库名前缀（同一台 MySQL、同一个账号，结果一致）。
SET NAMES utf8mb4;

SELECT 'ods' AS tag,
       (SELECT COUNT(*) FROM (
          SELECT o.order_id, DATE(o.created_at) AS stat_date, o.region, s.city, o.store_id, s.store_name,
                 o.channel, o.category, o.user_id AS member_id, IFNULL(m.level, '散客') AS member_level,
                 o.pay_amount, o.profit, o.qty, o.status AS order_status, o.created_at
            FROM db_model.demo_orders o
            JOIN db_model.demo_stores s ON s.store_id = o.store_id
            LEFT JOIN db_model.demo_members m ON m.member_id = o.user_id
           WHERE o.status IN ('PAID', 'SHIPPED')) dag) AS dag_rows,
       (SELECT COUNT(*) FROM db_analysis.ods_order_detail) AS tbl_rows,
       (SELECT ROUND(SUM(pay_amount), 2) FROM (
          SELECT o.pay_amount
            FROM db_model.demo_orders o
            JOIN db_model.demo_stores s ON s.store_id = o.store_id
           WHERE o.status IN ('PAID', 'SHIPPED')) dag) AS dag_amount,
       (SELECT ROUND(SUM(pay_amount), 2) FROM db_analysis.ods_order_detail) AS tbl_amount
UNION ALL
SELECT 'dwd',
       (SELECT COUNT(*) FROM (SELECT stat_date, region, category, channel FROM db_analysis.ods_order_detail
                               GROUP BY stat_date, region, category, channel) g),
       (SELECT COUNT(*) FROM db_analysis.dwd_sales_daily),
       (SELECT ROUND(SUM(amount), 2) FROM (SELECT SUM(pay_amount) AS amount FROM db_analysis.ods_order_detail
                               GROUP BY stat_date, region, category, channel) g),
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.dwd_sales_daily)
UNION ALL
SELECT 'dws_store',
       (SELECT COUNT(*) FROM (SELECT store_id FROM db_analysis.ods_order_detail
                               GROUP BY store_id, store_name, region, city) g),
       (SELECT COUNT(*) FROM db_analysis.dws_store_sales),
       (SELECT ROUND(SUM(amount), 2) FROM (SELECT SUM(pay_amount) AS amount FROM db_analysis.ods_order_detail
                               GROUP BY store_id, store_name, region, city) g),
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.dws_store_sales)
UNION ALL
SELECT 'dws_member',
       (SELECT COUNT(*) FROM (SELECT member_level FROM db_analysis.ods_order_detail WHERE member_id > 0
                               GROUP BY member_level) g),
       (SELECT COUNT(*) FROM db_analysis.dws_member_level),
       (SELECT ROUND(SUM(amount), 2) FROM (SELECT SUM(pay_amount) AS amount FROM db_analysis.ods_order_detail
                               WHERE member_id > 0 GROUP BY member_level) g),
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.dws_member_level)
UNION ALL
SELECT 'ads_store',
       (SELECT COUNT(*) FROM db_analysis.dws_store_sales d JOIN db_model.demo_stores s ON s.store_id = d.store_id),
       (SELECT COUNT(*) FROM db_analysis.ads_store_performance),
       (SELECT ROUND(SUM(d.amount), 2) FROM db_analysis.dws_store_sales d JOIN db_model.demo_stores s ON s.store_id = d.store_id),
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.ads_store_performance)
UNION ALL
SELECT 'ads_hour',
       (SELECT COUNT(*) FROM (SELECT stat_hour FROM db_analysis.ods_order_detail GROUP BY stat_hour) g),
       (SELECT COUNT(*) FROM db_analysis.ads_hour_heat),
       (SELECT ROUND(SUM(amount), 2) FROM (SELECT SUM(pay_amount) AS amount FROM db_analysis.ods_order_detail
                               GROUP BY stat_hour) g),
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.ads_hour_heat)
UNION ALL
SELECT 'ads_member',
       (SELECT COUNT(*) FROM (SELECT member_level FROM db_analysis.ods_order_detail WHERE member_id > 0
                               GROUP BY member_level) g),
       (SELECT COUNT(*) FROM db_analysis.ads_member_value),
       (SELECT ROUND(SUM(amount), 2) FROM (SELECT SUM(pay_amount) AS amount FROM db_analysis.ods_order_detail
                               WHERE member_id > 0 GROUP BY member_level) g),
       -- 会员价值层的 amount 是「会员消费总额」，口径与 dws_member_level 一致（散客已被剔掉）
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.ads_member_value)
UNION ALL
SELECT 'ads_feed',
       (SELECT COUNT(*) FROM (SELECT order_id FROM db_analysis.ods_order_detail
                               ORDER BY created_at DESC LIMIT 40) f),
       (SELECT COUNT(*) FROM db_analysis.ads_realtime_feed),
       (SELECT ROUND(SUM(amount), 2) FROM (SELECT pay_amount AS amount FROM db_analysis.ods_order_detail
                               ORDER BY created_at DESC LIMIT 40) f),
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.ads_realtime_feed);
