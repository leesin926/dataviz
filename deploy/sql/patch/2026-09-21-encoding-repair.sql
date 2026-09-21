-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

-- =============================================================
-- DataViz 中文编码修复补丁  2026-09-21（Round E / Y1）
-- -------------------------------------------------------------
-- 现象：init 脚本写入的中文在库里是字面量 `?`（如 `?????`），前端菜单/角色名显示为问号。
--
-- 根因（已实测确认，非双重编码）：
--   · 服务端全局字符集本身是正确的：@@global.character_set_server / _database / _client 均为 utf8mb4
--   · docker-compose.yml 也已传 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
--   · 但 mysql 命令行客户端未指定字符集时，会话 character_set_client/connection 会落到 latin1
--     （本次诊断中 `SHOW VARIABLES` 读到的 latin1 就是这个会话级默认值，不是服务端设置）
--   · latin1 连接下写入中文 → 每个字符被转换成不可映射的 `?`(0x3F) 后落库 ⇒ **入库即损毁**
--   · 因此 CONVERT(CAST(CONVERT(col USING latin1) AS BINARY) USING utf8mb4) 这类"双重编码修复"
--     对这些行是**无效操作**（0x3F 已是最终值）；更糟的是：若把该语句套到本来正常的 UTF-8 行上，
--     反而会正常中文打成 `?`（2026-09-21-test-data.sql 旧 §0 就犯过这个错，现已删除）
--   · 对照组：通过后端 JDBC 写入的中文完全正常
--     （db_screen.screen id=6 `回归…` HEX=E59B9E…；本补丁新种的业务数据也是正确 UTF-8）
--     ⇒ 应用层无需改动，问题只在"手工用 mysql CLI 导 SQL"这条路径
--
-- 修法：按**业务键**（role_code / permission_code / id）用源脚本里的权威原文整行覆盖，
--       语句本身幂等（重复执行结果一致），且能在再次被误写坏时自愈。
-- 权威来源：deploy/sql/init/02-auth-schema.sql:143-187（角色/权限/部门/昵称）、
--           deploy/sql/init/03-all-services-schema.sql:404（默认租户）、
--           frontend/apps/admin/src/views/SystemConfig.vue:125（哀悼配置备注）
--
-- 执行方式（⚠️ 必须带 --default-character-set=utf8mb4）：
--   cat deploy/sql/patch/2026-09-21-encoding-repair.sql \
--     | docker exec -i dataviz-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 -t
--
-- 后续防线：任何向本库手工导 SQL 的动作，都必须显式带 --default-character-set=utf8mb4；
--           可选加固（需重建容器，本补丁未做）：挂载 /etc/mysql/conf.d/utf8mb4.cnf 写入
--           `[client]\ndefault-character-set=utf8mb4`，让容器内 CLI 默认就是 utf8mb4。
-- =============================================================

-- =============================================
-- A. db_auth（auth-service 使用）
-- =============================================
USE `db_auth`;

UPDATE `sys_role` SET `role_name` = '超级管理员' WHERE `role_code` = 'super_admin';
UPDATE `sys_role` SET `role_name` = '系统管理员' WHERE `role_code` = 'admin';
UPDATE `sys_role` SET `role_name` = '普通用户'   WHERE `role_code` = 'user';

UPDATE `sys_permission` SET `permission_name` = '系统管理'   WHERE `permission_code` = 'system';
UPDATE `sys_permission` SET `permission_name` = '用户管理'   WHERE `permission_code` = 'system:user:list';
UPDATE `sys_permission` SET `permission_name` = '角色管理'   WHERE `permission_code` = 'system:role:list';
UPDATE `sys_permission` SET `permission_name` = '菜单管理'   WHERE `permission_code` = 'system:menu:list';
UPDATE `sys_permission` SET `permission_name` = '租户管理'   WHERE `permission_code` = 'system:tenant:list';
UPDATE `sys_permission` SET `permission_name` = '数据源管理' WHERE `permission_code` = 'datasource';
UPDATE `sys_permission` SET `permission_name` = 'ETL 管理'   WHERE `permission_code` = 'etl';
UPDATE `sys_permission` SET `permission_name` = '数据建模'   WHERE `permission_code` = 'model';
UPDATE `sys_permission` SET `permission_name` = '自助分析'   WHERE `permission_code` = 'analysis';
UPDATE `sys_permission` SET `permission_name` = '仪表板'     WHERE `permission_code` = 'dashboard';
UPDATE `sys_permission` SET `permission_name` = '大屏管理'   WHERE `permission_code` = 'screen';
UPDATE `sys_permission` SET `permission_name` = '告警中心'   WHERE `permission_code` = 'alert';
UPDATE `sys_permission` SET `permission_name` = '系统监控'   WHERE `permission_code` = 'monitor';
UPDATE `sys_permission` SET `permission_name` = '用户新增'   WHERE `permission_code` = 'system:user:add';
UPDATE `sys_permission` SET `permission_name` = '用户编辑'   WHERE `permission_code` = 'system:user:edit';
UPDATE `sys_permission` SET `permission_name` = '用户删除'   WHERE `permission_code` = 'system:user:delete';
UPDATE `sys_permission` SET `permission_name` = '用户查询'   WHERE `permission_code` = 'system:user:query';

