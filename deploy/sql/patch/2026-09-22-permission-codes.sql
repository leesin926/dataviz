-- ============================================================
-- 权限码补齐 · AC 阶段（服务端权限强制第一轮）
-- 日期：2026-09-22   关联决策：D54（AC 阶段口径）   关联缺陷：API-16 / API-26
-- 背景：common-security 的 PermissionInterceptor 早就注册在 16 个服务上，但全后端零端点带
--       @RequiresPermission ⇒ "任何登录用户可调任意业务接口"。本轮先给 user-service 的
--       三个 RBAC 控制器落注解（用户/角色/菜单管理），它们的读码（system:user:list、
--       system:role:list、system:menu:list）02-auth-schema.sql 里本来就有，
--       写码（…:add / …:edit / …:delete）沿用 system:user:* 的既有命名约定，本脚本负责补齐。
-- ⚠️ 为什么必须补：注解指向一个不存在的权限码，效果是"除 super_admin 外谁都进不去"——
--    这是安全的默认，但只有把行补进 sys_permission，管理端"菜单管理"里才看得见这三组按钮，
--    后续给用户角色授权才有东西可勾。缺行不等于故障，缺行等于"这件事永远没法授权"。
-- 归属：RBAC 表自 2026-09-21-rbac-single-owner.sql 起只在 db_user（D37/D39），故只写 db_user。
-- 执行：docker exec -i dataviz-mysql mysql -uroot -p<root口令> \
--         --default-character-set=utf8mb4 < deploy/sql/patch/2026-09-22-permission-codes.sql
--       （容器会话默认 latin1，第一句必须是 SET NAMES utf8mb4，见 D31；中文灌错不可逆）
-- 幂等：显式 id + uk_permission_code(permission_code, deleted)，可重复执行。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 角色管理三个写码（父节点 3 = system:role:list）
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`id`, `parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
VALUES
    (18, 3, '角色新增', 'system:role:add',    3, '', '', 1),
    (19, 3, '角色编辑', 'system:role:edit',   3, '', '', 2),
    (20, 3, '角色删除', 'system:role:delete', 3, '', '', 3);

-- 2. 菜单（权限）管理三个写码（父节点 4 = system:menu:list）
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`id`, `parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
VALUES
    (21, 4, '菜单新增', 'system:menu:add',    3, '', '', 1),
    (22, 4, '菜单编辑', 'system:menu:edit',   3, '', '', 2),
    (23, 4, '菜单删除', 'system:menu:delete', 3, '', '', 3);

-- 3. 超级管理员补齐"拥有所有权限"的不变式
--    （运行期靠 role_code=super_admin 直接短路，不读这张表；但库里少这几行，
--     "super_admin = 全部权限"这句话在数据上就是假的，一旦哪天去掉短路就会露馅。）
INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `db_user`.`sys_permission` p
WHERE p.`permission_code` IN ('system:role:add', 'system:role:edit', 'system:role:delete',
                              'system:menu:add', 'system:menu:edit', 'system:menu:delete')
  AND p.`deleted` = 0;

-- ============================================================
-- 4. 【等用户拍板，本脚本刻意不做】把 23 个码分给 role 2 / role 3
--    现状：sys_role_permission 里只有 role 1 有行 ⇒ role 2(admin)、role 3(user) 的权限集恒空。
--    也就是说本轮注解对**现有 6 个演示账号零影响**（唯一有权的 admin 就是 super_admin，走短路）。
--    要真正体现"分级"，需要先定两件事：
--      ① 业务模块（datasource/model/analysis/screen/dashboard/alert/etl）要不要各自的读写码
--         —— 现在只有 6 个"目录级"码（type=1），拿它们当接口权限用，等于"能看数据源菜单=能删数据源"；
--      ② 管理端 8 个页面用的 admin:*:view 码（apps/admin/src/router/index.ts）后端根本不存在
--         ⇒ 非 super_admin 现在连管理端页面都进不去（前端路由守卫先拦）。要么把 admin:*:view
--         补进 sys_permission，要么把前端 meta 改成后端既有码 —— 二者必须选一个，不能各说各话。
--    决策落成 SQL 时按同样的 INSERT IGNORE 形态追加，并记得改完授权要重新登录（或依赖本轮
--    新加的 LoginSessionEvictor 驱逐；改 sys_role_permission 只有走接口才驱逐，直改库不会）。
-- ============================================================

-- 5. 校验（执行完自己看）
SELECT COUNT(*) AS permission_total FROM `db_user`.`sys_permission` WHERE `deleted` = 0;
SELECT `permission_code` FROM `db_user`.`sys_permission`
WHERE `id` BETWEEN 18 AND 23 ORDER BY `id`;
SELECT r.`role_code`, COUNT(rp.`permission_id`) AS granted
FROM `db_user`.`sys_role` r
LEFT JOIN `db_user`.`sys_role_permission` rp ON rp.`role_id` = r.`id`
WHERE r.`deleted` = 0
GROUP BY r.`role_code`;
