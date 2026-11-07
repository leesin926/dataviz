-- PI4 · 回填模板（**不要直接执行**）：这是给 devlog/_pi/pi-backfill-shards.cjs 用的语句清单。
-- 为什么不直接执行：月度行既要按时间分、又要按租户哈希分实例，而"搬到第二个实例"必须跨实例搬运
-- （MySQL 没有本地跨实例 INSERT），所以真正的执行器是驱动脚本：ds0 本地写 + mysqldump→ds1 + 两边行数对账，
-- 对不平就拒绝收尾。留这个模板是为了让 SQL 判据本身可评审。
-- 另外：tenant_id 为空的行两边都数不到（MOD(NULL)=NULL），驱动脚本单独数、单独留档，不静默丢弃。
SET NAMES utf8mb4;

-- db_alert.alert_event → alert_event_202608 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202608`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_event → alert_event_202609 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202609`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_event → alert_event_202610 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202610`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_event → alert_event_202611 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202611`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_event → alert_event_202612 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202612`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_event → alert_event_202701 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202701`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_event → alert_event_202702 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202702`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_event → alert_event_202608 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202608`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_event → alert_event_202609 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202609`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_event → alert_event_202610 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202610`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_event → alert_event_202611 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202611`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_event → alert_event_202612 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202612`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_event → alert_event_202701 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202701`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_event → alert_event_202702 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_event_202702`
  SELECT * FROM `db_alert`.`alert_event`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_event 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）
SELECT CONCAT('@@OUT-OF-WINDOW db_alert.alert_event = ', COUNT(*)) FROM `db_alert`.`alert_event`
  WHERE create_time < '2026-08-01 00:00:00' OR create_time >= '2027-03-01 00:00:00';
-- db_alert.alert_event 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）
SELECT CONCAT('@@UNASSIGNED db_alert.alert_event = ', COUNT(*)) FROM `db_alert`.`alert_event`
  WHERE NOT (tenant_id IS NOT NULL AND create_time IS NOT NULL);

