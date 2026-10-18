SET NAMES utf8mb4;

-- 2026-10-16 · 阶段 AS3：平台许可证落库（R2-⑤）
--
-- 为什么需要这张表：`LicenseController` 此前把一份写死在代码里的演示对象返回给前端，
-- `LicenseManage.vue` 因此永远显示"有效"——那种永远绿的面板比没有面板更坏：它让"要不要管许可证"
-- 这个尚未决定的产品问题，看起来已经决定了。现在把它变成真的：密钥由 `LicenseCodec` 用
-- HMAC-SHA256 校验，激活结果落库，面板显示的是库里这一条的真实状态和剩余天数。
--
-- 三条设计判据：
-- ① **只存指纹，不存密钥原文**（`key_fingerprint` = sha256 前 6 字节 hex）。
--    校验只需要 HMAC 重算一遍，不需要"从库里把钥匙读出来"。这张表被拖走时，攻击者拿到的是
--    一串无法反推密钥的摘要，而不是一批可直接复用的激活材料。
-- ② **不带 `tenant_id`**：许可证是整个部署的属性，不是某个租户的属性。
--    但 `MybatisPlusConfig.IGNORE_TENANT_TABLES` 是"没有 tenant_id 列的表"的黑名单，
--    漏配的症状是登录态一查许可证就报 `Unknown column 'tenant_id'`。
--    ⇒ 本补丁必须与 common-mybatis 里那一行 `"sys_license"` 一起上线，少一边都是 500。
--    重启面：common-mybatis 是共享 jar，但只有 admin-service 读写这张表，所以实际需要重启的只有 8093。
-- ③ **"当前生效"只有一个，靠事务保证，不靠唯一索引**。
--    MySQL 没有部分索引，`UNIQUE (status, deleted)` 会把"已作废的历史"也只留一条，正好毁掉审计。
--    `LicenseServiceImpl.activate()` 在同一事务里先把旧行置 0 再插入新行；
--    读取侧若发现 >1 行 status=1 会打 WARN —— 也就是说这个不变量破了会说话，而不是静默选一条。
--
-- 语义：一个租户/部署的历史都在这里，`status=1` 是"当前用的那张"，`status=0` 是被顶替的过去。
--       重复激活不覆盖历史（旧行保留），所以"谁在什么时候换过钥匙"是可查的。
--
-- 前置条件（不是 SQL，但缺了它这张表用不起来）：admin-service 需要环境变量
-- `LICENSE_SECRET`（`platform.license.secret: ${LICENSE_SECRET:}`，代码里没有默认值）。
-- 留空时激活请求会明确失败，而不是回退到内置密钥——硬编码一个演示密钥等于给所有部署发万能钥匙。
--
-- 执行前置：本表在 db_admin 中不存在 ⇒ 纯增量 DDL，不改任何既有列与行，D30 备份规则未触发。
-- 执行后：`sys_license` 0 行。0 行 = 未激活，许可证面板显示"未激活"，
--         查询路径与本补丁之前逐字一致（0 行就是可回退判据）。
--         注意：valid=false 目前不拦截任何业务功能——是否让过期许可证真的停用是产品决定，不在代码里替他做。

USE `db_admin`;

CREATE TABLE IF NOT EXISTS `sys_license` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `key_fingerprint` VARCHAR(32) NOT NULL COMMENT '密钥指纹（sha256 前 6 字节 hex），不存原文',
    `edition` VARCHAR(32) NOT NULL COMMENT '版本：STANDARD/PRO/ENTERPRISE（取自密钥载荷，不由前端传）',
    `issued_to` VARCHAR(128) NOT NULL COMMENT '授权对象',
    `issued_at` DATE NOT NULL,
    `expires_at` DATE NOT NULL COMMENT '到期日（含当天）；早于 issued_at 的密钥在签发阶段就会被拒',
    `max_users` INT NOT NULL COMMENT '用户数上限，密钥里必须是正数',
    `max_nodes` INT NOT NULL COMMENT '节点数上限',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=当前生效，0=被后续激活顶替',
    `activate_time` DATETIME DEFAULT NULL COMMENT '本次激活时间',
    `activated_by` VARCHAR(64) DEFAULT NULL COMMENT '激活人用户名（会话里的 username，不由请求体传）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_license_status` (`status`, `deleted`),
    KEY `idx_license_fingerprint` (`key_fingerprint`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台许可证（激活历史，当前生效行 status=1）';

-- 判据留档：确认"表存在但零激活"，即执行本补丁后行为未变（面板应显示"未激活"）。
-- SELECT COUNT(*) AS total, SUM(status = 1) AS active FROM `db_admin`.`sys_license`;
-- 不变量抽查：active 最多 1。>1 说明有绕过 activate() 的写入方。
-- SELECT COUNT(*) FROM `db_admin`.`sys_license` WHERE `status` = 1 AND `deleted` = 0;
