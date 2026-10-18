SET NAMES utf8mb4;
-- 911 直连组件的等价查询：把 chartConfig 按 SqlBuilder 的规则重写成 SQL 就地跑，
-- 行数>0 且列名含图表点名的字段 = 数据集侧契约成立（渲染还需要登录，这一半只能这样先证）。
-- 注意：这里不带行级权限谓词（探针是超管口径），也不含 limit 之外的收口。
USE db_analysis;
-- d3 每日销售趋势（数据集 921）
SELECT CONCAT('@@ROWS ~|~ d3 ~|~ ', COUNT(*)) FROM (SELECT `stat_date`, SUM(`amount`) AS `sum_amount` FROM (SELECT stat_date, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, SUM(qty) AS qty, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY stat_date) t_source GROUP BY `stat_date` ORDER BY `stat_date` ASC LIMIT 1000) wrapped;
SELECT * FROM (SELECT `stat_date`, SUM(`amount`) AS `sum_amount` FROM (SELECT stat_date, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, SUM(qty) AS qty, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY stat_date) t_source GROUP BY `stat_date` ORDER BY `stat_date` ASC LIMIT 1000) sample LIMIT 3;
-- d4 大区销售与毛利（数据集 912）
SELECT CONCAT('@@ROWS ~|~ d4 ~|~ ', COUNT(*)) FROM (SELECT `region`, SUM(`amount`) AS `sum_amount`, SUM(`profit`) AS `sum_profit` FROM `db_analysis`.`dwd_sales_daily` GROUP BY `region` ORDER BY `sum_amount` DESC LIMIT 1000) wrapped;
SELECT * FROM (SELECT `region`, SUM(`amount`) AS `sum_amount`, SUM(`profit`) AS `sum_profit` FROM `db_analysis`.`dwd_sales_daily` GROUP BY `region` ORDER BY `sum_amount` DESC LIMIT 1000) sample LIMIT 3;
-- d5 会员等级价值（数据集 917）
SELECT CONCAT('@@ROWS ~|~ d5 ~|~ ', COUNT(*)) FROM (SELECT `member_level`, SUM(`amount`) AS `sum_amount` FROM `db_analysis`.`ads_member_value` GROUP BY `member_level` ORDER BY `sum_amount` DESC LIMIT 1000) wrapped;
SELECT * FROM (SELECT `member_level`, SUM(`amount`) AS `sum_amount` FROM `db_analysis`.`ads_member_value` GROUP BY `member_level` ORDER BY `sum_amount` DESC LIMIT 1000) sample LIMIT 3;
-- d6 时段成交分布（数据集 916）
SELECT CONCAT('@@ROWS ~|~ d6 ~|~ ', COUNT(*)) FROM (SELECT `stat_hour`, SUM(`amount`) AS `sum_amount`, SUM(`orders`) AS `sum_orders` FROM `db_analysis`.`ads_hour_heat` GROUP BY `stat_hour` ORDER BY `stat_hour` ASC LIMIT 1000) wrapped;
SELECT * FROM (SELECT `stat_hour`, SUM(`amount`) AS `sum_amount`, SUM(`orders`) AS `sum_orders` FROM `db_analysis`.`ads_hour_heat` GROUP BY `stat_hour` ORDER BY `stat_hour` ASC LIMIT 1000) sample LIMIT 3;
-- d7 渠道×品类毛利矩阵（数据集 920）
SELECT CONCAT('@@ROWS ~|~ d7 ~|~ ', COUNT(*)) FROM (SELECT `channel`, `category`, SUM(`amount`) AS `sum_amount` FROM (SELECT channel, category, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY channel, category) t_source GROUP BY `channel`, `category` LIMIT 1000) wrapped;
SELECT * FROM (SELECT `channel`, `category`, SUM(`amount`) AS `sum_amount` FROM (SELECT channel, category, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY channel, category) t_source GROUP BY `channel`, `category` LIMIT 1000) sample LIMIT 3;
-- d8 渠道销售额占比（数据集 912）
SELECT CONCAT('@@ROWS ~|~ d8 ~|~ ', COUNT(*)) FROM (SELECT `channel`, SUM(`amount`) AS `sum_amount` FROM `db_analysis`.`dwd_sales_daily` GROUP BY `channel` ORDER BY `sum_amount` DESC LIMIT 1000) wrapped;
SELECT * FROM (SELECT `channel`, SUM(`amount`) AS `sum_amount` FROM `db_analysis`.`dwd_sales_daily` GROUP BY `channel` ORDER BY `sum_amount` DESC LIMIT 1000) sample LIMIT 3;
-- d9 每日毛利趋势（数据集 921）
SELECT CONCAT('@@ROWS ~|~ d9 ~|~ ', COUNT(*)) FROM (SELECT `stat_date`, SUM(`profit`) AS `sum_profit` FROM (SELECT stat_date, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, SUM(qty) AS qty, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY stat_date) t_source GROUP BY `stat_date` ORDER BY `stat_date` ASC LIMIT 1000) wrapped;
SELECT * FROM (SELECT `stat_date`, SUM(`profit`) AS `sum_profit` FROM (SELECT stat_date, SUM(amount) AS amount, SUM(profit) AS profit, SUM(order_count) AS order_count, SUM(qty) AS qty, ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate FROM dwd_sales_daily GROUP BY stat_date) t_source GROUP BY `stat_date` ORDER BY `stat_date` ASC LIMIT 1000) sample LIMIT 3;
-- d10 门店销售额排行（数据集 919 跨库SQL）
SELECT CONCAT('@@ROWS ~|~ d10 ~|~ ', COUNT(*)) FROM (SELECT `store_name`, SUM(`amount`) AS `sum_amount` FROM (SELECT s.store_name, s.region, s.city, MAX(s.monthly_target) AS target_amount, SUM(t.amount) AS amount, SUM(t.profit) AS profit, ROUND(SUM(t.amount) / NULLIF(MAX(s.monthly_target), 0) * 100, 2) AS achievement_rate, ROUND(SUM(t.profit) / NULLIF(SUM(t.amount), 0) * 100, 2) AS margin_rate FROM db_model.demo_stores s JOIN db_analysis.dws_store_sales t ON t.store_id = s.store_id GROUP BY s.store_name, s.region, s.city) t_source GROUP BY `store_name` ORDER BY `sum_amount` DESC LIMIT 1000) wrapped;
SELECT * FROM (SELECT `store_name`, SUM(`amount`) AS `sum_amount` FROM (SELECT s.store_name, s.region, s.city, MAX(s.monthly_target) AS target_amount, SUM(t.amount) AS amount, SUM(t.profit) AS profit, ROUND(SUM(t.amount) / NULLIF(MAX(s.monthly_target), 0) * 100, 2) AS achievement_rate, ROUND(SUM(t.profit) / NULLIF(SUM(t.amount), 0) * 100, 2) AS margin_rate FROM db_model.demo_stores s JOIN db_analysis.dws_store_sales t ON t.store_id = s.store_id GROUP BY s.store_name, s.region, s.city) t_source GROUP BY `store_name` ORDER BY `sum_amount` DESC LIMIT 1000) sample LIMIT 3;
-- d11 门店平均达成率（数据集 915 未受限列）
SELECT CONCAT('@@ROWS ~|~ d11 ~|~ ', COUNT(*)) FROM (SELECT AVG(`achievement_rate`) AS `avg_achievement_rate` FROM `db_analysis`.`ads_store_performance` LIMIT 1000) wrapped;
SELECT * FROM (SELECT AVG(`achievement_rate`) AS `avg_achievement_rate` FROM `db_analysis`.`ads_store_performance` LIMIT 1000) sample LIMIT 3;
