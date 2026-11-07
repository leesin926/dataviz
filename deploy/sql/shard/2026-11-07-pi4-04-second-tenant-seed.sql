-- PI4 · 给已存在的租户 903（haina-energy）铺分片表判据数据 —— 全是新行，不动租户 1 的任何数据
--
-- 为什么必须做这一步：整库今天只有 tenant_id=1 一个租户（sys_user 6 个账号全在租户 1），
-- 而 1 的桶位算出来是 ds0。也就是说"回填跑完、对账跑平"只证明了**一切都在同一个实例上原地没动**，
-- 跨实例路由与扇出归并这两件事一次都没被执行过。分库分表如果只有一条租户，它是装饰，不是架构。
--
-- 903 的桶位是实测值（见 ShardBucketParityTest 与 README 的表）：CONV(LEFT(MD5('903'),8),16,10)=2853608265 → %2=1 → ds1。
-- 所以 903 的行会被回填搬到第二个实例上，读侧的跨租户扇出才第一次真的打两个库。
--
-- 做法：用"复制表结构 → 挑源行 → 平移主键与租户与时间 → 插回"的列序无关写法。
-- 不逐列写 INSERT 的原因：alert_event 有 20 多列，逐列写必然漏（漏一列 NOT NULL 就是 500），
-- 而 CREATE TABLE ... LIKE + SELECT * 让列序由数据库自己保证。
--
-- 主键偏移是固定的，与现网 id 段不撞（跑完文件末尾的 @@INTACT 会证明租户 1 一行没动）：
--   alert_rule     +30     （现 901~913 → 931~943）
--   alert_event    +100000 （现 901~3543 → 100901~103543）
--   alert_notify_log +200000（现 901~4039 → 200901~204039）
--   sys_login_log  +10000  （现 1~71 → 10001~10071）
--
-- 幂等：每条 INSERT 都带"目标 id 段还空着才插"的守卫，重复跑不会堆第二份。
SET NAMES utf8mb4;

-- ========== 1. 告警规则：先给 903 三条自己的规则（配置表，不分片，永远在 ds0） ==========
DROP TABLE IF EXISTS db_alert.`_seed_rule`;
CREATE TABLE db_alert.`_seed_rule` LIKE db_alert.`alert_rule`;
INSERT INTO db_alert.`_seed_rule`
  SELECT * FROM db_alert.`alert_rule` WHERE tenant_id = 1 ORDER BY id LIMIT 3;
UPDATE db_alert.`_seed_rule`
   SET id = id + 30,
       tenant_id = 903,
       name = CONCAT(name, '（903 判据副本）')
 WHERE tenant_id <> 903;   -- 这里的条件要排除"已经改过的"，不是排除"租户 1"：那时副本行还带着 tenant_id=1

INSERT INTO db_alert.`alert_rule`
  SELECT s.* FROM db_alert.`_seed_rule` s
  LEFT JOIN db_alert.`alert_rule` r ON r.id = s.id
  WHERE r.id IS NULL;
DROP TABLE IF EXISTS db_alert.`_seed_rule`;

-- ========== 2. 告警事件：903 两个月各一批（202609 原月、202611 由 10 月平移而来） ==========
DROP TABLE IF EXISTS db_alert.`_seed_event`;
CREATE TABLE db_alert.`_seed_event` LIKE db_alert.`alert_event`;
-- 九月：直接取 9 月的源行，月份不动
INSERT INTO db_alert.`_seed_event`
  SELECT * FROM db_alert.`alert_event`
  WHERE tenant_id = 1 AND create_time >= '2026-09-01' AND create_time < '2026-10-01'
  ORDER BY id LIMIT 30;
-- 十一月：取 10 月的源行整体推一个月，让同一个租户横跨两张月表
INSERT INTO db_alert.`_seed_event`
  SELECT * FROM db_alert.`alert_event`
  WHERE tenant_id = 1 AND create_time >= '2026-10-01' AND create_time < '2026-10-31'
  ORDER BY id LIMIT 20;
UPDATE db_alert.`_seed_event`
   SET id = id + 100000,
       tenant_id = 903,
       rule_id = 931 + (id % 3),          -- 指向上面给 903 建的三条规则
       -- 只把 10 月那批推一个月落成 11 月；9 月那批保持原月，
       -- 于是同一租户横跨两张月表（这正是"分表"要证的判据）
       create_time = CASE WHEN create_time >= '2026-10-01' THEN DATE_ADD(create_time, INTERVAL 1 MONTH)
                          ELSE create_time END
 WHERE tenant_id <> 903;

INSERT INTO db_alert.`alert_event`
  SELECT s.* FROM db_alert.`_seed_event` s
  LEFT JOIN db_alert.`alert_event` e ON e.id = s.id
  WHERE e.id IS NULL;

