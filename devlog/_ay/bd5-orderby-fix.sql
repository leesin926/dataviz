SET NAMES utf8mb4;
USE db_analysis;
-- 修复后 SqlBuilder 会拼出的三条 SQL（原先是 ORDER BY `amount` DESC，在 only_full_group_by 下报 1140）
-- 逐条跑：行数与 bd5-query.out 的 6 / 4 / 21 对齐即证明修法成立
SELECT CONCAT('@@D4 ~|~ ', COUNT(*)) FROM (
  SELECT `region`, SUM(`amount`) AS `sum_amount`, SUM(`profit`) AS `sum_profit`
    FROM `dwd_sales_daily` GROUP BY `region` ORDER BY `sum_amount` DESC LIMIT 1000) x;
SELECT CONCAT('@@D8 ~|~ ', COUNT(*)) FROM (
  SELECT `channel`, SUM(`amount`) AS `sum_amount`
    FROM `dwd_sales_daily` GROUP BY `channel` ORDER BY `sum_amount` DESC LIMIT 1000) x;
SELECT CONCAT('@@D10 ~|~ ', COUNT(*)) FROM (
  SELECT `store_name`, SUM(`amount`) AS `sum_amount` FROM (SELECT s.store_name, s.region, s.city, MAX(s.monthly_target) AS target_amount, SUM(t.amount) AS amount, SUM(t.profit) AS profit, ROUND(SUM(t.amount) / NULLIF(MAX(s.monthly_target), 0) * 100, 2) AS achievement_rate, ROUND(SUM(t.profit) / NULLIF(SUM(t.amount), 0) * 100, 2) AS margin_rate FROM db_model.demo_stores s JOIN db_analysis.dws_store_sales t ON t.store_id = s.store_id GROUP BY s.store_name, s.region, s.city) t_source
    GROUP BY `store_name` ORDER BY `sum_amount` DESC LIMIT 1000) x;
SELECT CONCAT('@@TOP3 ~|~ ', `region`, ' ~|~ ', ROUND(`sum_amount`/10000,1)) FROM (
  SELECT `region`, SUM(`amount`) AS `sum_amount` FROM `dwd_sales_daily` GROUP BY `region` ORDER BY `sum_amount` DESC LIMIT 3) x;
