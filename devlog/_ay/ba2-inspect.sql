SET NAMES utf8mb4;
SELECT id, dashboard_id, title, create_time FROM db_dashboard.dashboard_widget WHERE title LIKE 'zz\_%' ORDER BY id;
SELECT id, name, deleted FROM db_dashboard.dashboard WHERE name LIKE 'zz\_%' ORDER BY id;
SELECT id, user_id, datasource_id, status, LEFT(`sql`,30) AS sql_head, create_time FROM db_analysis.query_history WHERE id >= 908 ORDER BY id;
