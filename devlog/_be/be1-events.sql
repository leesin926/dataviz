SET NAMES utf8mb4;
SELECT CONCAT('@@TOTAL ', COUNT(*)) FROM db_alert.alert_event WHERE deleted = 0;
SELECT CONCAT('@@FAKE ', COUNT(*)) FROM db_alert.alert_event WHERE deleted = 0 AND message LIKE 'Alert: %exceeded threshold%';
SELECT CONCAT('@@REAL ', COUNT(*)) FROM db_alert.alert_event WHERE deleted = 0 AND message LIKE '规则%';
SELECT CONCAT('@@LASTFAKE ~|~ ', MAX(create_time)) FROM db_alert.alert_event WHERE deleted = 0 AND message LIKE 'Alert: %exceeded threshold%';
SELECT CONCAT('@@RULES_ON ', COUNT(*)) FROM db_alert.alert_rule WHERE deleted = 0 AND enabled = 1;
