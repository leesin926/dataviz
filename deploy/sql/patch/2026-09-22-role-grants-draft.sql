-- ============================================================
-- ⚠️⚠️ DRAFT —— 角色授权矩阵草案，**未经用户批准不得执行** ⚠️⚠️
-- 文件：deploy/sql/patch/2026-09-22-role-grants-draft.sql
-- 日期：2026-09-22   关联决策：D56   关联缺陷：API-16 / API-26
-- 为什么这是草案而不是一起执行：
--   权限码播种（2026-09-22-business-permission-codes.sql）只加码表行 + 补 super_admin 不变式，
--   执行后**行为变化为 0**；而"给 role 2 / role 3 发哪些码"是**业务授权决策** —— 它直接决定
--   "运维能不能删数据源""实习生能不能改告警规则"。我能给一个自洽的默认，但它不该由我拍。
--   所以这份按 D56 的两档粒度写成一版建议，**逐行可改**，改完再由用户批准执行。
-- 现状（决定"为什么必须有人批"）：`sys_role_permission` 里只有 role 1(super_admin) 有行，
--   role 2(admin) / role 3(user) 授权行数均为 **0** ⇒ AD 阶段的注解对现有 6 个演示账号
--   （901~905 + admin）当前**零影响**：非超管一律 403。这份脚本就是来改变这一点的。
-- 幂等：INSERT IGNORE + 按 permission_code 反查 id（不写死 id，码表 id 可被菜单管理改动）。
-- 执行后必须知道的两条边界（D55）：
--   ① **直接改库不会驱逐 Redis 会话快照** ⇒ 已登录的用户要**重新登录**才拿到新权限（走管理端接口改授权才会当场生效）。
--   ② 前端菜单显隐与后端强制现在同源（路线乙），但 `request.ts` 对 HTTP 403 **只 console.error 不弹提示**
--      ⇒ 无权限的用户会看到"页面能进、数字是 0、点了没反应"。这是**已知 UX 缺口**，不是本脚本的问题。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- role 2 = admin（租户管理员）：业务模块全读写；系统管理面只读；平台管理面只读
--   刻意**不给**：system:user:{add,edit,delete} / system:role:{add,edit,delete} /
--                system:menu:{add,edit,delete} / platform:write
--   理由：账号与菜单是"谁能进系统"的地基本，留给了超管；租户/许可证/系统配置/审计属平台运维。
-- ------------------------------------------------------------
INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 2, p.`id` FROM `db_user`.`sys_permission` p
WHERE p.`deleted` = 0
  AND p.`permission_code` IN (
    'datasource:read','datasource:write',
    'model:read','model:write',
    'analysis:read','analysis:write',
    'screen:read','screen:write',
    'dashboard:read','dashboard:write',
    'alert:read','alert:write',
    'etl:read','etl:write',
    'platform:read',
    'system:user:list','system:user:query',
    'system:role:list',
    'system:menu:list'
  );

-- ------------------------------------------------------------
-- role 3 = user（内容制作者）：能做大屏/仪表板/自助分析，数据侧一律只读
--   刻意**不给**：datasource/model/etl 的 write（凭据与管道属运维）、alert 的 write（告警规则影响值班）、
--                platform:*（平台面）、system:*（账号与菜单）
--   给 analysis:write 的理由：自助分析的"保存报表/导出"是写操作，不给就等于这个页面只能看不能存。
-- ------------------------------------------------------------
INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 3, p.`id` FROM `db_user`.`sys_permission` p
WHERE p.`deleted` = 0
  AND p.`permission_code` IN (
    'datasource:read',
    'model:read',
    'analysis:read','analysis:write',
    'screen:read','screen:write',
    'dashboard:read','dashboard:write',
    'alert:read',
    'etl:read'
  );

-- ============================================================
-- 校验（执行完自己看；在两份播种脚本都已执行的前提下，期望：super_admin 39、admin 19、user 10）
-- ============================================================
SELECT r.`role_code`, COUNT(rp.`permission_id`) AS granted
FROM `db_user`.`sys_role` r
LEFT JOIN `db_user`.`sys_role_permission` rp ON rp.`role_id` = r.`id`
WHERE r.`deleted` = 0
GROUP BY r.`role_code`;

SELECT r.`role_code`, GROUP_CONCAT(p.`permission_code` ORDER BY p.`permission_code` SEPARATOR ', ') AS codes
FROM `db_user`.`sys_role` r
INNER JOIN `db_user`.`sys_role_permission` rp ON rp.`role_id` = r.`id`
INNER JOIN `db_user`.`sys_permission` p ON p.`id` = rp.`permission_id` AND p.`deleted` = 0
WHERE r.`deleted` = 0 AND r.`id` IN (2, 3)
GROUP BY r.`role_code`;

-- 回退（只撤本轮发的码，不动 super_admin）：
-- DELETE FROM `db_user`.`sys_role_permission` WHERE `role_id` IN (2,3);