-- db_alert.alert_notify_log → alert_notify_log_202608 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202608`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_notify_log → alert_notify_log_202609 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202609`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_notify_log → alert_notify_log_202610 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202610`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_notify_log → alert_notify_log_202611 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202611`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_notify_log → alert_notify_log_202612 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202612`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_notify_log → alert_notify_log_202701 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202701`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_notify_log → alert_notify_log_202702 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202702`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_alert.alert_notify_log → alert_notify_log_202608 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202608`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_notify_log → alert_notify_log_202609 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202609`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_notify_log → alert_notify_log_202610 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202610`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_notify_log → alert_notify_log_202611 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202611`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_notify_log → alert_notify_log_202612 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202612`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_notify_log → alert_notify_log_202701 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202701`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_notify_log → alert_notify_log_202702 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_alert`.`alert_notify_log_202702`
  SELECT * FROM `db_alert`.`alert_notify_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_alert.alert_notify_log 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）
SELECT CONCAT('@@OUT-OF-WINDOW db_alert.alert_notify_log = ', COUNT(*)) FROM `db_alert`.`alert_notify_log`
  WHERE create_time < '2026-08-01 00:00:00' OR create_time >= '2027-03-01 00:00:00';
-- db_alert.alert_notify_log 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）
SELECT CONCAT('@@UNASSIGNED db_alert.alert_notify_log = ', COUNT(*)) FROM `db_alert`.`alert_notify_log`
  WHERE NOT (tenant_id IS NOT NULL AND create_time IS NOT NULL);

-- db_openapi.openapi_log → openapi_log_202608 @ds0（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202608`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_openapi.openapi_log → openapi_log_202609 @ds0（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202609`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_openapi.openapi_log → openapi_log_202610 @ds0（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202610`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_openapi.openapi_log → openapi_log_202611 @ds0（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202611`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_openapi.openapi_log → openapi_log_202612 @ds0（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202612`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_openapi.openapi_log → openapi_log_202701 @ds0（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202701`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_openapi.openapi_log → openapi_log_202702 @ds0（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202702`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_openapi.openapi_log → openapi_log_202608 @ds1（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202608`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_openapi.openapi_log → openapi_log_202609 @ds1（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202609`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_openapi.openapi_log → openapi_log_202610 @ds1（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202610`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_openapi.openapi_log → openapi_log_202611 @ds1（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202611`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_openapi.openapi_log → openapi_log_202612 @ds1（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202612`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_openapi.openapi_log → openapi_log_202701 @ds1（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202701`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_openapi.openapi_log → openapi_log_202702 @ds1（route-by=NONE；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_openapi`.`openapi_log_202702`
  SELECT * FROM `db_openapi`.`openapi_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(DATE_FORMAT(create_time, '%Y%m') AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_openapi.openapi_log 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）
SELECT CONCAT('@@OUT-OF-WINDOW db_openapi.openapi_log = ', COUNT(*)) FROM `db_openapi`.`openapi_log`
  WHERE create_time < '2026-08-01 00:00:00' OR create_time >= '2027-03-01 00:00:00';
-- db_openapi.openapi_log 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）
SELECT CONCAT('@@UNASSIGNED db_openapi.openapi_log = ', COUNT(*)) FROM `db_openapi`.`openapi_log`
  WHERE NOT (create_time IS NOT NULL);

-- db_auth.sys_login_log → sys_login_log_202608 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202608`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-08-01 00:00:00' AND login_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_auth.sys_login_log → sys_login_log_202609 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202609`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-09-01 00:00:00' AND login_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_auth.sys_login_log → sys_login_log_202610 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202610`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-10-01 00:00:00' AND login_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_auth.sys_login_log → sys_login_log_202611 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202611`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-11-01 00:00:00' AND login_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_auth.sys_login_log → sys_login_log_202612 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202612`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-12-01 00:00:00' AND login_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_auth.sys_login_log → sys_login_log_202701 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202701`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2027-01-01 00:00:00' AND login_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_auth.sys_login_log → sys_login_log_202702 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202702`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2027-02-01 00:00:00' AND login_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_auth.sys_login_log → sys_login_log_202608 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202608`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-08-01 00:00:00' AND login_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_auth.sys_login_log → sys_login_log_202609 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202609`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-09-01 00:00:00' AND login_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_auth.sys_login_log → sys_login_log_202610 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202610`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-10-01 00:00:00' AND login_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_auth.sys_login_log → sys_login_log_202611 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202611`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-11-01 00:00:00' AND login_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_auth.sys_login_log → sys_login_log_202612 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202612`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2026-12-01 00:00:00' AND login_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_auth.sys_login_log → sys_login_log_202701 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202701`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2027-01-01 00:00:00' AND login_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_auth.sys_login_log → sys_login_log_202702 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_auth`.`sys_login_log_202702`
  SELECT * FROM `db_auth`.`sys_login_log`
  WHERE login_time >= '2027-02-01 00:00:00' AND login_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND login_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_auth.sys_login_log 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）
SELECT CONCAT('@@OUT-OF-WINDOW db_auth.sys_login_log = ', COUNT(*)) FROM `db_auth`.`sys_login_log`
  WHERE login_time < '2026-08-01 00:00:00' OR login_time >= '2027-03-01 00:00:00';
-- db_auth.sys_login_log 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）
SELECT CONCAT('@@UNASSIGNED db_auth.sys_login_log = ', COUNT(*)) FROM `db_auth`.`sys_login_log`
  WHERE NOT (tenant_id IS NOT NULL AND login_time IS NOT NULL);

-- db_analysis.query_history → query_history_202608 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202608`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_analysis.query_history → query_history_202609 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202609`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_analysis.query_history → query_history_202610 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202610`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_analysis.query_history → query_history_202611 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202611`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_analysis.query_history → query_history_202612 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202612`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_analysis.query_history → query_history_202701 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202701`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_analysis.query_history → query_history_202702 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202702`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_analysis.query_history → query_history_202608 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202608`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_analysis.query_history → query_history_202609 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202609`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_analysis.query_history → query_history_202610 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202610`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_analysis.query_history → query_history_202611 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202611`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_analysis.query_history → query_history_202612 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202612`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_analysis.query_history → query_history_202701 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202701`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_analysis.query_history → query_history_202702 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_analysis`.`query_history_202702`
  SELECT * FROM `db_analysis`.`query_history`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_analysis.query_history 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）
SELECT CONCAT('@@OUT-OF-WINDOW db_analysis.query_history = ', COUNT(*)) FROM `db_analysis`.`query_history`
  WHERE create_time < '2026-08-01 00:00:00' OR create_time >= '2027-03-01 00:00:00';
-- db_analysis.query_history 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）
SELECT CONCAT('@@UNASSIGNED db_analysis.query_history = ', COUNT(*)) FROM `db_analysis`.`query_history`
  WHERE NOT (tenant_id IS NOT NULL AND create_time IS NOT NULL);

-- db_admin.audit_log → audit_log_202608 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202608`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_admin.audit_log → audit_log_202609 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202609`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_admin.audit_log → audit_log_202610 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202610`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_admin.audit_log → audit_log_202611 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202611`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_admin.audit_log → audit_log_202612 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202612`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_admin.audit_log → audit_log_202701 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202701`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_admin.audit_log → audit_log_202702 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202702`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_admin.audit_log → audit_log_202608 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202608`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_admin.audit_log → audit_log_202609 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202609`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_admin.audit_log → audit_log_202610 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202610`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_admin.audit_log → audit_log_202611 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202611`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_admin.audit_log → audit_log_202612 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202612`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_admin.audit_log → audit_log_202701 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202701`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_admin.audit_log → audit_log_202702 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_admin`.`audit_log_202702`
  SELECT * FROM `db_admin`.`audit_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_admin.audit_log 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）
SELECT CONCAT('@@OUT-OF-WINDOW db_admin.audit_log = ', COUNT(*)) FROM `db_admin`.`audit_log`
  WHERE create_time < '2026-08-01 00:00:00' OR create_time >= '2027-03-01 00:00:00';
-- db_admin.audit_log 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）
SELECT CONCAT('@@UNASSIGNED db_admin.audit_log = ', COUNT(*)) FROM `db_admin`.`audit_log`
  WHERE NOT (tenant_id IS NOT NULL AND create_time IS NOT NULL);

-- db_monitor.audit_log → audit_log_202608 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202608`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_monitor.audit_log → audit_log_202609 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202609`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_monitor.audit_log → audit_log_202610 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202610`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_monitor.audit_log → audit_log_202611 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202611`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_monitor.audit_log → audit_log_202612 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202612`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_monitor.audit_log → audit_log_202701 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202701`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_monitor.audit_log → audit_log_202702 @ds0（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202702`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 0;

-- db_monitor.audit_log → audit_log_202608 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202608`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-08-01 00:00:00' AND create_time < '2026-09-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_monitor.audit_log → audit_log_202609 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202609`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-09-01 00:00:00' AND create_time < '2026-10-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_monitor.audit_log → audit_log_202610 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202610`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-10-01 00:00:00' AND create_time < '2026-11-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_monitor.audit_log → audit_log_202611 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202611`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-11-01 00:00:00' AND create_time < '2026-12-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_monitor.audit_log → audit_log_202612 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202612`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2026-12-01 00:00:00' AND create_time < '2027-01-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_monitor.audit_log → audit_log_202701 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202701`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2027-01-01 00:00:00' AND create_time < '2027-02-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_monitor.audit_log → audit_log_202702 @ds1（route-by=TENANT；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）
INSERT INTO `db_monitor`.`audit_log_202702`
  SELECT * FROM `db_monitor`.`audit_log`
  WHERE create_time >= '2027-02-01 00:00:00' AND create_time < '2027-03-01 00:00:00'
    AND tenant_id IS NOT NULL AND create_time IS NOT NULL AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) = 1;

-- db_monitor.audit_log 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）
SELECT CONCAT('@@OUT-OF-WINDOW db_monitor.audit_log = ', COUNT(*)) FROM `db_monitor`.`audit_log`
  WHERE create_time < '2026-08-01 00:00:00' OR create_time >= '2027-03-01 00:00:00';
-- db_monitor.audit_log 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）
SELECT CONCAT('@@UNASSIGNED db_monitor.audit_log = ', COUNT(*)) FROM `db_monitor`.`audit_log`
  WHERE NOT (tenant_id IS NOT NULL AND create_time IS NOT NULL);
