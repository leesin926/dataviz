-- 2026-09-19 Phase 2：screen 表新增三端配置变体列（可空，存量数据零迁移，缺省端回退顶层配置）
USE `db_screen`;

ALTER TABLE `screen`
    ADD COLUMN `variants_json` JSON DEFAULT NULL
    COMMENT '三端配置变体 {"pc":{...},"mobile":{...},"tablet":{...}}，缺省端回退顶层配置'
    AFTER `components_json`;
