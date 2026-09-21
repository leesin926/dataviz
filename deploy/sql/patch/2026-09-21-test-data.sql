-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

-- =============================================================
-- DataViz 测试数据补丁  2026-09-21（Round E / Y1）
-- -------------------------------------------------------------
-- 目的：按「现有表」补齐可展示的测试数据，让管理端/设计端各页面与接口
--       返回真实业务行，而不是空列表。
-- 执行顺序（必须先结构后数据）：
--   1) 2026-09-21-schema-completion.sql   补齐代码引用但从未建表的表/列
--   2) 2026-09-21-schema-conflict-fix.sql 建遗漏实体表 + 放宽/改类型冲突列 + db_auth 列名对齐
--   3) 本文件                              测试数据
--   4) 2026-09-21-encoding-repair.sql      修复 init 脚本 latin1 导入损毁的中文
-- 执行方式（⚠️ 必须带 --default-character-set=utf8mb4，否则中文会在入库时被 latin1 连接转成 `?`）：
--   docker exec -i dataviz-mysql mysql -uroot -p --default-character-set=utf8mb4 < 本文件
-- 幂等性：固定 ID 段（业务 900~949 / 日志 9000~9099），重复执行为覆盖写入；
--         日志类表先 DELETE 同段旧行再插入，不动业务真实数据。
-- 约定：tenant_id = 1（admin 所属租户）；deleted = 0；create_by 留 NULL
--       （与 screen-service 现有行一致，避免 varchar/bigint 口径冲突）。
-- 演示账号：只补展示字段，password 置空串，不能直接登录；
--       需登录请在管理端「用户管理 → 重置密码」另设。
-- =============================================================

-- =============================================================
-- 0. （已删除）历史"双重编码修复"CONVERT 语句块
--    原因：库里的问号不是双重编码，而是 latin1 连接下**入库即损毁**（每个汉字写成了 0x3F），
--         CONVERT(CAST(CONVERT(col USING latin1) AS BINARY) USING utf8mb4) 对这类行是无效操作；
--         更糟的是它会把本来就正常的 UTF-8 行（例如本文件第一次执行时已插入的 901~903 租户
--         contact_name）反过来打成 `??`。
--    现状：修复改由 2026-09-21-encoding-repair.sql 用「按业务键整行覆盖」的方式完成。
--    执行顺序：completion → conflict-fix → 本文件 → encoding-repair
-- =============================================================

-- =============================================================
-- 1. db_admin：租户 / 系统配置 / 审计日志
-- =============================================================
USE db_admin;

INSERT INTO sys_tenant (id, name, code, contact_name, contact_phone, contact_email, max_users, max_datasources, max_storage_mb, config, status, expire_time, create_time, update_time, deleted) VALUES
 (901, '星云零售集团', 'nebula-retail', '周敏', '13800000901', 'ops@nebula-retail.example.com', 200, 20, 51200, '{"theme":"dark","modules":["screen","alert"],"region":"cn-east-1"}', 'ACTIVE', '2027-03-31 23:59:59', NOW() - INTERVAL 120 DAY, NOW() - INTERVAL 2 DAY, 0),
 (902, '长江智慧交通', 'yangtse-traffic', '李雷', '13800000902', 'admin@yt-traffic.example.com', 50, 8, 10240, '{"theme":"light","modules":["dashboard"],"region":"cn-central"}', 'SUSPENDED', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 200 DAY, NOW() - INTERVAL 10 DAY, 0),
 (903, '海纳能源', 'haina-energy', '赵强', '13800000903', 'it@haina-energy.example.com', 100, 15, 20480, '{"theme":"dark","modules":["screen","etl"],"region":"cn-north"}', 'TRIAL', NOW() + INTERVAL 45 DAY, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), contact_name = VALUES(contact_name), status = VALUES(status), config = VALUES(config), update_time = VALUES(update_time);

INSERT INTO sys_config (id, config_key, config_value, config_type, remark, tenant_id, create_time, update_time) VALUES
 (901, 'system.name', 'DataViz 数据可视化平台', 'SYSTEM', '平台名称，登录页与侧边栏展示', 1, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY),
 (902, 'system.page-size', '20', 'CUSTOM', '列表默认分页条数', 1, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 5 DAY),
 (903, 'screen.default-adapt-mode', 'scale', 'SYSTEM', '新建大屏默认适配方式：scale / fixed-width / responsive', 1, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY),
 (904, 'screen.canvas-bg-color', '#ffffff', 'SYSTEM', '设计器画布锁定底色（白底，不可在属性面板修改）', 1, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
 (905, 'alert.default-silence-minutes', '15', 'CUSTOM', '告警默认静默分钟数', 1, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY),
 (906, 'datasource.query-timeout-seconds', '30', 'SYSTEM', '数据源查询超时（秒）', 1, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY)
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark), update_time = VALUES(update_time);

