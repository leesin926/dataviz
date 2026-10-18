SET NAMES utf8mb4;

-- 2026-10-16 · 阶段 AS4：行列权限规则来源（API-21）
--
-- 为什么需要这张表：`analysis-service` 的 PermissionFilter 一直在读两个 Redis 键
-- `perm:row:{tenant}:{user}` / `perm:col:{tenant}:{user}`，而**全后端零写入方** ——
-- 行权限那段会改写 SQL，列权限那段解析出受限列之后原样 `return sql`（死代码）。
-- 也就是说"数据权限"从来没有规则来源，只是查了一个永远为空的键。
-- 现在把来源补上：规则落库、管理端可配、执行期按 用户 + 角色 + 数据集 命中并强制。
--
-- 三条设计判据（都是踩过坑之后定的）：
-- ① **带 `tenant_id`**：`MybatisPlusConfig.IGNORE_TENANT_TABLES` 的判据是"这张表没有 tenant_id 列"，
--    带列则新表默认被租户过滤，不需要动 common-mybatis 这个共享 jar ⇒ 重启面只有 analysis-service 自己。
-- ② **行条件存结构化 JSON，不存管理员手写的 SQL 片段**：成品 SQL 要经 HTTP 交给 datasource-service 执行，
--    没有参数绑定可退。若允许存原始片段，配规则的人就拿到了一条比查询本身更大的注入通道
--    （他能越过规则去读这个数据源能读的任何东西）。渲染时走 `SqlBuilder.permissionPredicate`，
--    与业务 filters 同一条白名单（字段名、操作符、字面量），不认识的一律拒绝。
-- ③ **未命中规则即查不到数据（fail-closed）**：只要某数据集存在启用中的规则，而当前用户一条都不命中，
--    谓词就是 `1 = 0` 而不是"不加限制"。反过来（没配任何规则）不加谓词，行为与本补丁之前完全一致。
--    这一条会让界面必须显形"该数据集已配 N 条规则，未命中的用户查不到数据"，否则症状会被当成故障。
--
-- 语义：规则之间 **OR**（角色授权是叠加的，两条规则各放行一部分，取并集）；
--       一条规则内的 conditions **AND**（"部门=A 且 状态=生效"是一个人能看到的东西）。
--       超级管理员（`super_admin`）跳过行列限制，与 RBAC 拦截器的短路口径一致。
--
-- 执行前置：本表在 db_analysis 中不存在 ⇒ 纯增量 DDL，不改任何既有列与行，D30 备份规则未触发。
-- 执行后：`data_perm_rule` 0 行。0 行 = 没有任何规则 = 查询路径与本补丁之前逐字一致（可回退判据）。

USE `db_analysis`;

CREATE TABLE IF NOT EXISTS `data_perm_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL COMMENT '规则名（列表与告警文案里引用的就是它）',
    `dataset_id` BIGINT DEFAULT NULL COMMENT '作用数据集；NULL = 本租户全部数据集',
    `scope_type` VARCHAR(16) NOT NULL COMMENT '命中方式：USER=按用户ID，ROLE=按角色码',
    `scope_value` VARCHAR(64) NOT NULL COMMENT 'USER 存用户ID，ROLE 存角色码（如 analyst）',
    `conditions_json` TEXT COMMENT '行条件 [{field,operator,value}]，规则内 AND；值支持 :userId/:tenantId/:username/:deptId 占位',
    `restricted_columns` VARCHAR(1024) DEFAULT NULL COMMENT '列权限：逗号分隔的物理列名，命中即从结果里剔除',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '0=停用（保留行，规则是审计对象，删掉就说不清谁改过）',
    `remark` VARCHAR(256) DEFAULT NULL COMMENT '备注（为什么要有这条规则）',
    `tenant_id` BIGINT DEFAULT NULL,
    `create_by` VARCHAR(64) DEFAULT NULL,
    `update_by` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_perm_scope` (`tenant_id`, `scope_type`, `scope_value`, `enabled`),
    KEY `idx_perm_dataset` (`tenant_id`, `dataset_id`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '行列数据权限规则（API-21 的规则来源）';

-- 判据留档：这条 SELECT 用来确认"表存在但零规则"，即执行本补丁后行为未变。
-- SELECT COUNT(*) FROM `db_analysis`.`data_perm_rule`;
