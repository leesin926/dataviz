-- ============================================================
-- 部门管理权限码播种 · AJ 阶段（管理端新增"部门管理"页）
-- 日期：2026-09-23   关联决策：D54/D56（码表口径）/ D66（本轮：一页一码，不复用 system:user:*）
-- 关联缺陷：API-39（DeptController 5 个 handler 此前零强制）
-- 背景：`DeptController` 的五个接口此前**一条 @RequiresPermission 都没有**，而它现在有了前端入口
--       （apps/admin/src/views/DeptManage.vue）。注解指向的四个码必须先入码表，否则
--       "缺行"不是"没影响"而是"除 super_admin 外永远无法授权"（同 AC 轮 D54 的结论）。
-- 为什么是四个新码而不是复用 `system:user:*`（R1 台账里原计划的做法，本轮推翻）：
--       复用等于把"能管用户"和"能删部门"绑成同一件事 —— 以后任何只为"加个用户"而拿到
--       `system:user:edit` 的角色，会**顺带**拿到部门的增删改。码表多四行是一次性成本，
--       权限合并是长期成本。
-- ⚠️ 一条必须同时记住的联动（不这么做就会当场露馅）：
--       管理端**用户管理页左侧的部门树**打的也是 `/dept/tree`。所以
--       **被授予 `system:user:list` 的角色必须同时拿到 `system:dept:list`**，
--       否则那个页面的部门树会 403（本脚本按这条给 role 2 补了读码）。
-- 影响面：只加码表行 + 给 role 1/role 2 补授权。role 2 拿到的是**只有读码**，
--         所以执行后"谁能增删改部门"仍然只有 super_admin（走 role_code 短路）。
-- 执行：docker exec -i dataviz-mysql mysql -uroot -p<root口令> \
--         --default-character-set=utf8mb4 < deploy/sql/patch/2026-09-23-dept-permissions.sql
--       （容器会话默认 latin1，首句必须 SET NAMES utf8mb4，见 D31；中文灌错不可逆）
-- 幂等：不按 id 定位 —— 前几轮已有脚本用自增 id 插过探针行（2026-09-23-api30 的 `system:*`），
--       写死 id 40 会撞上它并被 INSERT IGNORE **静默跳过**，于是"码没种上"这件事看起来像种上了。
--       这里全部按 permission_code 反查，幂等性由 uk_permission_code(permission_code,deleted) 保证。
-- 执行后：直接改库**不会驱逐 Redis 会话快照**（D55 边界①）⇒ 已登录的用户要重新登录才拿到新码。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 读码 `system:dept:list`：type=2（菜单行），父节点反查 `system` 目录（不猜 id）
--    path 列只是这棵授权树上的展示字段：管理端侧边栏由 router 静态派生，菜单管理页的树才是读这张表
-- ------------------------------------------------------------
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`parent_id`, '部门管理', 'system:dept:list', 2, '/system/dept', 'Grid', 5
FROM `db_user`.`sys_permission` p
WHERE p.`permission_code` = 'system:user:list' AND p.`deleted` = 0;

-- 2. 三个写码，父节点 = 上一步那条读码（同样反查，不写死 id）
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT d.`id`, x.`permission_name`, x.`permission_code`, 3, '', '', x.`sort_order`
FROM (SELECT `id` FROM `db_user`.`sys_permission`
      WHERE `permission_code` = 'system:dept:list' AND `deleted` = 0 LIMIT 1) d
JOIN (SELECT '部门新增' AS `permission_name`, 'system:dept:add' AS `permission_code`, 1 AS `sort_order`
      UNION ALL SELECT '部门编辑', 'system:dept:edit', 2
      UNION ALL SELECT '部门删除', 'system:dept:delete', 3) x;

-- ------------------------------------------------------------
-- 3. role 1（super_admin）：补齐"super_admin = 全部权限"这个不变式
--    （运行期靠 role_code 短路，不读这张表；库里少这四行，这句话在数据上就是假的）
-- ------------------------------------------------------------
INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `db_user`.`sys_permission` p
WHERE p.`permission_code` IN ('system:dept:list', 'system:dept:add', 'system:dept:edit', 'system:dept:delete')
  AND p.`deleted` = 0;

-- 4. role 2（admin）：**只给读码**
--    给的理由不是"它该管部门"，而是它已经有 `system:user:list` ⇒ 它的用户管理页一定要读 /dept/tree；
--    不给写码的理由与 role 2 拿不到 `system:user:add`/`system:menu:add` 完全一致（账号与组织的地基留给超管）。
INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 2, p.`id` FROM `db_user`.`sys_permission` p
WHERE p.`permission_code` = 'system:dept:list' AND p.`deleted` = 0;

-- 5. role 3（user）刻意**什么都不给**：它没有 `system:user:list`，进不了系统管理这一组页面，
--    发部门读码等于凭空多一个它用不上的授权行。

-- ============================================================
-- 6. 校验（执行完自己看）
-- ============================================================
-- 6a. 四行在位，且**中文没灌错**（HEX 比对，见 D31：字符集错了在库里不可逆，只能重插）
SELECT `id`, `parent_id`, `permission_name`, HEX(`permission_name`) AS `hex`, `permission_code`, `type`
FROM `db_user`.`sys_permission`
WHERE `permission_code` LIKE 'system:dept:%' AND `deleted` = 0
ORDER BY `id`;
--   期望 HEX：部门管理=E983A8E997A8E7AEA1E79086 / 部门新增=E983A8E997A8E696B0E5A29E
--             部门编辑=E983A8E997A8E7BC96E8BE91 / 部门删除=E983A8E997A8E588A0E999A4
--   若出现 3F3F3F…（一串问号）⇒ 会话字符集又回到 latin1 了，立刻停下别继续灌。

-- 6b. 三个写码的父节点确实落在那条读码上（parent_id 相等即成立）
SELECT w.`permission_code` AS `write_code`, r.`permission_code` AS `parent_code`
FROM `db_user`.`sys_permission` w
LEFT JOIN `db_user`.`sys_permission` r ON r.`id` = w.`parent_id` AND r.`deleted` = 0
WHERE w.`permission_code` LIKE 'system:dept:%' AND w.`deleted` = 0
ORDER BY w.`id`;

-- 6c. 授权行数（期望：super_admin 43 / admin 20 / user 10；总数 39 → 43）
SELECT COUNT(*) AS permission_total FROM `db_user`.`sys_permission` WHERE `deleted` = 0;
SELECT r.`role_code`, COUNT(rp.`permission_id`) AS granted
FROM `db_user`.`sys_role` r
LEFT JOIN `db_user`.`sys_role_permission` rp ON rp.`role_id` = r.`id`
WHERE r.`deleted` = 0
GROUP BY r.`role_code`;

-- ============================================================
-- 7. 回滚（按需单独执行，先删授权再删码）
-- ============================================================
-- DELETE rp FROM `db_user`.`sys_role_permission` rp
--   INNER JOIN `db_user`.`sys_permission` p ON p.`id` = rp.`permission_id`
--   WHERE p.`permission_code` LIKE 'system:dept:%';
-- DELETE FROM `db_user`.`sys_permission` WHERE `permission_code` LIKE 'system:dept:%';
