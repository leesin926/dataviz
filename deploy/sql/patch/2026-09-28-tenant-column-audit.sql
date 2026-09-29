SET NAMES utf8mb4;

-- `GROUP_CONCAT` 默认上限 **1024 字节**，而 ②③ 两段生成的是 21 条 UNION 语句（约 3KB）⇒ 不开大就会被
-- **静默截断成一条语法错误的半截 SQL**（尾部少一个 `)` 而已，看不出是被截的）。这和 API-23 是同一族问题：
-- 截断本身不报错。会话级变量，不改任何数据。
SET SESSION group_concat_max_len = 200000;

-- 2026-09-28 · 只读体检：租户列与租户数据（**不修改任何数据**，配合 common-mybatis 的 IGNORE_TENANT_TABLES 收口）
--
-- 为什么要跑：`MybatisPlusConfig` 里的多租户拦截器**只要有会话就给每条 SQL 注入 `tenant_id = '<会话租户>'`**，
-- 判据是"这张表没有 tenant_id 列就别注入"。原清单只有 4 项、其中 3 项（sys_menu/sys_dict_type/sys_dict_data）
-- 在本仓库根本不存在，而真实库里 21 张表没有 tenant_id ⇒ **用户一登录，这些表的读写就 500**
-- （免登与内部调用不受影响，所以症状看起来像"某些页面坏了"而不是"底座坏了"）。
-- 下面三段：① 用真库校对代码里那份清单；② 找出会被过滤条件**静默藏起来**的行；③ 交叉核对。
-- 期望值全部写在每段的注释里，跑完把三段输出贴回来即可。

-- ============================================================
-- ① 真实库里**没有** tenant_id 列的表（这段的输出应与代码里的 IGNORE_TENANT_TABLES 一致）
--    期望：21 张 —— sys_permission / sys_user_role / sys_role_permission / sys_oauth_client /
--          sys_tenant / datasource_metadata / dashboard_widget / screen_component / etl_task_log /
--          schedule_job_log / alert_notify_log / openapi_log / ai_message / few_shot_example /
--          service_instance / service_health / monitor_metric / monitor_alert /
--          demo_sales_daily / demo_user_behavior / demo_orders
--    （demo_* 三张是纯数据表，不经 MyBatis 映射，所以**故意不在**代码清单里 —— 出现在输出里是对的）
--    若出现清单外的新表 ⇒ 那张表在登录态下也会 500，需要补进代码。
--    ⚠️ 2026-09-28 实跑更正（D72 ⑤）：实际输出是 **24 行而不是 21 行** —— 多出的 `zz_bak_*` 是 D30 的
--    **备份表**（本就没有 `tenant_id` 列、也不在任何 MyBatis 映射里，出现在这里是对的）。
--    所以这段**要按表名集合比对，不能按行数比对**：每做一次备份行数就 +N，而结论没变。
--    实跑结论：代码清单 18 项 = 真实无列集合 − `demo_*` − `zz_bak_*`，**零分叉**。
-- ============================================================
SELECT t.TABLE_SCHEMA AS `schema`, t.TABLE_NAME AS table_without_tenant_id
FROM information_schema.TABLES t
LEFT JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA = t.TABLE_SCHEMA
      AND c.TABLE_NAME = t.TABLE_NAME
      AND c.COLUMN_NAME = 'tenant_id'
WHERE t.TABLE_TYPE = 'BASE TABLE'
  AND t.TABLE_SCHEMA LIKE 'db\_%'
  AND c.COLUMN_NAME IS NULL
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME;

-- ============================================================
-- ② 有 tenant_id 列、但**列值是 NULL** 的行 —— 这些行会被注入条件静默过滤掉（界面上看不见，数据还在库里）。
--    期望：**0 行**。任何一张表出现 > 0 都是"界面少数据"的真正原因，**不是**分页或权限问题。
--    （MySQL 里 `tenant_id IS NULL` 求值为 1/0，SUM 即计数；不额外扫全表之外的东西。）
-- ============================================================
SELECT GROUP_CONCAT(
         CONCAT(
           'SELECT ''', c.TABLE_SCHEMA, '.', c.TABLE_NAME, ''' AS tbl, COUNT(*) AS rows_total, ',
           'SUM(`', c.TABLE_NAME, '`.`tenant_id` IS NULL) AS null_tenant_rows',
           ' FROM `', c.TABLE_SCHEMA, '`.`', c.TABLE_NAME, '`'
         )
         SEPARATOR '\nUNION ALL\n'
       ) AS copy_this_union
FROM information_schema.COLUMNS c
JOIN information_schema.TABLES t
  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
WHERE c.COLUMN_NAME = 'tenant_id'
  AND c.TABLE_SCHEMA LIKE 'db\_%';

-- ② 的说明：上面这条**只生成 SQL 文本**（一段 UNION ALL），把它的输出整体复制执行一次，
--          得到每张业务表的 `rows_total / null_tenant_rows`。分两步是因为 MySQL 不能动态展开表名。
--          注意 `information_schema` 的 `TABLE_ROWS` 是估算值，**不要用**它当行数（D35），
--          本脚本全部走真实 COUNT(*)。
-- 2026-09-28 实跑读数（D72 ⑥）：29 张表里 **28 张 `null_tenant_rows = 0`**，唯一例外 `db_model.dataset`
--          返回 **`NULL`** —— 该表 `COUNT(*) = 0`，**空集上 `SUM` 返回 `NULL` 而不是 0**，是 SQL 语义
--          不是脏数据。判据要区分"0"与"没有可判的行"。
-- 程序化执行（非人肉复制）的一条改法：`SET @sql = (上面的 GROUP_CONCAT); PREPARE s FROM @sql; EXECUTE s;
--          DEALLOCATE PREPARE s;` —— 客户端 batch 模式会把结果里的换行转义成字面 `\n`，贴回去必语法错。
--          两种跑法都要排除 `zz_bak_%`，否则备份表会混进业务表面板。

-- ============================================================
-- ③ 会话租户与实际数据租户是否同一档：admin 登录后看不到东西，先看这里。
--    期望：每张业务表的 tenant_id 取值集合**只有一档**（演示库通常是 1）；
--          若出现 1 与 2 混排，说明数据是"按租户 2 种的、用租户 1 的账号看" ⇒ 界面上必然缺一块，
--          这是**数据问题**，不要去改代码里的过滤条件。
--    2026-09-28 实跑读数：**28 张全部单档 `[1]`**（`db_model.dataset` 因空表返回 `NULL`）⇒ 数据从未按租户
--    2 种过，"admin 登录后看不到东西"这条**数据方向**的排查可以关闭。
-- ============================================================
SELECT GROUP_CONCAT(
         CONCAT(
           'SELECT ''', c.TABLE_SCHEMA, '.', c.TABLE_NAME, ''' AS tbl, ',
           'GROUP_CONCAT(DISTINCT CONCAT(''['', IFNULL(`', c.TABLE_NAME, '`.`tenant_id`, ''<NULL>''), '']'')) AS tenant_values',
           ' FROM `', c.TABLE_SCHEMA, '`.`', c.TABLE_NAME, '`'
         )
         SEPARATOR '\nUNION ALL\n'
       ) AS copy_this_union
FROM information_schema.COLUMNS c
JOIN information_schema.TABLES t
  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
WHERE c.COLUMN_NAME = 'tenant_id'
  AND c.TABLE_SCHEMA LIKE 'db\_%';

-- ============ 回滚 ============
-- 本脚本全程只读（SELECT），无需回滚。