UPDATE `sys_dept` SET `dept_name` = '总公司' WHERE `id` = 1;
UPDATE `sys_dept` SET `dept_name` = '技术部' WHERE `id` = 2;
UPDATE `sys_dept` SET `dept_name` = '产品部' WHERE `id` = 3;
UPDATE `sys_dept` SET `dept_name` = '运营部' WHERE `id` = 4;

UPDATE `sys_user` SET `nickname` = '系统管理员' WHERE `id` = 1 AND `username` = 'admin';

-- =============================================
-- B. db_user（user-service 使用，与 db_auth 同构镜像数据）
-- =============================================
USE `db_user`;

UPDATE `sys_role` SET `role_name` = '超级管理员' WHERE `role_code` = 'super_admin';
UPDATE `sys_role` SET `role_name` = '系统管理员' WHERE `role_code` = 'admin';
UPDATE `sys_role` SET `role_name` = '普通用户'   WHERE `role_code` = 'user';

UPDATE `sys_permission` SET `permission_name` = '系统管理'   WHERE `permission_code` = 'system';
UPDATE `sys_permission` SET `permission_name` = '用户管理'   WHERE `permission_code` = 'system:user:list';
UPDATE `sys_permission` SET `permission_name` = '角色管理'   WHERE `permission_code` = 'system:role:list';
UPDATE `sys_permission` SET `permission_name` = '菜单管理'   WHERE `permission_code` = 'system:menu:list';
UPDATE `sys_permission` SET `permission_name` = '租户管理'   WHERE `permission_code` = 'system:tenant:list';
UPDATE `sys_permission` SET `permission_name` = '数据源管理' WHERE `permission_code` = 'datasource';
UPDATE `sys_permission` SET `permission_name` = 'ETL 管理'   WHERE `permission_code` = 'etl';
UPDATE `sys_permission` SET `permission_name` = '数据建模'   WHERE `permission_code` = 'model';
UPDATE `sys_permission` SET `permission_name` = '自助分析'   WHERE `permission_code` = 'analysis';
UPDATE `sys_permission` SET `permission_name` = '仪表板'     WHERE `permission_code` = 'dashboard';
UPDATE `sys_permission` SET `permission_name` = '大屏管理'   WHERE `permission_code` = 'screen';
UPDATE `sys_permission` SET `permission_name` = '告警中心'   WHERE `permission_code` = 'alert';
UPDATE `sys_permission` SET `permission_name` = '系统监控'   WHERE `permission_code` = 'monitor';
UPDATE `sys_permission` SET `permission_name` = '用户新增'   WHERE `permission_code` = 'system:user:add';
UPDATE `sys_permission` SET `permission_name` = '用户编辑'   WHERE `permission_code` = 'system:user:edit';
UPDATE `sys_permission` SET `permission_name` = '用户删除'   WHERE `permission_code` = 'system:user:delete';
UPDATE `sys_permission` SET `permission_name` = '用户查询'   WHERE `permission_code` = 'system:user:query';

UPDATE `sys_dept` SET `dept_name` = '总公司' WHERE `id` = 1;
UPDATE `sys_dept` SET `dept_name` = '技术部' WHERE `id` = 2;
UPDATE `sys_dept` SET `dept_name` = '产品部' WHERE `id` = 3;
UPDATE `sys_dept` SET `dept_name` = '运营部' WHERE `id` = 4;

-- sys_user id=1 在 db_user 里可能不存在（admin 由 user-service 同步），加 username 条件避免误伤
UPDATE `sys_user` SET `nickname` = '系统管理员' WHERE `id` = 1 AND `username` = 'admin';

-- 本补丁新增的演示账号昵称（若曾被旧 §0 的 CONVERT 语句打坏，这里一并覆盖回来）
UPDATE `sys_user` SET `nickname` = '陈静（分析师）' WHERE `id` = 901;
UPDATE `sys_user` SET `nickname` = '刘洋（分析师）' WHERE `id` = 902;
UPDATE `sys_user` SET `nickname` = '孙悦（设计）'   WHERE `id` = 903;
UPDATE `sys_user` SET `nickname` = '何平（运维）'   WHERE `id` = 904;
UPDATE `sys_user` SET `nickname` = '吴敏（只读）'   WHERE `id` = 905;

