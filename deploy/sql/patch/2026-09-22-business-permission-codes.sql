-- ============================================================
-- 业务模块权限码播种 · AD 阶段（服务端权限强制第二轮）
-- 日期：2026-09-22   关联决策：D54（第一轮口径）/ D56（本轮粒度与对齐方向）   关联缺陷：API-16 / API-26
-- 用户拍板（2026-09-22）：粒度 = **模块 + 读写两档**；码表对齐 = **路线乙（前端 meta 向后端码对齐）**。
-- 背景：AC 第一轮只锁了 user-service 的 RBAC 管理面。业务模块（datasource/model/analysis/screen/
--       dashboard/alert/etl）与平台管理面（admin-service：租户/许可证/系统配置/审计）此前**零强制**
--       ⇒ 任何登录用户可调任意业务接口。本轮按两档给它们落注解，本脚本负责把 16 个码补进码表。
-- 命名与父节点：现有 sys_permission 里每个业务模块本来就是一个 type=1 的目录行（其 permission_code
--       就是模块名：screen/dashboard/analysis/model/etl/datasource/alert/monitor），本轮把
--       `{module}:read` / `{module}:write` 作为该目录的子节点（type=3）挂上去，**不发明新的顶层目录**。
--       平台管理面（admin-service）没有目录行，按既有约定挂在 `system`(id=1) 下面。
-- ⚠️ 两档粒度的**已知代价**（必须让使用方知道，否则会被当成 bug）：
--    ① "能看数据源列表" = "能看数据源凭据以外的全部元数据"，但**不能**改；"能改"只有一个 write 码。
--    ② 同一模块内无法再区分"能新建不能删除"—— 要那种粒度得走 CRUD 五码，用户本轮明确没选。
--    ③ `monitor` 模块本轮**不播种**（无前端入口，留下一轮），所以监控接口仍是零强制。
-- 归属：RBAC 表只在 `db_user`（D37/D39），故只写 `db_user`。
-- 执行：docker exec -i dataviz-mysql mysql -uroot -p<root口令> \
--         --default-character-set=utf8mb4 < deploy/sql/patch/2026-09-22-business-permission-codes.sql
--       （容器会话默认 latin1，首句必须 SET NAMES utf8mb4，见 D31；中文灌错不可逆）
-- 幂等：显式 id + uk_permission_code(permission_code, deleted)，可重复执行。
-- 影响面：本脚本**只加权限码行 + 给 super_admin 补授权**，不动 role 2/3 ⇒ 执行后行为变化为 0
--         （超级管理员走 role_code 短路，不看这张表；role 2/3 本来就一个码都没有）。
--         真正"谁能干什么"在 2026-09-22-role-grants-draft.sql，那份要单独批准。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 七个业务模块 + 平台管理面，各两档（read / write）
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`id`, `parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
VALUES
    (24, 6,  '数据源-查看',   'datasource:read',  3, '', '', 1),
    (25, 6,  '数据源-变更',   'datasource:write', 3, '', '', 2),
    (26, 8,  '数据建模-查看', 'model:read',       3, '', '', 1),
    (27, 8,  '数据建模-变更', 'model:write',      3, '', '', 2),
    (28, 9,  '自助分析-查看', 'analysis:read',    3, '', '', 1),
    (29, 9,  '自助分析-变更', 'analysis:write',   3, '', '', 2),
    (30, 11, '大屏-查看',     'screen:read',      3, '', '', 1),
    (31, 11, '大屏-变更',     'screen:write',     3, '', '', 2),
    (32, 10, '仪表板-查看',   'dashboard:read',   3, '', '', 1),
    (33, 10, '仪表板-变更',   'dashboard:write',  3, '', '', 2),
    (34, 12, '告警-查看',     'alert:read',       3, '', '', 1),
    (35, 12, '告警-变更',     'alert:write',      3, '', '', 2),
    (36, 7,  'ETL-查看',      'etl:read',         3, '', '', 1),
    (37, 7,  'ETL-变更',      'etl:write',        3, '', '', 2),
    (38, 1,  '平台管理-查看', 'platform:read',    3, '', '', 5),
    (39, 1,  '平台管理-变更', 'platform:write',   3, '', '', 6);

-- 2. 保住"super_admin = 全部权限"这个不变式（运行期靠 role_code 短路，库里少这几行这句话就是假的）
INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `db_user`.`sys_permission` p
WHERE p.`permission_code` IN ('datasource:read','datasource:write','model:read','model:write',
                              'analysis:read','analysis:write','screen:read','screen:write',
                              'dashboard:read','dashboard:write','alert:read','alert:write',
                              'etl:read','etl:write','platform:read','platform:write')
  AND p.`deleted` = 0;

-- 3. 清理上一轮遗留的口径问题：**管理端前端用的 8 个 admin:*:view 码后端从来不存在**（API-26）。
--    路线乙选定后，前端 meta 已改成后端真实码 ⇒ 这里不需要新增任何 `admin:*` 行。
--    （若将来有人再把 admin:xxx:view 写进前端 meta，PermissionInterceptor 不认识它，
--     症状是"菜单在、点进去前端路由守卫直接 403 页"，而不是接口 403 —— 两层要分清。）

-- ============================================================
-- 4. 校验（执行完自己看）
-- ============================================================
SELECT COUNT(*) AS permission_total FROM `db_user`.`sys_permission` WHERE `deleted` = 0;
SELECT p.`id`, p.`parent_id`, p.`permission_name`, p.`permission_code`
FROM `db_user`.`sys_permission` p
WHERE p.`id` BETWEEN 24 AND 39 ORDER BY p.`id`;
SELECT r.`role_code`, COUNT(rp.`permission_id`) AS granted
FROM `db_user`.`sys_role` r
LEFT JOIN `db_user`.`sys_role_permission` rp ON rp.`role_id` = r.`id`
WHERE r.`deleted` = 0
GROUP BY r.`role_code`;