DELETE FROM audit_log WHERE id BETWEEN 9000 AND 9099;
INSERT INTO audit_log (id, user_id, username, module, action, method, request_url, request_params, response_code, ip, user_agent, execution_time, tenant_id, create_time) VALUES
 (9001, 1, 'admin', 'auth', 'login', 'POST', '/api/auth/login', '{"username":"admin","captchaKey":"a1b2c3"}', 200, '10.20.30.41', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/139', 126, 1, NOW() - INTERVAL 6 MINUTE),
 (9002, 1, 'admin', 'auth', 'logout', 'POST', '/api/auth/logout', '{}', 200, '10.20.30.41', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/139', 18, 1, NOW() - INTERVAL 3 HOUR),
 (9003, 1, 'admin', 'auth', 'login', 'POST', '/api/auth/login', '{"username":"admin"}', 401, '10.20.30.77', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) Safari/605', 44, 1, NOW() - INTERVAL 5 HOUR),
 (9004, 1, 'admin', 'screen', 'create', 'POST', '/api/screen', '{"name":"销售实时监控大屏","adaptMode":"scale"}', 200, '10.20.30.41', 'Chrome/139', 63, 1, NOW() - INTERVAL 1 DAY),
 (9005, 1, 'admin', 'screen', 'update', 'PUT', '/api/screen/901', '{"id":901,"name":"销售实时监控大屏"}', 200, '10.20.30.41', 'Chrome/139', 88, 1, NOW() - INTERVAL 1 DAY),
 (9006, 1, 'admin', 'screen', 'publish', 'POST', '/api/screen/901/publish', '{"id":901}', 200, '10.20.30.41', 'Chrome/139', 35, 1, NOW() - INTERVAL 23 HOUR),
 (9007, 1, 'admin', 'screen', 'delete', 'DELETE', '/api/screen/903', '{"id":903}', 409, '10.20.30.41', 'Chrome/139', 21, 1, NOW() - INTERVAL 22 HOUR),
 (9008, 1, 'admin', 'datasource', 'create', 'POST', '/api/datasource', '{"name":"分析库(演示)","type":"mysql"}', 200, '10.20.30.42', 'Chrome/139', 152, 1, NOW() - INTERVAL 2 DAY),
 (9009, 1, 'admin', 'datasource', 'test-connection', 'POST', '/api/datasource/1/test-connection', '{"id":1}', 200, '10.20.30.42', 'Chrome/139', 1830, 1, NOW() - INTERVAL 2 DAY),
 (9010, 1, 'admin', 'datasource', 'delete', 'DELETE', '/api/datasource/4', '{"id":4}', 404, '10.20.30.42', 'Chrome/139', 12, 1, NOW() - INTERVAL 2 DAY),
 (9011, 1, 'admin', 'dashboard', 'create', 'POST', '/api/dashboard', '{"name":"经营总览"}', 200, '10.20.30.43', 'Edge/139', 74, 1, NOW() - INTERVAL 3 DAY),
 (9012, 1, 'admin', 'dashboard', 'copy', 'POST', '/api/dashboard/901/copy', '{"id":901}', 200, '10.20.30.43', 'Edge/139', 96, 1, NOW() - INTERVAL 3 DAY),
 (9013, 1, 'admin', 'alert', 'create', 'POST', '/api/alert/rule', '{"name":"CPU 使用率过高","severity":"CRITICAL"}', 200, '10.20.30.44', 'Chrome/139', 58, 1, NOW() - INTERVAL 4 DAY),
 (9014, 1, 'admin', 'alert', 'enable', 'POST', '/api/alert/rule/901/enable', '{"id":901}', 200, '10.20.30.44', 'Chrome/139', 24, 1, NOW() - INTERVAL 4 DAY),
 (9015, 1, 'admin', 'alert', 'disable', 'POST', '/api/alert/rule/902/disable', '{"id":902}', 200, '10.20.30.44', 'Chrome/139', 26, 1, NOW() - INTERVAL 4 DAY),
 (9016, 1, 'admin', 'user', 'create', 'POST', '/api/user', '{"username":"analyst01","nickname":"数据分析师"}', 200, '10.20.30.45', 'Chrome/139', 143, 1, NOW() - INTERVAL 5 DAY),
 (9017, 1, 'admin', 'user', 'update', 'PUT', '/api/user/903', '{"id":903,"status":0}', 200, '10.20.30.45', 'Chrome/139', 67, 1, NOW() - INTERVAL 5 DAY),
 (9018, 1, 'admin', 'user', 'reset-password', 'PUT', '/api/user/904/password/reset', '{"newPassword":"***"}', 200, '10.20.30.45', 'Chrome/139', 210, 1, NOW() - INTERVAL 5 DAY),
 (9019, 1, 'admin', 'tenant', 'create', 'POST', '/api/admin/tenant', '{"name":"海纳能源","code":"haina-energy"}', 200, '10.20.30.46', 'Chrome/139', 81, 1, NOW() - INTERVAL 6 DAY),
 (9020, 1, 'admin', 'tenant', 'disable', 'POST', '/api/admin/tenant/902/disable', '{"id":902}', 200, '10.20.30.46', 'Chrome/139', 30, 1, NOW() - INTERVAL 6 DAY),
 (9021, 1, 'admin', 'config', 'update', 'PUT', '/api/admin/config', '{"configKey":"screen.mourning.enabled","configValue":"true"}', 200, '10.20.30.47', 'Chrome/139', 40, 1, NOW() - INTERVAL 7 DAY),
 (9022, 1, 'admin', 'config', 'update', 'PUT', '/api/admin/config', '{"configKey":"screen.mourning.enabled","configValue":"false"}', 200, '10.20.30.47', 'Chrome/139', 38, 1, NOW() - INTERVAL 7 DAY),
 (9023, 2, 'ops', 'role', 'update', 'PUT', '/api/role/2', '{"id":2,"permissionIds":[1,2,3]}', 403, '10.20.30.48', 'Firefox/141', 15, 1, NOW() - INTERVAL 8 DAY),
 (9024, 1, 'admin', 'etl', 'start', 'POST', '/api/etl/task/901/start', '{"id":901}', 200, '10.20.30.49', 'Chrome/139', 205, 1, NOW() - INTERVAL 9 DAY);

-- =============================================================
-- 2. db_datasource：数据源 + 元数据
--    DDL 拆列(host/port/database_name/username/password/properties)，
--    同时补 config JSON 列（schema-completion 添加，datasource-service 实体读这一列）。
--    password 一律留空占位：接口测试不需要真实口令，前端也永不回显。
-- =============================================================
USE db_datasource;

DELETE FROM datasource WHERE id BETWEEN 900 AND 949;
-- config 是 datasource-service 实体真正读取的列（schema-completion 补列）；host/port 等旧列同步保留，便于对照
INSERT INTO datasource (id, tenant_id, name, type, config, host, port, database_name, username, password, properties, status, last_check_time, description, create_time, update_time, deleted) VALUES
 (901, 1, '分析库（演示）', 'mysql', '{"host":"172.18.0.1","port":3306,"database":"db_analysis","username":"root","maxPoolSize":10,"minIdle":2,"queryTimeoutSeconds":30}', '172.18.0.1', 3306, 'db_analysis', 'root', '', '{"maxPoolSize":10,"minIdle":2,"queryTimeoutSeconds":30}', 1, NOW() - INTERVAL 12 MINUTE, '平台自带的分析库，承载 OLAP 演示宽表', NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 12 MINUTE, 0),
 (902, 1, '订单业务库', 'mysql', '{"host":"172.18.0.1","port":3306,"database":"db_model","username":"root","maxPoolSize":8,"minIdle":1}', '172.18.0.1', 3306, 'db_model', 'root', '', '{"maxPoolSize":8,"minIdle":1}', 1, NOW() - INTERVAL 1 HOUR, '订单/客户维度来源', NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 1 HOUR, 0),
 (903, 1, '行为日志集群', 'clickhouse', '{"host":"172.18.0.1","port":8123,"database":"dwh","username":"default","cluster":"ch-single","replica":1}', '172.18.0.1', 8123, 'dwh', 'default', '', '{"cluster":"ch-single","replica":1}', 1, NOW() - INTERVAL 2 HOUR, '埋点与行为明细（示例，未真实连通）', NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 2 HOUR, 0),
 (904, 1, '指标开放接口', 'api', '{"endpoint":"https://api.example.com/metrics","authType":"bearer","cacheSeconds":300}', 'https://api.example.com', 443, '', '', '', '{"endpoint":"/metrics","authType":"bearer","cacheSeconds":300}', 0, NOW() - INTERVAL 3 DAY, '第三方指标 API 数据源，已停用', NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 3 DAY, 0);

DELETE FROM datasource_metadata WHERE id BETWEEN 900 AND 949;
INSERT INTO datasource_metadata (id, datasource_id, table_name, table_comment, columns_json, sync_time) VALUES
 (901, 901, 'sales_daily', '日销售汇总', '[{"columnName":"stat_date","dataType":"DATE","primaryKey":false,"nullable":false,"comment":"统计日期"},{"columnName":"region","dataType":"VARCHAR(32)","primaryKey":false,"nullable":true,"comment":"大区"},{"columnName":"category","dataType":"VARCHAR(32)","primaryKey":false,"nullable":true,"comment":"商品类目"},{"columnName":"amount","dataType":"DECIMAL(18,2)","primaryKey":false,"nullable":true,"comment":"销售额"},{"columnName":"order_count","dataType":"INT","primaryKey":false,"nullable":true,"comment":"订单量"}]', NOW() - INTERVAL 1 HOUR),
 (902, 901, 'user_behavior', '用户行为明细', '[{"columnName":"event_time","dataType":"DATETIME","primaryKey":false,"nullable":false,"comment":"事件时间"},{"columnName":"user_id","dataType":"BIGINT","primaryKey":false,"nullable":true,"comment":"用户ID"},{"columnName":"event_type","dataType":"VARCHAR(32)","primaryKey":false,"nullable":true,"comment":"事件类型"},{"columnName":"duration_ms","dataType":"INT","primaryKey":false,"nullable":true,"comment":"停留时长(ms)"}]', NOW() - INTERVAL 1 HOUR),
 (903, 902, 'orders', '订单主表', '[{"columnName":"order_id","dataType":"BIGINT","primaryKey":true,"nullable":false,"comment":"订单号"},{"columnName":"pay_amount","dataType":"DECIMAL(18,2)","primaryKey":false,"nullable":true,"comment":"实付金额"},{"columnName":"created_at","dataType":"DATETIME","primaryKey":false,"nullable":true,"comment":"下单时间"},{"columnName":"status","dataType":"VARCHAR(16)","primaryKey":false,"nullable":true,"comment":"订单状态"}]', NOW() - INTERVAL 2 HOUR),
 (904, 903, 'dwd_event_log', '行为日志宽表', '[{"columnName":"dt","dataType":"DATE","primaryKey":true,"nullable":false,"comment":"分区日期"},{"columnName":"page","dataType":"String","primaryKey":false,"nullable":true,"comment":"页面"},{"columnName":"pv","dataType":"UInt64","primaryKey":false,"nullable":true,"comment":"浏览量"}]', NOW() - INTERVAL 6 HOUR);

-- =============================================================
-- 3. db_model：数据集 / 维度 / 度量
--    ⚠️ model-service 的三个 Mapper 分别绑定 ModelDataset/ModelDimension/ModelMetric，
--       即接口真正读的是 model_dataset / model_dimension / model_metric；
--       旧表 dataset（entity Dataset 无任何 Mapper 引用）不造数据，避免误导。
--    ⚠️ DatasetServiceImpl.previewDataset 用 model-service 自己的 JdbcTemplate 执行 SQL，
--       即只会查 db_model 本库，不会路由到 datasourceId 指向的库；
--       所以被数据集引用的物理表必须真实建在 db_model 里（见 3.0），否则预览接口必然失败。
-- =============================================================
USE db_model;

-- ---------- 3.0 演示物理表（供数据集预览 / 自助分析查询） ----------
CREATE TABLE IF NOT EXISTS demo_sales_daily (
    stat_date   DATE          NOT NULL COMMENT '统计日期',
    region      VARCHAR(32)   NOT NULL COMMENT '大区',
    category    VARCHAR(32)   NOT NULL COMMENT '商品类目',
    amount      DECIMAL(18,2) NOT NULL COMMENT '销售额',
    order_count INT           NOT NULL COMMENT '订单量',
    KEY idx_demo_sales_date (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '演示数据：日销售汇总';

CREATE TABLE IF NOT EXISTS demo_user_behavior (
    event_time  DATETIME     NOT NULL COMMENT '事件时间',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    event_type  VARCHAR(32)  NOT NULL COMMENT '事件类型',
    page        VARCHAR(64)  NOT NULL COMMENT '页面',
    duration_ms INT          NOT NULL COMMENT '停留时长(ms)',
    KEY idx_demo_behavior_time (event_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '演示数据：用户行为明细';

CREATE TABLE IF NOT EXISTS demo_orders (
    order_id    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单号',
    user_id     BIGINT        NOT NULL COMMENT '下单用户',
    region      VARCHAR(32)   NOT NULL COMMENT '大区',
    pay_amount  DECIMAL(18,2) NOT NULL COMMENT '实付金额',
    status      VARCHAR(16)   NOT NULL COMMENT '订单状态',
    created_at  DATETIME      NOT NULL COMMENT '下单时间',
    PRIMARY KEY (order_id),
    KEY idx_demo_orders_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '演示数据：订单主表';

DELETE FROM demo_sales_daily;
-- 近 30 天 × 5 大区 × 3 类目 = 450 行，数值确定（便于回归比对），不使用随机数
INSERT INTO demo_sales_daily (stat_date, region, category, amount, order_count)
SELECT DATE_SUB(CURDATE(), INTERVAL n.n DAY)                              AS stat_date,
       r.region,
       c.category,
       ROUND(18000 + (n.n * 977 + r.ord * 131 + c.ord * 71) % 62000 + c.ord * 13.5, 2) AS amount,
       90 + (n.n * 17 + r.ord * 29 + c.ord * 7) % 230                    AS order_count
FROM (SELECT d1.d + d2.d * 10 AS n
      FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2) d1,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
      WHERE d1.d + d2.d * 10 < 30) n
         CROSS JOIN (SELECT '华东' region, 1 ord UNION ALL SELECT '华北', 2 UNION ALL SELECT '华南', 3
                     UNION ALL SELECT '西南', 4 UNION ALL SELECT '东北', 5) r
         CROSS JOIN (SELECT '家电' category, 1 ord UNION ALL SELECT '服装', 2 UNION ALL SELECT '食品', 3) c;

DELETE FROM demo_user_behavior;
INSERT INTO demo_user_behavior (event_time, user_id, event_type, page, duration_ms)
SELECT DATE_SUB(NOW(), INTERVAL (n.n * 37 + r.ord * 11) MINUTE),
       1000 + (n.n * 7 + r.ord) % 40,
       e.event_type,
       e.page,
       2000 + (n.n * 613 + r.ord * 97 + e.ord * 53) % 240000
FROM (SELECT d1.d + d2.d * 10 AS n
      FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) d1,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
      WHERE d1.d + d2.d * 10 < 50) n
         CROSS JOIN (SELECT 1 ord UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) r
         CROSS JOIN (SELECT 'view' event_type, '/home' page, 1 ord UNION ALL
                     SELECT 'click', '/analysis', 2 UNION ALL
                     SELECT 'login', '/login', 3 UNION ALL
                     SELECT 'export', '/dashboard', 4) e;

DELETE FROM demo_orders;
INSERT INTO demo_orders (user_id, region, pay_amount, status, created_at)
SELECT 1000 + (n.n * 13 + r.ord) % 40,
       r.region,
       ROUND(60 + (n.n * 271 + r.ord * 91) % 4200 + r.ord * 7.25, 2),
       CASE (n.n + r.ord) % 4 WHEN 0 THEN 'PAID' WHEN 1 THEN 'SHIPPED' WHEN 2 THEN 'REFUNDING' ELSE 'CANCELLED' END,
       DATE_SUB(NOW(), INTERVAL (n.n * 41 + r.ord * 19) MINUTE)
FROM (SELECT d1.d + d2.d * 10 AS n
      FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) d1,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
      WHERE d1.d + d2.d * 10 < 50) n
         CROSS JOIN (SELECT '华东' region, 1 ord UNION ALL SELECT '华北', 2 UNION ALL SELECT '华南', 3
                     UNION ALL SELECT '西南', 4 UNION ALL SELECT '东北', 5) r;

-- ---------- 3.1 model_dataset：DatasetMapper 真正查询的表 ----------
-- dimensions / metrics 是 JSON 文本列，内部结构必须与前端 QueryDimension / QueryMeasure 对齐：
--   {field, displayName, type|aggregation, format}
DELETE FROM model_dataset WHERE id BETWEEN 900 AND 949;
INSERT INTO model_dataset (id, tenant_id, name, datasource_id, table_name, sql_query, dimensions, metrics, filters, create_time, update_time, deleted) VALUES
 (901, 1, '日销售分析', 901, 'demo_sales_daily', NULL,
  '[{"field":"stat_date","displayName":"统计日期","type":"time","format":"yyyy-MM-dd"},{"field":"region","displayName":"大区","type":"category"},{"field":"category","displayName":"商品类目","type":"category"}]',
  '[{"field":"amount","displayName":"销售额","aggregation":"sum","format":"#,##0.00"},{"field":"order_count","displayName":"订单量","aggregation":"sum"},{"field":"amount","displayName":"客单价","aggregation":"avg"}]',
  '[]', NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 1 HOUR, 0),
 (902, 1, '用户行为概览', 901, NULL,
  'SELECT event_time, user_id, event_type, page, duration_ms FROM demo_user_behavior',
  '[{"field":"event_time","displayName":"事件时间","type":"time","format":"yyyy-MM-dd HH:mm:ss"},{"field":"user_id","displayName":"用户","type":"category"},{"field":"event_type","displayName":"事件类型","type":"category"},{"field":"page","displayName":"页面","type":"category"}]',
  '[{"field":"duration_ms","displayName":"停留时长","aggregation":"avg"},{"field":"user_id","displayName":"去重用户数","aggregation":"count"}]',
  '[{"field":"event_type","operator":"!=","value":"heartbeat"}]', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 3 HOUR, 0),
 (903, 1, '订单履约分析', 902, 'demo_orders', NULL,
  '[{"field":"region","displayName":"大区","type":"category"},{"field":"status","displayName":"订单状态","type":"category"},{"field":"created_at","displayName":"下单时间","type":"time","format":"yyyy-MM-dd HH:mm:ss"}]',
  '[{"field":"pay_amount","displayName":"实付金额","aggregation":"sum","format":"#,##0.00"},{"field":"order_id","displayName":"订单数","aggregation":"count"}]',
  '[]', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0),
 (904, 1, '已删除的临时数据集', 901, 'demo_sales_daily', NULL,
  '[{"field":"stat_date","displayName":"统计日期","type":"time"}]',
  '[]', '[]', NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY, 1);

-- ---------- 3.2 model_dimension（DimensionMapper 查询，按 datasourceId 过滤） ----------
DELETE FROM model_dimension WHERE id BETWEEN 900 AND 949;
INSERT INTO model_dimension (id, tenant_id, dataset_id, name, display_name, column_name, data_type, dimension_type, datasource_id, table_name, description, format, hierarchy_json, sort, create_time, update_time, deleted) VALUES
 (901, 1, 901, 'stat_date',   '统计日期', 'stat_date',   'DATE',     'TIME',     901, 'demo_sales_daily',  '天粒度，近 30 天', 'yyyy-MM-dd', '{"levels":["stat_date","month","quarter","year"]}', 1, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 0),
 (902, 1, 901, 'region',      '大区',     'region',      'VARCHAR',  'GEO',      901, 'demo_sales_daily',  '五级大区', NULL, '{"levels":["region","province","city"]}', 2, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 0),
 (903, 1, 901, 'category',    '商品类目', 'category',    'VARCHAR',  'CATEGORY', 901, 'demo_sales_daily',  '家电/服装/食品', NULL, NULL, 3, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 0),
 (904, 1, 902, 'event_time',  '事件时间', 'event_time',  'DATETIME', 'TIME',     901, 'demo_user_behavior', NULL, 'yyyy-MM-dd HH:mm:ss', NULL, 1, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, 0),
 (905, 1, 902, 'event_type',  '事件类型', 'event_type',  'VARCHAR',  'CATEGORY', 901, 'demo_user_behavior', 'view/click/login/export', NULL, NULL, 2, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, 0),
 (906, 1, 903, 'region',      '大区',     'region',      'VARCHAR',  'GEO',      902, 'demo_orders',       NULL, NULL, NULL, 1, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0),
 (907, 1, 903, 'status',      '订单状态', 'status',      'VARCHAR',  'CATEGORY', 902, 'demo_orders',       'PAID/SHIPPED/REFUNDING/CANCELLED', NULL, NULL, 2, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0);

-- ---------- 3.3 model_metric（MetricMapper 查询） ----------
DELETE FROM model_metric WHERE id BETWEEN 900 AND 949;
INSERT INTO model_metric (id, tenant_id, dataset_id, name, display_name, column_name, expression, agg_function, aggregation_type, data_type, datasource_id, table_name, description, format, sort, create_time, update_time, deleted) VALUES
 (901, 1, 901, 'amount',      '销售额',     'amount',      NULL,                                                  'SUM',   'SUM',    'DECIMAL', 901, 'demo_sales_daily',  '销售总额（元）', '#,##0.00', 1, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 0),
 (902, 1, 901, 'order_count', '订单量',     'order_count', NULL,                                                  'SUM',   'SUM',    'BIGINT',  901, 'demo_sales_daily',  '订单笔数', '#,##0', 2, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 0),
 (903, 1, 901, 'avg_ticket',  '客单价',     NULL,          'SUM(amount) / NULLIF(SUM(order_count), 0)',           'CUSTOM','CUSTOM', 'DECIMAL', 901, 'demo_sales_daily',  '销售额 / 订单量', '#,##0.00', 3, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, 0),
 (904, 1, 902, 'duration_ms', '平均停留时长', 'duration_ms', NULL,                                                  'AVG',   'AVG',    'BIGINT',  901, 'demo_user_behavior', '单次会话平均停留(ms)', '#,##0"ms"', 1, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, 0),
 (905, 1, 903, 'pay_amount',  '实付金额',   'pay_amount',  NULL,                                                  'SUM',   'SUM',    'DECIMAL', 902, 'demo_orders',       '已扣优惠', '#,##0.00', 1, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0);
-- =============================================================
-- 4. db_etl：任务 + 运行日志 + 运行实例
--    ⚠️ EtlTask entity 不映射 dag_json，编辑器 DAG 存在 transform_config；
--       前端 api-client/etl.ts 以 transformConfig ↔ dag 做适配，故 DAG 一律写进 transform_config。
--       节点 config 字段名遵循前端 EtlNodeConfig（datasourceId / tableName，不是 table）。
--    ⚠️ status 为 Java 枚举大写名（STOPPED/RUNNING/PAUSED/ERROR/COMPLETED），schema-conflict-fix 已把列改为 varchar(32)。
--    ⚠️ /etl/task/{id}/logs 读的是 etl_task_log（schema-completion 新建），执行日志面板数据来源在此。
-- =============================================================
USE db_etl;

DELETE FROM etl_task WHERE id BETWEEN 900 AND 949;
INSERT INTO etl_task (id, tenant_id, name, description, source_datasource_id, target_datasource_id, source_table, target_table, transform_config, schedule_cron, status, last_run_time, last_run_status, create_time, update_time, deleted) VALUES
 (901, 1, '销售日汇总入仓', '每日 01:10 从订单库抽取汇总写入分析库', 902, 901, 'demo_orders', 'demo_sales_daily',
  '{"nodes":[{"id":"n1","type":"input","name":"订单明细(demo_orders)","x":80,"y":120,"config":{"datasourceId":902,"tableName":"demo_orders"}},{"id":"n2","type":"aggregate","name":"按大区/日期汇总","x":320,"y":120,"config":{"groupBy":["DATE(created_at)","region"],"agg":{"SUM(pay_amount)":"amount","COUNT(1)":"order_count"}}},{"id":"n3","type":"output","name":"写入 demo_sales_daily","x":580,"y":120,"config":{"datasourceId":901,"tableName":"demo_sales_daily","mode":"upsert"}}],"edges":[{"id":"e1","source":"n1","target":"n2"},{"id":"e2","source":"n2","target":"n3"}]}',
  '10 1 * * *', 'RUNNING', NOW() - INTERVAL 6 HOUR, 'SUCCESS', NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 6 HOUR, 0),
 (902, 1, '用户行为清洗', '剔除心跳事件，落地到行为明细表', 901, 901, 'demo_user_behavior', 'demo_user_behavior',
  '{"nodes":[{"id":"n1","type":"input","name":"原始埋点","x":80,"y":140,"config":{"datasourceId":901,"tableName":"demo_user_behavior"}},{"id":"n2","type":"filter","name":"过滤心跳事件","x":320,"y":140,"config":{"filters":[{"field":"event_type","operator":"!=","value":"heartbeat"}]}},{"id":"n3","type":"output","name":"写回 demo_user_behavior","x":560,"y":140,"config":{"datasourceId":901,"tableName":"demo_user_behavior","mode":"append"}}],"edges":[{"id":"e1","source":"n1","target":"n2"},{"id":"e2","source":"n2","target":"n3"}]}',
  '*/30 * * * *', 'RUNNING', NOW() - INTERVAL 25 MINUTE, 'SUCCESS', NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 25 MINUTE, 0),
 (903, 1, '门店主数据同步', '从第三方指标接口拉取门店维表（已停用）', 904, 901, '/stores', 'demo_sales_daily',
  '{"nodes":[{"id":"n1","type":"input","name":"门店接口","x":100,"y":100,"config":{"datasourceId":904,"sql":"SELECT * FROM /stores"}},{"id":"n2","type":"output","name":"写入维表","x":360,"y":100,"config":{"datasourceId":901,"tableName":"demo_sales_daily","mode":"overwrite"}}],"edges":[{"id":"e1","source":"n1","target":"n2"}]}',
  '0 2 * * 1', 'STOPPED', NOW() - INTERVAL 9 DAY, 'ERROR', NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 9 DAY, 0);

DELETE FROM etl_task_log WHERE id BETWEEN 900 AND 949;
INSERT INTO etl_task_log (id, task_id, start_time, end_time, status, records_read, records_written, error_message, create_time) VALUES
 (901, 901, NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 6 HOUR + INTERVAL 3 MINUTE, 'SUCCESS', 128540, 1204, NULL, NOW() - INTERVAL 6 HOUR),
 (902, 901, NOW() - INTERVAL 30 HOUR, NOW() - INTERVAL 30 HOUR + INTERVAL 3 MINUTE, 'SUCCESS', 126010, 1198, NULL, NOW() - INTERVAL 30 HOUR),
 (903, 901, NOW() - INTERVAL 54 HOUR, NOW() - INTERVAL 54 HOUR + INTERVAL 2 MINUTE, 'FAILED', 0, 0, 'Communications link failure: 172.18.0.1:3306', NOW() - INTERVAL 54 HOUR),
 (904, 902, NOW() - INTERVAL 25 MINUTE, NOW() - INTERVAL 24 MINUTE, 'SUCCESS', 88245, 84125, NULL, NOW() - INTERVAL 25 MINUTE),
 (905, 902, NOW() - INTERVAL 85 MINUTE, NOW() - INTERVAL 84 MINUTE, 'FAILED', 12000, 0, 'connection reset by peer', NOW() - INTERVAL 85 MINUTE),
 (906, 903, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY + INTERVAL 40 SECOND, 'CANCELLED', 420, 0, '任务已停用，由操作者取消', NOW() - INTERVAL 9 DAY);

DELETE FROM etl_task_instance WHERE id BETWEEN 900 AND 949;
-- status: 0-运行中 1-成功 2-失败 3-取消（EtlInstanceVO.status 是 Integer）；trigger_type: manual/cron/api
INSERT INTO etl_task_instance (id, task_id, tenant_id, trigger_type, status, start_time, end_time, duration_ms, log, error_msg, create_time) VALUES
 (901, 901, 1, 'cron',    1, NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 6 HOUR + INTERVAL 3 MINUTE, 182450, 'extracted 128,540 rows; aggregated 1,204 groups; loaded 1,204 rows', NULL, NOW() - INTERVAL 6 HOUR),
 (902, 901, 1, 'cron',    1, NOW() - INTERVAL 30 HOUR, NOW() - INTERVAL 30 HOUR + INTERVAL 3 MINUTE, 179800, 'extracted 126,010 rows; loaded 1,198 rows', NULL, NOW() - INTERVAL 30 HOUR),
 (903, 901, 1, 'manual',  0, NOW() - INTERVAL 2 MINUTE, NULL, NULL, 'extracting from demo_orders...', NULL, NOW() - INTERVAL 2 MINUTE),
 (904, 902, 1, 'cron',    1, NOW() - INTERVAL 25 MINUTE, NOW() - INTERVAL 24 MINUTE, 61200, 'filtered 4,120 of 88,245 rows', NULL, NOW() - INTERVAL 25 MINUTE),
 (905, 902, 1, 'cron',    2, NOW() - INTERVAL 85 MINUTE, NOW() - INTERVAL 84 MINUTE, 42300, 'connection reset by peer', 'ClickHouse: code 210 DB::NetException connection reset (903)', NOW() - INTERVAL 85 MINUTE),
 (906, 903, 1, 'api',     3, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY + INTERVAL 40 SECOND, 40000, 'cancelled by operator', '任务已停用', NOW() - INTERVAL 9 DAY);

-- =============================================================
-- 5. db_dashboard：看板 + 组件
-- =============================================================
USE db_dashboard;

DELETE FROM dashboard WHERE id BETWEEN 900 AND 949;
INSERT INTO dashboard (id, tenant_id, name, description, config_json, layout_json, cover_url, status, view_count, like_count, is_template, create_time, update_time, deleted) VALUES
 (901, 1, '经营总览', '集团层面的日/周/月经营指标看板', '{"width":1200,"cols":24,"rowHeight":30,"theme":"light"}', '{"widgets":[{"i":"w1","x":0,"y":0,"w":8,"h":4},{"i":"w2","x":8,"y":0,"w":16,"h":4}]}', NULL, 1, 1286, 42, 0, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 2 HOUR, 0),
 (902, 1, '供应链监控', '库存周转与履约时效', '{"width":1200,"cols":24,"rowHeight":30,"theme":"dark"}', '{"widgets":[{"i":"w3","x":0,"y":0,"w":12,"h":6}]}', NULL, 1, 431, 12, 0, NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 1 DAY, 0),
 (903, 1, '市场活动复盘', '渠道投放与转化漏斗（草稿）', '{"width":1200,"cols":24,"rowHeight":30}', '{}', NULL, 0, 0, 0, 0, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY, 0),
 (904, 1, '通用经营看板模板', '平台内置模板，可从列表「从模板新建」使用', '{"width":1200,"cols":24,"rowHeight":30}', '{"widgets":[]}', NULL, 1, 3021, 168, 1, NOW() - INTERVAL 60 DAY, NOW() - INTERVAL 60 DAY, 0);

DELETE FROM dashboard_widget WHERE id BETWEEN 900 AND 949;
INSERT INTO dashboard_widget (id, dashboard_id, widget_type, title, config_json, data_config_json, position_json, sort, create_time, update_time) VALUES
 (901, 901, 'statCard', '今日销售额', '{"unit":"元","precision":2,"compare":"yoy"}', '{"datasetId":901,"metric":"amount","aggregation":"sum"}', '{"x":0,"y":0,"w":8,"h":4}', 1, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY),
 (902, 901, 'bar', '各大区销售额', '{"stack":false,"showLabel":true,"color":["#3aa2ff","#4dd6a8","#ffb547"]}', '{"datasetId":901,"dimensions":["region"],"measures":[{"field":"amount","aggregation":"sum"}]}', '{"x":8,"y":0,"w":16,"h":4}', 2, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY),
 (903, 901, 'table', '近 7 日明细', '{"pageSize":10,"striped":true}', '{"datasetId":901,"columns":["stat_date","region","amount","order_count"]}', '{"x":0,"y":4,"w":24,"h":8}', 3, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 19 DAY),
 (904, 902, 'line', '履约时效趋势', '{"smooth":true,"area":true}', '{"datasetId":903,"dimensions":["created_at"],"measures":[{"field":"pay_amount","aggregation":"avg"}]}', '{"x":0,"y":0,"w":12,"h":6}', 1, NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 14 DAY),
 (905, 902, 'pie', '库存结构', '{"roseType":false,"innerRadius":"45%"}', '{"datasetId":903,"dimensions":["status"],"measures":[{"field":"pay_amount","aggregation":"sum"}]}', '{"x":12,"y":0,"w":12,"h":6}', 2, NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 14 DAY),
 (906, 903, 'text', '活动说明', '{"content":"618 大促渠道复盘（草稿）","fontSize":16}', '{}', '{"x":0,"y":0,"w":24,"h":2}', 1, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY);

-- =============================================================
-- 6. db_alert：规则 / 事件 / 通知渠道 / 通知日志
--    ⚠️ `condition` 是 MySQL 关键字，必须反引号。
-- =============================================================
USE db_alert;

DELETE FROM alert_rule WHERE id BETWEEN 900 AND 949;
INSERT INTO alert_rule (id, name, description, type, datasource_id, metric_expression, `condition`, threshold, duration, severity, notify_channels, enabled, tenant_id, create_time, update_time, deleted) VALUES
 (901, 'CPU 使用率过高', '服务节点 CPU 连续超阈值', 'THRESHOLD', 903, 'cpu_usage_percent', 'GT', 85.0000, 300, 'CRITICAL', '["EMAIL","DINGTALK"]', 1, 1, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY, 0),
 (902, '接口 5xx 比率飙升', '网关 5 分钟内 5xx 占比', 'THRESHOLD', 903, 'http_5xx_ratio', 'GTE', 0.0500, 300, 'CRITICAL', '["EMAIL","SMS"]', 1, 1, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 2 DAY, 0),
 (903, '销售额环比下滑', '日销售额较昨日下降超 30%', 'DERIVATIVE', 901, 'sum(amount)', 'LT', -0.3000, 0, 'WARNING', '["DINGTALK"]', 1, 1, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY, 0),
 (904, 'ETL 任务失败', '关键入仓任务连续失败', 'COMPOSITE', 901, 'etl_failed_count', 'GTE', 2.0000, 600, 'WARNING', '["EMAIL"]', 0, 1, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 3 DAY, 0),
 (905, '磁盘水位告警', '数据盘使用率超 90%', 'THRESHOLD', 903, 'disk_used_percent', 'GT', 90.0000, 600, 'INFO', '["SMS"]', 1, 1, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 0);

DELETE FROM notify_channel WHERE id BETWEEN 900 AND 949;
INSERT INTO notify_channel (id, name, type, config, enabled, tenant_id, create_time, update_time) VALUES
 (901, '运维邮箱组', 'EMAIL', '{"smtp":"smtp.example.com","port":465,"from":"alert@example.com","to":["ops@example.com","data@example.com"],"ssl":true}', 1, 1, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 15 DAY),
 (902, '钉钉值班群', 'DINGTALK', '{"webhook":"https://oapi.dingtalk.com/robot/send?access_token=***","atAll":false,"mobiles":["13800000901"]}', 1, 1, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 4 DAY),
 (903, '短信通道', 'SMS', '{"provider":"aliyun","signName":"DataViz","templateCode":"SMS_DEMO","receivers":["13800000901"]}', 0, 1, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY);

DELETE FROM alert_event WHERE id BETWEEN 900 AND 949;
INSERT INTO alert_event (id, rule_id, rule_name, trigger_value, severity, status, message, notified_at, resolved_at, tenant_id, create_time) VALUES
 (901, 901, 'CPU 使用率过高', 96.4000, 'CRITICAL', 'PENDING', '实例 ch-node-1 cpu_usage_percent=96.4%，持续 5 分钟', NOW() - INTERVAL 18 MINUTE, NULL, 1, NOW() - INTERVAL 18 MINUTE),
 (902, 901, 'CPU 使用率过高', 89.2000, 'CRITICAL', 'ACKNOWLEDGED', '实例 ch-node-2 cpu_usage_percent=89.2%', NOW() - INTERVAL 2 HOUR, NULL, 1, NOW() - INTERVAL 2 HOUR),
 (903, 902, '接口 5xx 比率飙升', 0.0812, 'CRITICAL', 'RESOLVED', '网关 5xx 占比 8.12%，主要来源 /api/analysis/query', NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 4 HOUR + INTERVAL 40 MINUTE, 1, NOW() - INTERVAL 5 HOUR),
 (904, 903, '销售额环比下滑', -0.4200, 'WARNING', 'PENDING', '昨日销售额环比下降 42.0%，其华东区贡献最大', NOW() - INTERVAL 7 HOUR, NULL, 1, NOW() - INTERVAL 7 HOUR),
 (905, 903, '销售额环比下滑', -0.3100, 'WARNING', 'RESOLVED', '昨日销售额环比下降 31.0%', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 3 HOUR, 1, NOW() - INTERVAL 2 DAY),
 (906, 905, '磁盘水位告警', 91.7000, 'INFO', 'ACKNOWLEDGED', '数据盘 /data 使用率 91.7%', NOW() - INTERVAL 3 DAY, NULL, 1, NOW() - INTERVAL 3 DAY),
 (907, 904, 'ETL 任务失败', 2.0000, 'WARNING', 'RESOLVED', '任务「门店主数据同步」连续失败 2 次', NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY + INTERVAL 20 MINUTE, 1, NOW() - INTERVAL 9 DAY),
 (908, 901, 'CPU 使用率过高', 100.0000, 'CRITICAL', 'PENDING', 'ClickHouse 查询倾斜导致 CPU 打满', NOW() - INTERVAL 3 MINUTE, NULL, 1, NOW() - INTERVAL 3 MINUTE);

DELETE FROM alert_notify_log WHERE id BETWEEN 900 AND 949;
INSERT INTO alert_notify_log (id, event_id, channel, recipient, status, error_message, create_time) VALUES
 (901, 901, 'DINGTALK', 'https://oapi.dingtalk.com/robot/send?access_token=***', 'SUCCESS', NULL, NOW() - INTERVAL 18 MINUTE),
 (902, 901, 'EMAIL', 'ops@example.com', 'SUCCESS', NULL, NOW() - INTERVAL 18 MINUTE),
 (903, 902, 'DINGTALK', 'https://oapi.dingtalk.com/robot/send?access_token=***', 'FAILED', '403 Forbidden: robot is disabled', NOW() - INTERVAL 2 HOUR),
 (904, 903, 'SMS', '13800000901', 'SUCCESS', NULL, NOW() - INTERVAL 5 HOUR),
 (905, 904, 'DINGTALK', 'https://oapi.dingtalk.com/robot/send?access_token=***', 'SUCCESS', NULL, NOW() - INTERVAL 7 HOUR),
 (906, 907, 'EMAIL', 'data@example.com', 'FAILED', 'SMTP connect timeout after 10s', NOW() - INTERVAL 9 DAY);

-- =============================================================
-- 7. db_analysis：查询历史
--    QueryHistory entity 映射的是 datasource_id / `sql` / execution_time / status(String)（schema-completion 补列），
--    旧列 dataset_id / query_json / sql_text / duration_ms 保留但不再是接口读写口径；
--    user_id 为 varchar（存用户名），status 为 varchar（SUCCESS/FAILED）。
-- =============================================================
USE db_analysis;

-- 7.0 演示表视图：analysis-service 的 QueryServiceImpl 用自身 JdbcTemplate 执行裸 SQL（只连 db_analysis），
--     而 demo_* 物理表建在 db_model。这里以视图方式暴露，保证「自助分析」页能查出真实数据。
CREATE OR REPLACE VIEW demo_sales_daily AS SELECT * FROM db_model.demo_sales_daily;
CREATE OR REPLACE VIEW demo_user_behavior AS SELECT * FROM db_model.demo_user_behavior;
CREATE OR REPLACE VIEW demo_orders AS SELECT * FROM db_model.demo_orders;

DELETE FROM query_history WHERE id BETWEEN 900 AND 949;
INSERT INTO query_history (id, tenant_id, user_id, datasource_id, `sql`, status, execution_time, row_count, dataset_id, query_json, sql_text, duration_ms, create_time) VALUES
 (901, 1, 'admin', 901, 'SELECT region, SUM(amount) AS amount FROM demo_sales_daily GROUP BY region ORDER BY amount DESC', 'SUCCESS', 148, 5,
  901, '{"datasourceId":901,"maxRows":1000,"timeout":30}', 'SELECT region, SUM(amount) FROM demo_sales_daily GROUP BY region', 148, NOW() - INTERVAL 26 MINUTE),
 (902, 1, 'admin', 901, 'SELECT stat_date, SUM(amount) AS amount FROM demo_sales_daily GROUP BY stat_date ORDER BY stat_date', 'SUCCESS', 233, 30,
  901, '{"datasourceId":901,"maxRows":2000}', 'SELECT stat_date, SUM(amount) FROM demo_sales_daily GROUP BY stat_date', 233, NOW() - INTERVAL 2 HOUR),
 (903, 1, 'analyst', 901, 'SELECT event_type, AVG(duration_ms) AS avg_duration FROM demo_user_behavior GROUP BY event_type', 'SUCCESS', 96, 4,
  902, '{"datasourceId":901,"maxRows":1000}', 'SELECT event_type, AVG(duration_ms) FROM demo_user_behavior GROUP BY event_type', 96, NOW() - INTERVAL 5 HOUR),
 (904, 1, 'analyst', 902, 'SELECT status, SUM(pay_amount) FROM demo_orders GROUP BY status', 'FAILED', 12, 0,
  903, '{"datasourceId":902,"maxRows":1000}', 'SELECT status, SUM(pay_amount) FROM demo_orders GROUP BY status', 12, NOW() - INTERVAL 1 DAY),
 (905, 1, 'admin', 901, 'SELECT category, SUM(order_count) AS cnt FROM demo_sales_daily GROUP BY category LIMIT 50', 'SUCCESS', 41, 3,
  901, '{"datasourceId":901,"maxRows":50}', 'SELECT category, SUM(order_count) FROM demo_sales_daily LIMIT 50', 41, NOW() - INTERVAL 2 DAY);

-- =============================================================
-- 8. db_collab：评论 / 订阅（mentions_json 为 json 列 = 字符串数组）
-- =============================================================
USE db_collab;

DELETE FROM collab_comment WHERE id BETWEEN 900 AND 949;
INSERT INTO collab_comment (id, tenant_id, target_type, target_id, content, parent_id, mentions_json, create_time, deleted) VALUES
 (901, 1, 'screen', 901, '标题区文字换成主品牌色，白底更醒目。', NULL, NULL, NOW() - INTERVAL 3 HOUR, 0),
 (902, 1, 'screen', 901, '同意，另外飞线的默认数据用演示城市即可。', 901, '["admin"]', NOW() - INTERVAL 2 HOUR, 0),
 (903, 1, 'dashboard', 901, '经营总览缺一个同比指标卡，麻烦补一下。', NULL, '["analyst01"]', NOW() - INTERVAL 1 DAY, 0),
 (904, 1, 'dashboard', 902, '供应链监控的履约时效改成 P95。', NULL, NULL, NOW() - INTERVAL 3 DAY, 0),
 (905, 1, 'screen', 902, '移动端 375 宽度下图表标签重叠，建议隐藏次要维度。', NULL, NULL, NOW() - INTERVAL 4 DAY, 0),
 (906, 1, 'dataset', 901, '销售额口径要确认是否含退款。', NULL, '["admin","analyst01"]', NOW() - INTERVAL 6 DAY, 0);

DELETE FROM collab_subscription WHERE id BETWEEN 900 AND 949;
INSERT INTO collab_subscription (id, tenant_id, user_id, target_type, target_id, notify_types, create_time) VALUES
 (901, 1, 1, 'screen', 901, '["COMMENT","PUBLISH"]', NOW() - INTERVAL 5 DAY),
 (902, 1, 1, 'dashboard', 901, '["ALL"]', NOW() - INTERVAL 5 DAY),
 (903, 1, 1, 'dataset', 901, '["COMMENT","CHANGE"]', NOW() - INTERVAL 2 DAY);

-- =============================================================
-- 9. db_monitor：服务实例 / 指标 / 告警 / 操作日志（当前无前端页面，供接口测试）
-- =============================================================
USE db_monitor;

DELETE FROM service_instance WHERE id BETWEEN 900 AND 949;
INSERT INTO service_instance (id, service_name, instance_id, host, port, status, metadata, last_heartbeat, create_time, update_time) VALUES
 (901, 'gateway-service', 'gw-8080-01', 'localhost', 8080, 'UP', '{"java":"17","zone":"cn-east-1a","version":"1.0.0"}', NOW() - INTERVAL 20 SECOND, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 20 SECOND),
 (902, 'user-service', 'user-8082-01', 'localhost', 8082, 'UP', '{"zone":"cn-east-1a","version":"1.0.0"}', NOW() - INTERVAL 25 SECOND, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 25 SECOND),
 (903, 'screen-service', 'screen-8088-01', 'localhost', 8088, 'UP', '{"zone":"cn-east-1a","version":"1.0.0"}', NOW() - INTERVAL 15 SECOND, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 15 SECOND),
 (904, 'analysis-service', 'analysis-8086-01', 'localhost', 8086, 'STARTING', '{"zone":"cn-east-1b"}', NOW() - INTERVAL 40 SECOND, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 40 SECOND),
 (905, 'alert-service', 'alert-8090-01', 'localhost', 8090, 'DOWN', '{"zone":"cn-east-1b","reason":"heartbeat timeout > 60s"}', NOW() - INTERVAL 8 MINUTE, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 8 MINUTE),
 (906, 'file-service', 'file-8094-01', 'localhost', 8094, 'UP', '{"storage":"local","basePath":"./uploads"}', NOW() - INTERVAL 18 SECOND, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 18 SECOND);

DELETE FROM monitor_metric WHERE id BETWEEN 900 AND 999;
INSERT INTO monitor_metric (id, service_name, instance_id, metric_name, metric_value, metric_type, tags, timestamp) VALUES
 (901, 'gateway-service', 'gw-8080-01', 'cpu_usage_percent', 32.500000, 'GAUGE', '{"env":"dev","zone":"cn-east-1a"}', NOW() - INTERVAL 5 MINUTE),
 (902, 'gateway-service', 'gw-8080-01', 'cpu_usage_percent', 41.200000, 'GAUGE', '{"env":"dev","zone":"cn-east-1a"}', NOW() - INTERVAL 4 MINUTE),
 (903, 'gateway-service', 'gw-8080-01', 'cpu_usage_percent', 37.800000, 'GAUGE', '{"env":"dev","zone":"cn-east-1a"}', NOW() - INTERVAL 3 MINUTE),
 (904, 'gateway-service', 'gw-8080-01', 'cpu_usage_percent', 88.400000, 'GAUGE', '{"env":"dev","zone":"cn-east-1a"}', NOW() - INTERVAL 2 MINUTE),
 (905, 'gateway-service', 'gw-8080-01', 'cpu_usage_percent', 64.100000, 'GAUGE', '{"env":"dev","zone":"cn-east-1a"}', NOW() - INTERVAL 1 MINUTE),
 (906, 'gateway-service', 'gw-8080-01', 'http_requests_total', 128450.000000, 'COUNTER', '{"code":"2xx"}', NOW() - INTERVAL 1 MINUTE),
 (907, 'gateway-service', 'gw-8080-01', 'http_5xx_ratio', 0.004200, 'GAUGE', '{"window":"5m"}', NOW() - INTERVAL 1 MINUTE),
 (908, 'screen-service', 'screen-8088-01', 'jvm_heap_used_bytes', 412314624.000000, 'GAUGE', '{"pool":"old"}', NOW() - INTERVAL 30 SECOND),
 (909, 'screen-service', 'screen-8088-01', 'api_duration_p95_ms', 86.000000, 'HISTOGRAM', '{"api":"/api/screen/list"}', NOW() - INTERVAL 30 SECOND),
 (910, 'screen-service', 'screen-8088-01', 'api_duration_p95_ms', 412.000000, 'HISTOGRAM', '{"api":"/api/screen"}', NOW() - INTERVAL 1 MINUTE),
 (911, 'analysis-service', 'analysis-8086-01', 'api_duration_p95_ms', 1830.000000, 'HISTOGRAM', '{"api":"/api/analysis/query"}', NOW() - INTERVAL 1 MINUTE),
 (912, 'analysis-service', 'analysis-8086-01', 'error_rate_percent', 12.500000, 'GAUGE', '{"api":"/api/analysis/query"}', NOW() - INTERVAL 1 MINUTE),
 (913, 'user-service', 'user-8082-01', 'cpu_usage_percent', 18.300000, 'GAUGE', '{"env":"dev"}', NOW() - INTERVAL 45 SECOND),
 (914, 'file-service', 'file-8094-01', 'disk_used_percent', 91.700000, 'GAUGE', '{"mount":"/data"}', NOW() - INTERVAL 3 DAY);

DELETE FROM monitor_alert WHERE id BETWEEN 900 AND 949;
INSERT INTO monitor_alert (id, service_name, metric_name, `condition`, threshold, message, status, create_time, resolved_time) VALUES
 (901, 'gateway-service', 'cpu_usage_percent', '>', 85.000000, 'gateway-service CPU 88.4% 连续 5 分钟超阈值', 'ACTIVE', NOW() - INTERVAL 2 MINUTE, NULL),
 (902, 'file-service', 'disk_used_percent', '>', 90.000000, '/data 使用率 91.7%，请清理上传目录', 'ACTIVE', NOW() - INTERVAL 3 DAY, NULL),
 (903, 'alert-service', 'instance_up', '<', 1.000000, 'alert-service 心跳超时，实例 DOWN', 'RESOLVED', NOW() - INTERVAL 8 MINUTE, NOW() - INTERVAL 1 MINUTE),
 (904, 'analysis-service', 'api_duration_p95_ms', '>', 1000.000000, '/api/analysis/query P95 = 1830ms', 'ACKNOWLEDGED', NOW() - INTERVAL 1 HOUR, NULL);

DELETE FROM audit_log WHERE id BETWEEN 900 AND 949;
INSERT INTO audit_log (id, tenant_id, user_id, username, module, action, target_type, target_id, detail, ip, user_agent, duration, status, create_time) VALUES
 (901, 1, 1, 'admin', 'screen', 'publish', 'screen', '901', '{"result":"ok","shareToken":"smoke0921aaaa"}', '10.20.30.41', 'Chrome/139', 35, 1, NOW() - INTERVAL 23 HOUR),
 (902, 1, 1, 'admin', 'datasource', 'test-connection', 'datasource', '901', '{"result":"ok","costMs":24}', '10.20.30.42', 'Chrome/139', 24, 1, NOW() - INTERVAL 2 DAY),
 (903, 1, 2, 'ops', 'user', 'login', 'session', 's-9021', '{"reason":"账号被停用"}', '10.20.30.77', 'Safari/605', 12, 0, NOW() - INTERVAL 5 HOUR),
 (904, 1, 1, 'admin', 'etl', 'run', 'etl_task', '901', '{"instanceId":901}', '10.20.30.49', 'Chrome/139', 182450, 1, NOW() - INTERVAL 6 HOUR),
 (905, 1, 1, 'admin', 'alert', 'resolve', 'alert_event', '903', '{"note":"网关限流已恢复"}', '10.20.30.44', 'Chrome/139', 22, 1, NOW() - INTERVAL 5 HOUR),
 (906, 1, 1, 'admin', 'file', 'upload', 'file', '901', '{"fileName":"cover-sales.png","size":68412}', '10.20.30.41', 'Chrome/139', 340, 1, NOW() - INTERVAL 1 DAY);

-- =============================================================
-- 10. db_file：文件元数据
--     ⚠️ 只有元数据行；/api/file/view/{id} 要返回字节还依赖磁盘/MinIO 上的真实对象，
--        因此本页不把这些 id 当封面用（避免图片 404）。
-- =============================================================
USE db_file;

DELETE FROM file_info WHERE id BETWEEN 900 AND 949;
INSERT INTO file_info (id, tenant_id, file_name, file_path, file_size, file_type, bucket, create_by, create_time) VALUES
 (901, 1, 'cover-sales.png', 'screen/2026/09/901-cover-sales.png', 68412, 'image/png', 'dataviz', '1', NOW() - INTERVAL 1 DAY),
 (902, 1, 'bg-tech-grid.png', 'screen/2026/09/902-bg-tech-grid.png', 142308, 'image/png', 'dataviz', '1', NOW() - INTERVAL 3 DAY),
 (903, 1, 'sales-daily-export.csv', 'export/2026/09/903-sales-daily-export.csv', 20480, 'text/csv', 'dataviz', '1', NOW() - INTERVAL 6 HOUR);

-- =============================================================
-- 11. db_screen：大屏（列表页 + 免登分享 + 三端变体）
--     契约要点：
--       · width/height 必须落在 config_json 里（表无独立列）
--       · status: 0=draft 1=published 2=archived；分享链路要求 status=1
--       · layers[].componentIds 与 components[].id 必须一一对应
--       · 画布底色锁白（CANVAS_BG_COLOR=#ffffff），组件文字用深墨色保证对比度
-- =============================================================
USE db_screen;

DELETE FROM screen WHERE id BETWEEN 900 AND 949;
INSERT INTO screen (id, tenant_id, name, description, config_json, components_json, variants_json, cover_url, status, adapt_mode, view_count, share_token, share_expire_time, create_time, update_time, deleted) VALUES
 (901, 1, '销售实时监控大屏', '已发布，可作为分享链接与嵌入示例',
  '{"width":1920,"height":1080,"adaptationMode":"scale","backgroundColor":"#ffffff","gridSize":10,"gridSnap":true,"layers":[{"id":"ly_title","name":"标题组","locked":false,"visible":true,"componentIds":["comp_title","comp_kpi_1","comp_kpi_2"]},{"id":"ly_charts","name":"图表组","locked":false,"visible":true,"componentIds":["comp_bar_region","comp_line_trend","comp_pie_category"]}]}',
  '[{"id":"comp_title","name":"主标题","type":"text","x":40,"y":24,"w":600,"h":48,"zIndex":1,"visible":true,"locked":false,"props":{"text":"销售实时监控中心","color":"#1f2d3d","fontSize":"28px","fontWeight":"600"},"request":{"sourceType":"static","staticData":{}}},{"id":"comp_kpi_1","name":"今日销售额","type":"statCard","x":700,"y":24,"w":280,"h":90,"zIndex":2,"visible":true,"locked":false,"props":{"title":"今日销售额","unit":"元","precision":0,"color":"#26323f","accentColor":"#1f6feb"},"request":{"sourceType":"static","staticData":{"rows":[{"value":1286430}]}}},{"id":"comp_kpi_2","name":"今日订单量","type":"statCard","x":1000,"y":24,"w":280,"h":90,"zIndex":3,"visible":true,"locked":false,"props":{"title":"今日订单量","unit":"单","precision":0,"color":"#26323f","accentColor":"#0f9d58"},"request":{"sourceType":"static","staticData":{"rows":[{"value":8426}]}}},{"id":"comp_bar_region","name":"各大区销售额","type":"chart","chartPreset":"bar","x":40,"y":140,"w":900,"h":420,"zIndex":4,"visible":true,"locked":false,"props":{"title":"各大区销售额","color":["#3aa2ff","#4dd6a8","#ffb547"]},"request":{"sourceType":"static","staticData":{"rows":[{"category":"华东","amount":428000},{"category":"华南","amount":312000},{"category":"华北","amount":286000},{"category":"西南","amount":148000},{"category":"东北","amount":76000},{"category":"西北","amount":36430}]}}},{"id":"comp_line_trend","name":"近 7 日趋势","type":"chart","chartPreset":"lineArea","x":960,"y":140,"w":920,"h":420,"zIndex":5,"visible":true,"locked":false,"props":{"title":"近 7 日销售趋势","color":["#1f6feb"]},"request":{"sourceType":"static","staticData":{"rows":[{"date":"09-14","amount":98000},{"date":"09-15","amount":112000},{"date":"09-16","amount":104500},{"date":"09-17","amount":131200},{"date":"09-18","amount":127800},{"date":"09-19","amount":143600},{"date":"09-20","amount":128643}]}}},{"id":"comp_pie_category","name":"类目占比","type":"chart","chartPreset":"donut","x":40,"y":580,"w":1840,"h":460,"zIndex":6,"visible":true,"locked":false,"props":{"title":"商品类目销售额占比","color":["#3aa2ff","#4dd6a8","#ffb547","#ff7875","#9254de"]},"request":{"sourceType":"static","staticData":{"rows":[{"category":"家用电器","amount":468000},{"category":"服饰鞋包","amount":352000},{"category":"食品生鲜","amount":226000},{"category":"美妆个护","amount":148000},{"category":"其他","amount":92430}]}}}]',
  NULL, NULL, 1, 'scale', 142, 'smoke0921aaaa', NULL, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 1 HOUR, 0),
 (902, 1, '三端适配演示大屏', 'PC 1920×1080 / Mobile 750×1334 / Tablet 1024×768 变体齐备',
  '{"width":1920,"height":1080,"adaptationMode":"scale","backgroundColor":"#ffffff","gridSize":10,"gridSnap":true,"layers":[{"id":"ly_a","name":"标题","locked":false,"visible":true,"componentIds":["comp_title","comp_chart"]}]}',
  '[{"id":"comp_title","name":"标题","type":"text","x":40,"y":40,"w":520,"h":40,"zIndex":1,"visible":true,"locked":false,"props":{"text":"三端适配演示","color":"#1f2d3d","fontSize":"24px"},"request":{"sourceType":"static","staticData":{}}},{"id":"comp_chart","name":"柱图","type":"chart","chartPreset":"bar","x":40,"y":100,"w":1200,"h":520,"zIndex":2,"visible":true,"locked":false,"props":{"title":"季度营收","color":["#3aa2ff","#4dd6a8"]},"request":{"sourceType":"static","staticData":{"rows":[{"quarter":"Q1","revenue":820},{"quarter":"Q2","revenue":960},{"quarter":"Q3","revenue":1105},{"quarter":"Q4","revenue":1286}]}}}]',
  '{"mobile":{"config":{"width":750,"height":1334,"adaptationMode":"scale","backgroundColor":"#ffffff","layers":[{"id":"ly_a","name":"标题","locked":false,"visible":true,"componentIds":["comp_title","comp_chart"]}]},"components":[{"id":"comp_title","name":"标题","type":"text","x":20,"y":20,"w":340,"h":32,"zIndex":1,"visible":true,"locked":false,"props":{"text":"三端适配演示","color":"#1f2d3d","fontSize":"18px"},"request":{"sourceType":"static","staticData":{}}},{"id":"comp_chart","name":"柱图","type":"chart","chartPreset":"bar","x":20,"y":60,"w":710,"h":360,"zIndex":2,"visible":true,"locked":false,"props":{"title":"季度营收","color":["#3aa2ff","#4dd6a8"]},"request":{"sourceType":"static","staticData":{"rows":[{"quarter":"Q1","revenue":820},{"quarter":"Q2","revenue":960},{"quarter":"Q3","revenue":1105},{"quarter":"Q4","revenue":1286}]}}}]},"tablet":{"config":{"width":1024,"height":768,"adaptationMode":"scale","backgroundColor":"#ffffff","layers":[{"id":"ly_a","name":"标题","locked":false,"visible":true,"componentIds":["comp_title","comp_chart"]}]},"components":[{"id":"comp_title","name":"标题","type":"text","x":30,"y":24,"w":420,"h":36,"zIndex":1,"visible":true,"locked":false,"props":{"text":"三端适配演示","color":"#1f2d3d","fontSize":"20px"},"request":{"sourceType":"static","staticData":{}}},{"id":"comp_chart","name":"柱图","type":"chart","chartPreset":"bar","x":30,"y":70,"w":964,"h":520,"zIndex":2,"visible":true,"locked":false,"props":{"title":"季度营收","color":["#3aa2ff","#4dd6a8"]},"request":{"sourceType":"static","staticData":{"rows":[{"quarter":"Q1","revenue":820},{"quarter":"Q2","revenue":960},{"quarter":"Q3","revenue":1105},{"quarter":"Q4","revenue":1286}]}}}]}}',
  NULL, 0, 'scale', 7, NULL, NULL, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 2 DAY, 0),
 (903, 1, '地图与飞线演示', '已归档，验证 archived 状态过滤',
  '{"width":1920,"height":1080,"adaptationMode":"fixed-width","backgroundColor":"#ffffff","layers":[{"id":"ly_map","name":"地图","locked":false,"visible":true,"componentIds":["comp_map"]}]}',
  '[{"id":"comp_map","name":"中国地图","type":"map","x":360,"y":80,"w":1200,"h":900,"zIndex":1,"visible":true,"locked":false,"props":{"title":"业务分布","mapStyle":"dark"},"request":{"sourceType":"static","staticData":{"rows":[{"name":"广东","value":320},{"name":"江苏","value":275},{"name":"浙江","value":244},{"name":"北京","value":210},{"name":"四川","value":168}]}}}]',
  NULL, NULL, 2, 'fixed-width', 3, NULL, NULL, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 6 DAY, 0),
 (904, 1, '逻辑删除的待验证大屏', 'deleted=1，任何查询接口都不应返回它',
  '{"width":1920,"height":1080,"adaptationMode":"scale","backgroundColor":"#ffffff","layers":[]}',
  '[]', NULL, NULL, 0, 'scale', 0, NULL, NULL, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY, 1);

-- 把既有测试大屏 id=6（回归-拖拽缩放测试）发布并配一个分享口令，供免登分享接口测试
UPDATE screen SET status = 1, share_token = 'smoke0921bbbb', update_time = NOW() WHERE id = 6 AND deleted = 0;

-- 11.5 screen_component：组件独立存储表（/api/screen/component/** 依赖，schema-conflict-fix 新建）
--      与 901 号大屏 components_json 一一对应，供组件级 CRUD 接口测试
DELETE FROM screen_component WHERE screen_id BETWEEN 900 AND 949;
INSERT INTO screen_component (id, screen_id, component_type, title, config_json, data_config_json, position_json, refresh_interval) VALUES
 (901, 901, 'text',      '主标题',       '{"color":"#1f2d3d","fontSize":"28px","fontWeight":"600"}', '{"sourceType":"static"}',                    '{"x":40,"y":24,"w":600,"h":48}',   0),
 (902, 901, 'statCard',  '今日销售额',   '{"unit":"元","precision":0,"accentColor":"#1f6feb"}',      '{"sourceType":"static","value":1286430}',     '{"x":700,"y":24,"w":280,"h":90}',  60),
 (903, 901, 'statCard',  '今日订单量',   '{"unit":"单","precision":0,"accentColor":"#0f9d58"}',      '{"sourceType":"static","value":8426}',        '{"x":1000,"y":24,"w":280,"h":90}', 60),
 (904, 901, 'chart',     '各大区销售额', '{"chartPreset":"bar","color":["#3aa2ff","#4dd6a8"]}',       '{"sourceType":"dataset","datasetId":901}', '{"x":40,"y":140,"w":900,"h":420}',  300),
 (905, 901, 'chart',     '近 7 日趋势',  '{"chartPreset":"lineArea","color":["#1f6feb"]}',           '{"sourceType":"dataset","datasetId":901}', '{"x":960,"y":140,"w":920,"h":420}', 300),
 (906, 901, 'chart',     '类目占比',     '{"chartPreset":"donut"}',                                  '{"sourceType":"dataset","datasetId":901}', '{"x":40,"y":580,"w":1840,"h":460}', 300),
 (907, 902, 'text',      '标题',         '{"color":"#1f2d3d","fontSize":"24px"}',                    '{"sourceType":"static"}',                    '{"x":40,"y":40,"w":520,"h":40}',   0),
 (908, 902, 'chart',     '季度营收',     '{"chartPreset":"bar","color":["#3aa2ff","#4dd6a8"]}',       '{"sourceType":"dataset","datasetId":903}', '{"x":40,"y":100,"w":1200,"h":520}', 0);

-- 11.6 collab_approval：审批流水（/api/collab/approval/** 依赖，schema-conflict-fix 新建）
USE db_collab;
DELETE FROM collab_approval WHERE id BETWEEN 900 AND 949;
INSERT INTO collab_approval (id, tenant_id, target_type, target_id, applicant_id, approver_id, status, comment, create_time, approve_time) VALUES
 (901, 1, 'SCREEN',    901, 901, 1,   1, '内容核对无误，允许对外分享', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY + INTERVAL 40 MINUTE),
 (902, 1, 'DASHBOARD', 902, 902, 1,   2, '供应链口径需与财务确认，暂不通过', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY + INTERVAL 2 HOUR),
 (903, 1, 'SCREEN',    902, 903, NULL, 0, NULL, NOW() - INTERVAL 40 MINUTE, NULL),
 (904, 1, 'DASHBOARD', 901, 905, NULL, 0, NULL, NOW() - INTERVAL 10 MINUTE, NULL);

-- =============================================================
-- 12. db_user / db_auth：演示账号（不含可用密码，仅补齐列表展示）
-- =============================================================
USE db_user;

DELETE FROM sys_user WHERE id BETWEEN 900 AND 949;
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, phone, avatar, status, dept_id, create_time, update_time, deleted) VALUES
 (901, 1, 'analyst01', '', '陈静（分析师）', 'chenjing@example.com', '13900000901', NULL, 1, 2, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY, 0),
 (902, 1, 'analyst02', '', '刘洋（分析师）', 'liuyang@example.com', '13900000902', NULL, 1, 2, NOW() - INTERVAL 11 DAY, NOW() - INTERVAL 11 DAY, 0),
 (903, 1, 'designer01', '', '孙悦（设计）', 'sunyue@example.com', '13900000903', NULL, 1, 3, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY, 0),
 (904, 1, 'ops01', '', '何平（运维）', 'heping@example.com', '13900000904', NULL, 0, 4, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 1 DAY, 0),
 (905, 1, 'viewer01', '', '吴敏（只读）', 'wumin@example.com', '13900000905', NULL, 1, 4, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 0);

DELETE FROM sys_user_role WHERE id BETWEEN 900 AND 949;
INSERT INTO sys_user_role (id, user_id, role_id) VALUES
 (901, 901, 3), (902, 902, 3), (903, 903, 3), (904, 904, 2), (905, 905, 3);

-- =============================================================
-- 13. 校验（执行后逐条查看行数）
-- =============================================================
SELECT 'db_datasource.datasource' t, count(*) c FROM db_datasource.datasource WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_datasource.datasource_metadata', count(*) FROM db_datasource.datasource_metadata WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_model.model_dataset', count(*) FROM db_model.model_dataset WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_model.dataset(未使用,期望0)', count(*) FROM db_model.dataset WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_model.model_dimension', count(*) FROM db_model.model_dimension WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_model.model_metric', count(*) FROM db_model.model_metric WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_etl.etl_task', count(*) FROM db_etl.etl_task WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_etl.etl_task_instance', count(*) FROM db_etl.etl_task_instance WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_dashboard.dashboard', count(*) FROM db_dashboard.dashboard WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_dashboard.dashboard_widget', count(*) FROM db_dashboard.dashboard_widget WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_alert.alert_rule', count(*) FROM db_alert.alert_rule WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_alert.alert_event', count(*) FROM db_alert.alert_event WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_alert.notify_channel', count(*) FROM db_alert.notify_channel WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_alert.alert_notify_log', count(*) FROM db_alert.alert_notify_log WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_etl.etl_task_log', count(*) FROM db_etl.etl_task_log WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_screen.screen_component', count(*) FROM db_screen.screen_component WHERE screen_id BETWEEN 900 AND 949
UNION ALL SELECT 'db_collab.collab_approval', count(*) FROM db_collab.collab_approval WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_model.demo_sales_daily', count(*) FROM db_model.demo_sales_daily
UNION ALL SELECT 'db_model.demo_user_behavior', count(*) FROM db_model.demo_user_behavior
UNION ALL SELECT 'db_model.demo_orders', count(*) FROM db_model.demo_orders
UNION ALL SELECT 'db_analysis.demo_sales_daily(视图)', count(*) FROM db_analysis.demo_sales_daily
UNION ALL SELECT 'db_analysis.query_history', count(*) FROM db_analysis.query_history WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_collab.collab_comment', count(*) FROM db_collab.collab_comment WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_collab.collab_subscription', count(*) FROM db_collab.collab_subscription WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_monitor.service_instance', count(*) FROM db_monitor.service_instance WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_monitor.monitor_metric', count(*) FROM db_monitor.monitor_metric WHERE id BETWEEN 900 AND 999
UNION ALL SELECT 'db_monitor.monitor_alert', count(*) FROM db_monitor.monitor_alert WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_monitor.audit_log', count(*) FROM db_monitor.audit_log WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_file.file_info', count(*) FROM db_file.file_info WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_screen.screen', count(*) FROM db_screen.screen WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_admin.audit_log', count(*) FROM db_admin.audit_log WHERE id BETWEEN 9000 AND 9099
UNION ALL SELECT 'db_admin.sys_tenant', count(*) FROM db_admin.sys_tenant WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_admin.sys_config', count(*) FROM db_admin.sys_config WHERE id BETWEEN 900 AND 949
UNION ALL SELECT 'db_user.sys_user', count(*) FROM db_user.sys_user WHERE id BETWEEN 900 AND 949;

-- 中文编码修复结果抽查（应显示正常中文，而非 é»˜å¼± 形式）
SELECT id, source, label FROM (
    SELECT id, 'db_user.sys_role' AS source, role_name AS label FROM db_user.sys_role
    UNION ALL SELECT id, 'db_user.sys_dept', dept_name FROM db_user.sys_dept
    UNION ALL SELECT id, 'db_auth.sys_role', role_name FROM db_auth.sys_role
    UNION ALL SELECT id, 'db_admin.sys_tenant', name FROM db_admin.sys_tenant
) t
ORDER BY source, id;
