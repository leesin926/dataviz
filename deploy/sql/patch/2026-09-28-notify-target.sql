SET NAMES utf8mb4;

-- 2026-09-28 · 阶段 AQ1：通知对象（联系人 / 通知组）入库
--
-- 为什么要这几张表：收件人原先存在 `notify_channel.config` 的 `to` / `receivers` / `mobiles` 里，
-- 而派发侧"每种渠道类型只取启用中 id 最小的那一条配置"（见 `NotifyDispatcher.findEnabledChannel`）
-- ⇒ 一条邮箱渠道只能带一组收件人，**所有**引用 EMAIL 的规则共用它，换收件人只能改渠道配置、
-- 没法按规则动态调整。所以把"发给谁"从"走哪条通道"里拆出来：通道 = 凭据与出口地址，
-- 通知对象 = 联系人（谁）+ 通知组（一组人）+ 规则引用组（这条规则发给哪几组）。
--
-- 判据：`atAll`（钉钉/企微"是否@所有人"）**留在渠道配置**，因为那是这个群机器人的行为；
-- `mobiles` / `to` / `receivers` 是"谁"，挪走。
--
-- 四张表**全部带 `tenant_id`**，这是刻意的：`MybatisPlusConfig.IGNORE_TENANT_TABLES` 的判据是
-- "这张表没有 tenant_id 列"，把关联表做成无租户列就得改这份清单 —— 而清单在 common-mybatis
-- 这个共享 jar 里，改一次等于 8 个端口全部重启面。带列则新表**默认被过滤**（黑名单语义），
-- 不需要动任何共享代码。
--
-- 执行前置（已跑，只读）：db_alert 现有 4 张表（alert_event / alert_notify_log / alert_rule /
-- notify_channel），本补丁的四张**均不存在** ⇒ 纯增量 DDL，不改任何既有列、不动任何既有行，
-- 因此 D30 的备份表规则未触发（没有可被改坏的东西）。
-- 基线：alert_rule 8 行（enabled=1 的 3 行：911/912/913）、notify_channel 4 行（901 EMAIL /
-- 902 DINGTALK 停 / 903 SMS / 911 WEBHOOK）。

USE `db_alert`;

-- 联系人：一个人有哪些可达地址。email / mobile 至少填一个 —— MySQL 5.7 的 CHECK 会被忽略、
-- 8.0 才生效，所以这条判据落在服务层（`AlertContactServiceImpl`），库里不加约束，
-- 免得出现"库能塞进去、界面报错了但没人知道是谁塞的"。
CREATE TABLE IF NOT EXISTS `alert_contact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL COMMENT '显示名（告警消息里 @ 的就是它）',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮件收件地址',
    `mobile` VARCHAR(32) DEFAULT NULL COMMENT '手机号：短信收件人 + 钉钉/企微机器人 @ 的对象',
    `remark` VARCHAR(256) DEFAULT NULL COMMENT '备注（值班岗位、职责范围等）',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '停用即不收件（离职/换岗），保留行以留住历史组成员关系',
    `tenant_id` BIGINT DEFAULT NULL,
    `create_by` VARCHAR(64) DEFAULT NULL,
    `update_by` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_enabled` (`tenant_id`, `enabled`),
    KEY `idx_tenant_name` (`tenant_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警联系人（通知对象的最小单位）';

-- 通知组：一撮联系人，是规则引用的单位
CREATE TABLE IF NOT EXISTS `alert_notify_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) DEFAULT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '停用组 = 整组一次不收件，比逐个停联系人更适合"这块今天不归我们管"',
    `tenant_id` BIGINT DEFAULT NULL,
    `create_by` VARCHAR(64) DEFAULT NULL,
    `update_by` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_enabled` (`tenant_id`, `enabled`),
    KEY `idx_tenant_name` (`tenant_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警通知组（联系人的集合，规则引用它）';

-- 组成员：多对多，成员关系本身没有"删除后还要复原"的语义 ⇒ 物理删（保存组时整批替换）
CREATE TABLE IF NOT EXISTS `alert_notify_group_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `group_id` BIGINT NOT NULL,
    `contact_id` BIGINT NOT NULL,
    `tenant_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_contact` (`group_id`, `contact_id`),
    KEY `idx_contact` (`contact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知组成员关系';

-- 规则引用组：告警消息"发给哪几组"。同样物理删（保存规则时整批替换）。
-- 不用 JSON 列塞 groupIds 的理由是删除守卫要**反查**"哪些规则在用这个组"，
-- JSON + LIKE 会把 900 和 9001 混成一次命中（这类"看着能用其实判据是错的"正是 API 台账里反复出现的形状）。
CREATE TABLE IF NOT EXISTS `alert_rule_notify_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `rule_id` BIGINT NOT NULL,
    `group_id` BIGINT NOT NULL,
    `tenant_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_group` (`rule_id`, `group_id`),
    KEY `idx_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则与通知组的引用关系';

-- ============================================================
-- 执行后自检（期望：四张表都在、行数全 0）
-- ⚠️ 计数用 COUNT(*) 而不是 information_schema.TABLES.TABLE_ROWS —— 后者是估算值，空表也可能显示非 0。
-- ⚠️ 不要用 GROUP_CONCAT 拼表名清单：默认上限 1024 字节会**静默截断**（D72 ⑤ 同一族）。
-- ============================================================
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'db_alert'
ORDER BY TABLE_NAME;

SELECT COUNT(*) AS contact_rows FROM db_alert.alert_contact;
SELECT COUNT(*) AS group_rows FROM db_alert.alert_notify_group;
SELECT COUNT(*) AS member_rows FROM db_alert.alert_notify_group_member;
SELECT COUNT(*) AS rule_group_rows FROM db_alert.alert_rule_notify_group;
