SET NAMES utf8mb4;
-- 只删本轮我自己写进去的行：title/name 都带 zz_ba1_ 前缀，id 一并锁死
DELETE FROM db_dashboard.dashboard_widget WHERE id IN (910, 911) AND title LIKE 'zz\_ba1\_%';
UPDATE db_dashboard.dashboard SET deleted = 1 WHERE id = 907 AND name LIKE 'zz\_ba1\_%';
DELETE FROM db_analysis.query_history WHERE id = 910 AND user_id = '1' AND `sql` LIKE '{"datasetId":1,%';
SELECT COUNT(*) AS widget_left FROM db_dashboard.dashboard_widget WHERE title LIKE 'zz\_ba1\_%';
SELECT COUNT(*) AS dash_alive_left FROM db_dashboard.dashboard WHERE name LIKE 'zz\_ba1\_%' AND deleted = 0;
SELECT COUNT(*) AS hist_left FROM db_analysis.query_history WHERE id = 910;
SELECT COUNT(*) AS hist_rows FROM db_analysis.query_history;
