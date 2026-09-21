-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

-- =============================================
-- entity ↔ 实际库结构 冲突修复补丁 (schema-conflict-fix)
-- 前置：必须先执行 2026-09-21-schema-completion.sql（补建缺失表/列）
-- 依据：静态比对 backend/*/entity 字段 ↔ information_schema（脚本 entity-schema-check.js），
--       并逐表核对 STRICT_TRANS_TABLES 下的 INSERT/SELECT 可行性。
-- 范围：只做「放宽/类型对齐/补列/建表」，不 DROP 表、不删列、不改数据语义。
-- 影响：涉及表当前行数均为 0（仅 db_screen.screen 有 6 行且不在本文件改列范围内），
--       因此 MODIFY 无数据迁移风险。
-- 执行：cat 本文件 | docker exec -i dataviz-mysql mysql -uroot -p --default-character-set=utf8mb4
-- 日期：2026-09-21
-- =============================================

-- =============================================
-- A. 补齐两个「有 entity + Controller 但无表」的接口依赖表
--    缺失后果：/api/screen/component/** 与 /api/collab/approval/** 直接 SQL 报错(表不存在)
-- =============================================

USE `db_screen`;

-- ScreenComponent（不继承 BaseEntity，无 deleted/tenant_id；由 ScreenComponentMapper 直接 CRUD）
CREATE TABLE IF NOT EXISTS `screen_component` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `screen_id` BIGINT NOT NULL COMMENT '所属大屏ID(screen.id)',
    `component_type` VARCHAR(32) NOT NULL COMMENT '组件类型',
    `title` VARCHAR(128) DEFAULT NULL COMMENT '组件标题',
    `config_json` JSON DEFAULT NULL COMMENT '组件样式配置',
    `data_config_json` JSON DEFAULT NULL COMMENT '组件数据配置',
    `position_json` JSON DEFAULT NULL COMMENT '位置尺寸{x,y,w,h}',
    `refresh_interval` INT DEFAULT 0 COMMENT '数据刷新间隔(秒),0=不刷新',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_screen_id` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大屏组件表(独立存储的组件实例)';

USE `db_collab`;

-- CollabApproval（entity 字段：tenantId/targetType/targetId/applicantId/approverId/status/comment/createTime/approveTime）
CREATE TABLE IF NOT EXISTS `collab_approval` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `target_type` VARCHAR(32) NOT NULL COMMENT '审批对象类型 DASHBOARD/SCREEN/DATASET',
    `target_id` BIGINT NOT NULL COMMENT '对象ID',
    `applicant_id` BIGINT NOT NULL COMMENT '申请人ID',
    `approver_id` BIGINT DEFAULT NULL COMMENT '审批人ID',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0-待审批 1-通过 2-驳回',
    `comment` VARCHAR(512) DEFAULT NULL COMMENT '审批意见',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
    PRIMARY KEY (`id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_applicant` (`applicant_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协作审批表';

-- =============================================
-- B. 放宽 entity 未提供、但库里 NOT NULL 且无默认值的列
--    后果（修复前）：STRICT 模式下 INSERT 报 "Field 'x' doesn't have a default value" → 创建类接口 500
-- =============================================

USE `db_datasource`;
-- Datasource entity 只有 name/type/config/status/description：host/port 挪进 config JSON，故放宽
ALTER TABLE `datasource`
    MODIFY COLUMN `host` VARCHAR(256) NULL DEFAULT NULL COMMENT '主机(旧列，新数据存于 config JSON)',
    MODIFY COLUMN `port` INT NULL DEFAULT NULL COMMENT '端口(旧列，新数据存于 config JSON)';

USE `db_etl`;
-- EtlTask entity 无 dagJson 字段（DAG 由前端 dag 结构写入 transform_config / 新表 etl_task_log）
ALTER TABLE `etl_task`
    MODIFY COLUMN `dag_json` JSON NULL DEFAULT NULL COMMENT 'DAG配置(旧列，entity 不再映射)';

-- EtlTaskInstance：triggerType=String(manual/cron/api)、status=Integer(0/1/2/3)，与库里 tinyint/varchar(16) 互换
ALTER TABLE `etl_task_instance`
    MODIFY COLUMN `trigger_type` VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT '触发方式 manual/cron/api',
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '执行状态 0-运行中 1-成功 2-失败 3-取消';

USE `db_analysis`;
-- QueryHistory entity：userId 为 String(用户名)，且不写 dataset_id/query_json
ALTER TABLE `query_history`
    MODIFY COLUMN `user_id` VARCHAR(64) NOT NULL COMMENT '执行用户(用户名)',
    MODIFY COLUMN `dataset_id` BIGINT NULL DEFAULT NULL COMMENT '数据集ID(旧列)',
    MODIFY COLUMN `query_json` JSON NULL DEFAULT NULL COMMENT '查询配置JSON(旧列)',
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行状态 SUCCESS/FAILED';

USE `db_model`;
-- ModelDimension entity 无 datasetId/dimensionType（改为挂在 datasource+table 上）
ALTER TABLE `model_dimension`
    MODIFY COLUMN `dataset_id` BIGINT NULL DEFAULT NULL COMMENT '数据集ID(旧列，可选)',
    MODIFY COLUMN `dimension_type` VARCHAR(32) NULL DEFAULT 'CATEGORICAL' COMMENT '维度类型(旧列，可选)';

-- ModelMetric entity 无 aggFunction/dataType（用 aggregation_type/expression）
ALTER TABLE `model_metric`
    MODIFY COLUMN `dataset_id` BIGINT NULL DEFAULT NULL COMMENT '数据集ID(旧列，可选)',
    MODIFY COLUMN `agg_function` VARCHAR(32) NULL DEFAULT NULL COMMENT '聚合函数(旧列，与 aggregation_type 并存)',
    MODIFY COLUMN `data_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '数据类型(旧列，可选)';

-- EtlTask.status entity 为 String 枚举名（STOPPED/RUNNING/PAUSED/ERROR/COMPLETED）
ALTER TABLE `db_etl`.`etl_task`
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'STOPPED' COMMENT '任务状态 STOPPED/RUNNING/PAUSED/ERROR/COMPLETED';

-- =============================================
-- C. db_auth 列名对齐 auth-service entity（并统一为 04-user-service-schema.sql 的命名）
--    auth-service entity：SysRole.roleCode / SysPermission.permissionCode / *.sortOrder / SysRole.description / SysUser.deptId
--    db_auth 旧列：role_key / permission_key / sort / remark / 无 dept_id
--    ⚠ 配套 Java 改动：auth-service RoleMapper.xml 的 r.role_key、PermissionMapper.xml 的 p.permission_key
--      已同步改为 role_code / permission_code（需重启 auth-service 生效）
-- =============================================

USE `db_auth`;

ALTER TABLE `sys_role`
    CHANGE COLUMN `role_key` `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    CHANGE COLUMN `remark` `description` VARCHAR(256) DEFAULT NULL COMMENT '角色描述',
    CHANGE COLUMN `sort` `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号';

ALTER TABLE `sys_permission`
    CHANGE COLUMN `permission_key` `permission_code` VARCHAR(128) NOT NULL COMMENT '权限编码',
    CHANGE COLUMN `sort` `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号';

ALTER TABLE `sys_dept`
    CHANGE COLUMN `sort` `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号';

ALTER TABLE `sys_user`
    ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '部门ID' AFTER `status`;

-- =============================================
-- D. 校验
-- =============================================

-- D1) 两张新表到位（期望 2 行）
SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES
WHERE (TABLE_SCHEMA, TABLE_NAME) IN (('db_screen', 'screen_component'), ('db_collab', 'collab_approval'));

-- D2) 不再有「NOT NULL + 无默认值 + entity 不提供」的列（期望 0 行）
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA LIKE 'db\_%' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL
  AND EXTRA NOT LIKE '%auto_increment%'
  AND (
    (TABLE_SCHEMA = 'db_datasource' AND TABLE_NAME = 'datasource'  AND COLUMN_NAME IN ('host','port'))
 OR (TABLE_SCHEMA = 'db_etl'        AND TABLE_NAME = 'etl_task'    AND COLUMN_NAME = 'dag_json')
 OR (TABLE_SCHEMA = 'db_analysis'   AND TABLE_NAME = 'query_history' AND COLUMN_NAME IN ('dataset_id','query_json'))
 OR (TABLE_SCHEMA = 'db_model'      AND TABLE_NAME IN ('model_dimension','model_metric')
                                     AND COLUMN_NAME IN ('dataset_id','dimension_type','agg_function','data_type'))
  );

-- D3) 类型对齐结果（期望：etl_task.status=varchar(32)、etl_task_instance.status=tinyint、
--      etl_task_instance.trigger_type=varchar(32)、query_history.user_id=varchar(64)、query_history.status=varchar(32)）
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE (TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME) IN
      (('db_etl','etl_task','status'), ('db_etl','etl_task_instance','status'),
       ('db_etl','etl_task_instance','trigger_type'), ('db_analysis','query_history','user_id'),
       ('db_analysis','query_history','status'));

-- D4) db_auth 列名对齐（期望 role_code / permission_code / sort_order / description / dept_id 各 1 行，共 6 行；
--     且旧列 role_key / permission_key / remark 已不存在 → D5）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_auth'
  AND (
    (TABLE_NAME = 'sys_role'       AND COLUMN_NAME IN ('role_code','description','sort_order'))
 OR (TABLE_NAME = 'sys_permission' AND COLUMN_NAME IN ('permission_code','sort_order'))
 OR (TABLE_NAME = 'sys_dept'       AND COLUMN_NAME = 'sort_order')
 OR (TABLE_NAME = 'sys_user'       AND COLUMN_NAME = 'dept_id')
  );

-- D5) 旧列名残留检查（期望 0 行）
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_auth'
  AND ((TABLE_NAME = 'sys_role' AND COLUMN_NAME IN ('role_key','remark','sort'))
    OR (TABLE_NAME = 'sys_permission' AND COLUMN_NAME IN ('permission_key','sort'))
    OR (TABLE_NAME = 'sys_dept' AND COLUMN_NAME = 'sort'));

-- D6) db_auth 既有数据未被破坏（期望 3 角色 / 17 权限 / 4 部门 / 1 用户，role_code 有值）
SELECT (SELECT COUNT(*) FROM db_auth.sys_role) role_cnt,
       (SELECT COUNT(*) FROM db_auth.sys_permission) perm_cnt,
       (SELECT COUNT(*) FROM db_auth.sys_dept) dept_cnt,
       (SELECT COUNT(*) FROM db_auth.sys_user) user_cnt,
       (SELECT GROUP_CONCAT(role_code ORDER BY id) FROM db_auth.sys_role) role_codes;