-- ========== 3. 通知流水：跟着上面这批事件走（event_id 与 id 用同一批偏移） ==========
DROP TABLE IF EXISTS db_alert.`_seed_notify`;
CREATE TABLE db_alert.`_seed_notify` LIKE db_alert.`alert_notify_log`;
-- _seed_event 里的 id 已经 +100000，所以要搬回源事件 id 才能对上流水表
INSERT INTO db_alert.`_seed_notify`
  SELECT n.* FROM db_alert.`alert_notify_log` n
  JOIN db_alert.`_seed_event` e ON n.event_id = e.id - 100000
  WHERE n.tenant_id = 1;
UPDATE db_alert.`_seed_notify`
   SET id = id + 200000,
       event_id = event_id + 100000,
       tenant_id = 903
 WHERE tenant_id <> 903;

INSERT INTO db_alert.`alert_notify_log`
  SELECT s.* FROM db_alert.`_seed_notify` s
  LEFT JOIN db_alert.`alert_notify_log` n ON n.id = s.id
  WHERE n.id IS NULL;
DROP TABLE IF EXISTS db_alert.`_seed_notify`;
DROP TABLE IF EXISTS db_alert.`_seed_event`;

-- ========== 4. 登录日志：时间列是 login_time（不是 create_time），单独验一次分表列口径 ==========
DROP TABLE IF EXISTS db_auth.`_seed_login`;
CREATE TABLE db_auth.`_seed_login` LIKE db_auth.`sys_login_log`;
INSERT INTO db_auth.`_seed_login`
  SELECT * FROM db_auth.`sys_login_log` WHERE tenant_id = 1 ORDER BY id LIMIT 10;
UPDATE db_auth.`_seed_login`
   SET id = id + 10000,
       tenant_id = 903,
       login_time = DATE_ADD(login_time, INTERVAL 1 MONTH)
 WHERE tenant_id <> 903;

INSERT INTO db_auth.`sys_login_log`
  SELECT s.* FROM db_auth.`_seed_login` s
  LEFT JOIN db_auth.`sys_login_log` l ON l.id = s.id
  WHERE l.id IS NULL;
DROP TABLE IF EXISTS db_auth.`_seed_login`;

-- ========== 回读：判据数据到底铺没铺上、桶位到底对不对 ==========
SELECT CONCAT('@@SEED alert_rule@903 = ', COUNT(*)) FROM db_alert.`alert_rule` WHERE tenant_id = 903;
SELECT CONCAT('@@SEED alert_event@903 = ', COUNT(*), ' 月份=', GROUP_CONCAT(DISTINCT DATE_FORMAT(create_time, '%Y%m') ORDER BY DATE_FORMAT(create_time, '%Y%m')))
FROM db_alert.`alert_event` WHERE tenant_id = 903;
SELECT CONCAT('@@SEED alert_notify_log@903 = ', COUNT(*)) FROM db_alert.`alert_notify_log` WHERE tenant_id = 903;
SELECT CONCAT('@@SEED sys_login_log@903 = ', COUNT(*)) FROM db_auth.`sys_login_log` WHERE tenant_id = 903;

-- 关键一条：903 的行按 SQL 式子算出来的桶必须全是 1（=ds1），否则回填会把判据数据搬错实例。
-- 不分组、不带 GROUP BY —— 违例为 0 时也要出一行读数，"什么都没打印"不算通过。
SELECT CONCAT('@@BUCKET 903 = ',
              MOD(CAST(CONV(LEFT(MD5(CAST(903 AS CHAR)), 8), 16, 10) AS UNSIGNED), 2)) AS check_line;
SELECT CONCAT('@@BUCKET-CHECK 非 1 号桶的 903 行数 = ', COUNT(*)) AS check_line
FROM (
  SELECT 'alert_event' AS table_name FROM db_alert.`alert_event`
    WHERE tenant_id = 903 AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) <> 1
  UNION ALL
  SELECT 'alert_notify_log' FROM db_alert.`alert_notify_log`
    WHERE tenant_id = 903 AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) <> 1
  UNION ALL
  SELECT 'sys_login_log' FROM db_auth.`sys_login_log`
    WHERE tenant_id = 903 AND MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2) <> 1
) x;

-- 铺给 903 的事件必须指向 903 自己的规则：跨租户指过去就是 API-40 那一类（数据在，但按租户永远查不到 owner）
SELECT CONCAT('@@RULE-OWNER 903 事件指向非 903 规则的行数 = ', COUNT(*)) AS check_line
FROM db_alert.`alert_event` e
LEFT JOIN db_alert.`alert_rule` r ON r.id = e.rule_id AND r.tenant_id = 903
WHERE e.tenant_id = 903 AND r.id IS NULL;

-- 源数据没被动过：租户 1 的行数必须与铺数据之前完全一致
SELECT CONCAT('@@INTACT tenant1 alert_event = ', COUNT(*), ' notify = ',
              (SELECT COUNT(*) FROM db_alert.`alert_notify_log` WHERE tenant_id = 1),
              ' login = ', (SELECT COUNT(*) FROM db_auth.`sys_login_log` WHERE tenant_id = 1))
FROM db_alert.`alert_event` WHERE tenant_id = 1;
