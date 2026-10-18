SET NAMES utf8mb4;
SELECT '--- sales_daily shape ---' s; SHOW COLUMNS FROM db_model.demo_sales_daily;
SELECT '--- orders shape ---' s; SHOW COLUMNS FROM db_model.demo_orders;
SELECT '--- behavior shape ---' s; SHOW COLUMNS FROM db_model.demo_user_behavior;
SELECT '--- sales_daily range/coverage ---' s;
SELECT MIN(stat_date) mn, MAX(stat_date) mx, COUNT(*) rows_cnt,
       COUNT(DISTINCT region) regions, COUNT(DISTINCT category) cats,
       SUM(amount) total_amount, SUM(order_count) total_orders
FROM db_model.demo_sales_daily;
SELECT '--- sales_daily by region ---' s;
SELECT region, COUNT(*) c, SUM(amount) amt FROM db_model.demo_sales_daily GROUP BY region ORDER BY amt DESC;
SELECT '--- categories ---' s;
SELECT category, COUNT(*) c, SUM(amount) amt FROM db_model.demo_sales_daily GROUP BY category ORDER BY amt DESC;
SELECT '--- orders status ---' s;
SELECT status, COUNT(*) c, SUM(pay_amount) amt FROM db_model.demo_orders GROUP BY status;
SELECT '--- behavior ---' s;
SELECT event_type, COUNT(*) c, ROUND(AVG(duration_ms),1) avg_ms FROM db_model.demo_user_behavior GROUP BY event_type;
SELECT '--- views alive? ---' s;
SELECT COUNT(*) sales_view_rows FROM db_analysis.demo_sales_daily;
SELECT '--- 901 components_json raw ---' s;
SELECT components_json FROM db_screen.screen WHERE id=901\G