-- =============================================
-- C. db_admin（admin-service 使用）
-- =============================================
USE `db_admin`;

UPDATE `sys_tenant` SET `name` = '默认租户' WHERE `id` = 1;
-- 901~903 为测试数据；旧 §0 的 CONVERT 语句把正确的中文联系人名打成了 `??`，这里覆盖回来
UPDATE `sys_tenant` SET `contact_name` = '周敏' WHERE `id` = 901;
UPDATE `sys_tenant` SET `contact_name` = '李雷' WHERE `id` = 902;
UPDATE `sys_tenant` SET `contact_name` = '赵强' WHERE `id` = 903;

UPDATE `sys_config`
   SET `remark` = '全局哀悼模式：true 时管理端与设计端/分享页整体灰度'
 WHERE `id` = 1 AND `config_key` = 'screen.mourning.enabled';

-- =============================================
-- D. 校验
-- =============================================

-- D1. 全库扫描：三个库里不应再有任何"含 ? 的文本"（期望 0 行）
SELECT * FROM (
    SELECT 'db_auth.sys_role.role_name' col, COUNT(*) n FROM `db_auth`.`sys_role` WHERE `role_name` LIKE '%?%'
    UNION ALL SELECT 'db_auth.sys_permission.permission_name', COUNT(*) FROM `db_auth`.`sys_permission` WHERE `permission_name` LIKE '%?%'
    UNION ALL SELECT 'db_auth.sys_dept.dept_name', COUNT(*) FROM `db_auth`.`sys_dept` WHERE `dept_name` LIKE '%?%'
    UNION ALL SELECT 'db_auth.sys_user.nickname', COUNT(*) FROM `db_auth`.`sys_user` WHERE `nickname` LIKE '%?%'
    UNION ALL SELECT 'db_user.sys_role.role_name', COUNT(*) FROM `db_user`.`sys_role` WHERE `role_name` LIKE '%?%'
    UNION ALL SELECT 'db_user.sys_permission.permission_name', COUNT(*) FROM `db_user`.`sys_permission` WHERE `permission_name` LIKE '%?%'
    UNION ALL SELECT 'db_user.sys_dept.dept_name', COUNT(*) FROM `db_user`.`sys_dept` WHERE `dept_name` LIKE '%?%'
    UNION ALL SELECT 'db_user.sys_user.nickname', COUNT(*) FROM `db_user`.`sys_user` WHERE `nickname` LIKE '%?%'
    UNION ALL SELECT 'db_admin.sys_tenant.name', COUNT(*) FROM `db_admin`.`sys_tenant` WHERE `name` LIKE '%?%'
    UNION ALL SELECT 'db_admin.sys_tenant.contact_name', COUNT(*) FROM `db_admin`.`sys_tenant` WHERE `contact_name` LIKE '%?%'
    UNION ALL SELECT 'db_admin.sys_config.remark', COUNT(*) FROM `db_admin`.`sys_config` WHERE `remark` LIKE '%?%'
) t WHERE n > 0 ORDER BY col;

-- D2. 十六进制抽查：应为 E4/E5 开头的 UTF-8 多字节序列，而非 3F3F3F（期望每行 hex_label 不以 3F 开头）
SELECT source, id, label, HEX(LEFT(label, 3)) hex_label FROM (
    SELECT 'db_auth.sys_role' source, id, role_name label FROM `db_auth`.`sys_role`
    UNION ALL SELECT 'db_auth.sys_dept', id, dept_name FROM `db_auth`.`sys_dept`
    UNION ALL SELECT 'db_auth.sys_permission', id, permission_name FROM `db_auth`.`sys_permission` WHERE id IN (1,7,11)
    UNION ALL SELECT 'db_user.sys_role', id, role_name FROM `db_user`.`sys_role`
    UNION ALL SELECT 'db_user.sys_permission', id, permission_name FROM `db_user`.`sys_permission` WHERE id IN (1,7,11)
    UNION ALL SELECT 'db_admin.sys_tenant', id, name FROM `db_admin`.`sys_tenant`
    UNION ALL SELECT 'db_screen.screen', id, name FROM `db_screen`.`screen` WHERE id = 6
) x ORDER BY source, id;

-- D3. 菜单树可读性（管理端/设计端左侧菜单来源，期望 13 行中文名）
SELECT `id`, `parent_id`, `permission_code`, `permission_name`, `type`, `path`
FROM `db_auth`.`sys_permission` WHERE `type` = 1 ORDER BY `sort_order`, `id`;
