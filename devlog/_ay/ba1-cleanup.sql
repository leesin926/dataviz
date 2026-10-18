SET NAMES utf8mb4;
DELETE FROM db_dashboard.dashboard_widget WHERE id = 909 AND title LIKE 'zz\_ba1\_%';
SELECT ROW_COUNT() AS deleted_widgets;
SELECT id, dashboard_id, title, config_json IS NULL AS cfg_null FROM db_dashboard.dashboard_widget WHERE dashboard_id IN (908, 909) OR title LIKE 'zz\_ba1\_%';
SELECT id, name, deleted FROM db_dashboard.dashboard WHERE id IN (908, 909);
