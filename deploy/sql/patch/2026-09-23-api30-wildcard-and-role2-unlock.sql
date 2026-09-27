-- ============================================================
-- ⚠️ 临时探针数据 —— 只为跑两条至今不可执行的验收判据，**跑完必须执行同目录 `-rollback.sql`**
-- 文件：deploy/sql/patch/2026-09-23-api30-wildcard-and-role2-unlock.sql
-- 日期：2026-09-23   关联：docs/12 §19.9 / 缺陷 API-30 / 任务 #79 #61 #85   用户已批准（2026-09-23）
-- 这两条为什么需要动数据（都是"判据当前不可执行"，不是功能缺陷）：
--   ① **API-30 的端到端判据缺样本**：它的原句是"被授予 `system:*` 这类**前缀通配码**的角色，接口打得通、
--      菜单里却没有那一项"。但两份播种脚本里 `*` **只出现在注释**，`sys_role_permission` 里没有任何通配授权
--      ⇒ 老实现（裸 `includes`）与新实现（`matchesPermissionCode`）在现有数据下**表现完全一致**，端到端不可分辨。
--      函数级判据已在活页面证过（报告 19.8 那批：8/8 含 `system:*` 对照），这一轮补的是端到端那一半。
--   ② **role 2 没有可用真人账号**：`ops01`(904) 是唯一挂 role 2 的号且 `status=0`（禁用态是 API-14 封号验证的材料，
--      刻意留）。"同一模块 read 通、write 拒"这四条是读写两档唯一真正的端到端判据 ⇒ 按用户选定方案 ①：
--      把 role 2 **临时并挂**到 `designer01`(903)，跑完删这一行，不动 `ops01`、不损失封号演示。
-- 通配语义（两端一致，已读源码确认，不是推断）：
--   前端 `packages/permission/src/core/matchesPermissionCode.ts`（精确 / `*` / `xxx:*` 前缀通）
--   后端 `common-security/interceptor/PermissionInterceptor.java:107 matches()`（同一套三条，注释明写"两端必须一起改"）
--   ⇒ 所以本探针的预期是"菜单可见 **且** 接口 200"；若只见菜单可见而接口 403，说明两端语义分叉了，那是**新缺陷**。
--   ⚠️ 已知副作用（同一套前缀截断，两端共有）：`system:*` 会放行一切以 `system:` 开头的码，粒度比"逐模块 read"粗。
--      这正是本行只挂到探针结束的原因 —— 通配码**不打算进产品码表**。
-- 幂等：两张关联表都有唯一键（`uk_role_permission(role_id,permission_id)`、`uk_user_role(user_id,role_id)`），
--       `sys_permission` 有 `uk_permission_code(permission_code,deleted)` ⇒ 三条 INSERT IGNORE 重复跑不产生脏行。
-- D31 编码口径：`SET NAMES utf8mb4` + 执行时带 `--default-character-set=utf8mb4`；
--       本探针的 `permission_name` **刻意用 ASCII**（测完即删的临时行，不必再走一遍 HEX 取证）。
-- ============================================================

SET NAMES utf8mb4;
USE `db_user`;

-- ------------------------------------------------------------
-- 1. 通配授权探针：种一条 `system:*`，parent 沿用 `system:user:list` 的父节点（不猜 id，反查）
--    type=3（按钮/权限点）⇒ 不进菜单树渲染逻辑，只作授权用
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`parent_id`, 'PROBE system wildcard (temp, rollback me)', 'system:*', 3, '', '', 99
FROM `sys_permission` p
WHERE p.`permission_code` = 'system:user:list' AND p.`deleted` = 0;

-- 2. 授予 role 3（user）。**验收用 `viewer01`(905) 登录，不要用 designer01** ——
--    它在本脚本第 3 步会并挂 role 2，而 role 2 里已经有精确码 `system:user:list`，
--    两者混在一个号上就分不出"是通配匹配生效"还是"精确码本来就在"。
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 3, `id` FROM `sys_permission` WHERE `permission_code` = 'system:*' AND `deleted` = 0;

-- 3. role 2 真人正证：designer01(903) 临时并挂 role 2。id 949 落在演示数据 900~949 段内，回滚按 id 删。
INSERT IGNORE INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES (949, 903, 2);

-- ============================================================
-- 校验（执行完自己看，期望值写在注释里）
-- ============================================================
-- 期望：permission_total = 40（原 39 + 本探针 1 行）
SELECT COUNT(*) AS permission_total FROM `sys_permission` WHERE `deleted` = 0;

-- 期望：恰好 1 行，parent_id 与 `system:user:list` 相同（这条同时证明第 1 步的反查没落空）
SELECT a.`id`, a.`parent_id` AS probe_parent, b.`parent_id` AS list_parent, a.`permission_code`
FROM `sys_permission` a
LEFT JOIN `sys_permission` b ON b.`permission_code` = 'system:user:list' AND b.`deleted` = 0
WHERE a.`permission_code` = 'system:*';

-- 期望：super_admin 39 / admin 19 / user 11（role 3 = 原 10 + 本探针 1）
SELECT r.`role_code`, COUNT(rp.`permission_id`) AS granted
FROM `sys_role` r
LEFT JOIN `sys_role_permission` rp ON rp.`role_id` = r.`id`
LEFT JOIN `sys_permission` p ON p.`id` = rp.`permission_id` AND p.`deleted` = 0
WHERE r.`deleted` = 0
GROUP BY r.`role_code`;

-- 期望：designer01 两行（role 2 + role 3），其余演示号各一行
SELECT u.`username`, ur.`role_id`, r.`role_code`
FROM `sys_user` u
INNER JOIN `sys_user_role` ur ON ur.`user_id` = u.`id`
INNER JOIN `sys_role` r ON r.`id` = ur.`role_id`
WHERE u.`id` IN (901, 902, 903, 904, 905)
ORDER BY u.`id`, ur.`role_id`;

-- 期望：重复授权 0、孤儿授权 0（探针行不例外）
SELECT COUNT(*) AS dup_grants FROM (
  SELECT `role_id`, `permission_id` FROM `sys_role_permission`
  GROUP BY `role_id`, `permission_id` HAVING COUNT(*) > 1) x;

-- ⚠️ 执行后必须知道的一条（D55 边界①，与上轮两份播种脚本同）：
--    直接改库**不会**驱逐 Redis 会话快照，也不会刷新浏览器 localStorage ⇒
--    **每一步都要从登录接口重新登录**才拿得到新权限集；旧 token 的页面看到的还是旧快照。
