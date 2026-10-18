SET NAMES utf8mb4;
USE db_analysis;
-- 行数探针：报告只能挂"表本身就是那个粒度"且 ≤ 1000 行的来源，这里给判据
SELECT CONCAT('@@CNT ~|~ 911 ~|~ ', COUNT(*)) FROM `ods_order_detail`;
SELECT CONCAT('@@CNT ~|~ 912 ~|~ ', COUNT(*)) FROM `dwd_sales_daily`;
SELECT CONCAT('@@CNT ~|~ 913 ~|~ ', COUNT(*)) FROM `dws_store_sales`;
SELECT CONCAT('@@CNT ~|~ 914 ~|~ ', COUNT(*)) FROM `dws_member_level`;
SELECT CONCAT('@@CNT ~|~ 915 ~|~ ', COUNT(*)) FROM `ads_store_performance`;
SELECT CONCAT('@@CNT ~|~ 916 ~|~ ', COUNT(*)) FROM `ads_hour_heat`;
SELECT CONCAT('@@CNT ~|~ 917 ~|~ ', COUNT(*)) FROM `ads_member_value`;
SELECT CONCAT('@@CNT ~|~ 918 ~|~ ', COUNT(*)) FROM `ads_realtime_feed`;
SELECT CONCAT('@@CNT ~|~ 919 ~|~ ', COUNT(*)) FROM (SELECT s.store_name, s.region, s.city, MAX(s.monthly_target) AS target_amount, SUM(t.amount) AS amount, SUM(t.profit) AS profit, ROUND(SUM(t.amount) / NULLIF(MAX(s.monthly_target), 0) * 100, 2) AS achievement_rate, ROUND(SUM(t.profit) / NULLIF(SUM(t.amount), 0) * 100, 2) AS margin_rate FROM db_model.demo_stores s JOIN db_analysis.dws_store_sales t ON t.store_id = s.store_id GROUP BY s.store_name, s.region, s.city) t_source;
SELECT CONCAT('@@CNT ~|~ 920 ~|~ ', COUNT(*)) FROM (SELECT channel, category, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY channel, category) t_source;
SELECT CONCAT('@@CNT ~|~ 921 ~|~ ', COUNT(*)) FROM (SELECT stat_date, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, SUM(qty) AS qty, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY stat_date) t_source;
