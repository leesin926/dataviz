-- ============================================================
-- 剩余模块权限码播种 · AS6 阶段（R1：最后一批零强制 handler）
-- 日期：2026-10-16   关联决策：D54/D56（读写两档口径）/ D66（一页一码，不复用别模块码）
-- 关联缺陷：API-28 的收尾半区（R1 三档处置）
-- 背景：AD 轮种了 datasource/model/analysis/screen/dashboard/alert/etl/platform 八个模块，
--       AJ 轮种了 system:dept:*。剩下 schedule / ai / file / monitor / collab / openapi 六组
--       共 78 个 handler 到此仍是**零强制** —— 任何登录用户都能改调度任务、铸对外 API 凭据、
--       删任意人的文件。本轮按同一口径补齐，并把注解一并落上（代码侧见各 Controller）。
-- 为什么含父目录行：`schedule`/`ai`/`file`/`collab`/`openapi` 在 sys_permission 里**原本没有目录行**
--       （monitor 有，id=13）。两档码得挂在目录下面，没有目录就只能挂到 `system`(id=1) 上 ——
--       那等于在授权树里把"调度"藏进"系统管理"，以后按模块勾权限的人找不到它。
--       目录 permission_code 用模块名本身，与既有 8 个目录行同一约定。
-- collab 两个码是还债：AS3 轮 ApprovalController 的类注释写"码表补丁在 deploy/sql/patch 里待执行"，
--       但那个补丁当时**并没有落盘**（grep 全 deploy 目录零命中 `collab:read`）。
--       也就是说那句话在 2026-10-16 之前是假的；本脚本就是它欠的那份补丁。
-- ⚠️ 两档粒度的已知代价（同 AD 轮，这里只补对本模块生效的部分）：
--    ① `ai:read` 能看会话历史，`ai:write` 才能发消息/生成洞察 —— 但发消息会调大模型、有真实现金成本，
--       所以"能不能花钱"这件事只能靠 write 码表达，没法再细一层。
--    ② `openapi:write` = 能铸/重置对外凭据。拿到它的外部钥匙本身又能打内部服务，
--       所以这一码实际是"能不能给别人发通行证"，比一般的写码重。
--    ③ `file:read` 覆盖 `/download/{id}`，但**不覆盖** `/view/{id}`：后者是免登图片直读
--       （网关与 AuthInterceptor 白名单里都有它），FileController 因此刻意只挂方法级注解、不挂类级。
-- 影响面：只加 17 行码表 + 给 role 1（super_admin）补授权 ⇒ 执行后对现有 6 个演示账号**行为变化为 0**
--         （admin 走 role_code 短路；role 2/3 本来就一个码都没有，本轮也不给他们发）。
--         真正的"谁能干什么"在下面第 5 节的草拟矩阵，那部分属产品决策，**本脚本不含、也不执行**。
-- 执行：docker exec -i dataviz-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot \
--         --default-character-set=utf8mb4' < deploy/sql/patch/2026-10-16-r1-permission-codes.sql
--       （容器会话默认 latin1，首句必须 SET NAMES utf8mb4，见 D31；中文灌错不可逆）
-- 幂等：不写死 id —— 早期脚本用自增 id 插过探针行（2026-09-23-api30），写死会撞上并被
--       INSERT IGNORE **静默跳过**，"没种上"会看起来像"种上了"。全部靠
--       uk_permission_code(permission_code, deleted) 去重，父节点按 code 反查。
-- 执行后：直接改库**不会驱逐 Redis 会话快照**（D55 边界①）⇒ 已登录用户要重新登录才拿到新码。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 五个新目录行（type=1，parent_id=0）
--    sort_order 落在既有目录的空档里：alert 60 / datasource 50 / etl 40 / model 35 /
--    analysis 30 / dashboard 20 / screen 15，monitor 90、system 100 在最上。
-- ------------------------------------------------------------
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
VALUES
    (0, '定时调度', 'schedule', 1, '/schedule',  'Timer',     55),
    (0, 'AI 助手',  'ai',       1, '/ai',        'MagicStick', 45),
    (0, '文件管理', 'file',     1, '/file',      'Folder',     25),
    (0, '协同评论', 'collab',   1, '/collab',    'ChatDotRound', 18),
    (0, '开放平台', 'openapi',  1, '/openapi',   'Link',       12);

-- ------------------------------------------------------------
-- 2. 十二个两档码，父节点按 code 反查（不猜 id）
--    type=3（按钮）：与 AD 轮的 {module}:read/{module}:write 同一种节点，管理端授权树里
--    它们是目录下的可勾选项；path 留空，侧边栏由 router 静态派生、不读这一列。
-- ------------------------------------------------------------
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '调度-查看', 'schedule:read', 3, '', '', 1
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'schedule' AND p.`deleted` = 0;
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '调度-变更', 'schedule:write', 3, '', '', 2
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'schedule' AND p.`deleted` = 0;

INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, 'AI-查看', 'ai:read', 3, '', '', 1
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'ai' AND p.`deleted` = 0;
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, 'AI-变更', 'ai:write', 3, '', '', 2
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'ai' AND p.`deleted` = 0;

INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '文件-查看', 'file:read', 3, '', '', 1
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'file' AND p.`deleted` = 0;
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '文件-变更', 'file:write', 3, '', '', 2
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'file' AND p.`deleted` = 0;

INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '协同-查看', 'collab:read', 3, '', '', 1
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'collab' AND p.`deleted` = 0;
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '协同-变更', 'collab:write', 3, '', '', 2
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'collab' AND p.`deleted` = 0;

INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '开放平台-查看', 'openapi:read', 3, '', '', 1
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'openapi' AND p.`deleted` = 0;
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '开放平台-变更', 'openapi:write', 3, '', '', 2
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'openapi' AND p.`deleted` = 0;

-- monitor 挂在既有目录 `monitor`（02-auth-schema.sql id=13）下。AD 轮那句"monitor 本轮不播种"
-- 到这一轮才兑现；本轮只给它两个码，其中 write 码目前封的是注册/心跳/上报那组机器端点
-- （临时口径，正解是 /internal/ + X-Internal-Token，见 ServiceRegistryController 类注释）。
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '监控-查看', 'monitor:read', 3, '', '', 1
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'monitor' AND p.`deleted` = 0;
INSERT IGNORE INTO `db_user`.`sys_permission`
    (`parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`)
SELECT p.`id`, '监控-变更', 'monitor:write', 3, '', '', 2
FROM `db_user`.`sys_permission` p WHERE p.`permission_code` = 'monitor' AND p.`deleted` = 0;

-- ------------------------------------------------------------
-- 3. 保住"super_admin = 全部权限"这个不变式（同 AD/D 轮每一步）
-- ------------------------------------------------------------
INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `db_user`.`sys_permission` p
WHERE p.`permission_code` IN (
    'schedule', 'ai', 'file', 'collab', 'openapi',
    'schedule:read', 'schedule:write', 'ai:read', 'ai:write', 'file:read', 'file:write',
    'collab:read', 'collab:write', 'openapi:read', 'openapi:write',
    'monitor:read', 'monitor:write')
  AND p.`deleted` = 0;

-- ------------------------------------------------------------
-- 4. 校验（执行完自己看）
-- ------------------------------------------------------------
SELECT `permission_code`, `type`, `parent_id` FROM `db_user`.`sys_permission`
WHERE `permission_code` IN ('schedule','schedule:read','schedule:write','ai','ai:read','ai:write',
                            'file','file:read','file:write','collab','collab:read','collab:write',
                            'openapi','openapi:read','openapi:write','monitor:read','monitor:write')
  AND `deleted` = 0
ORDER BY `type` DESC, `permission_code`;

SELECT COUNT(*) AS role1_rows_for_new_codes
FROM `db_user`.`sys_role_permission` rp
JOIN `db_user`.`sys_permission` p ON p.`id` = rp.`permission_id`
WHERE rp.`role_id` = 1 AND p.`deleted` = 0
  AND p.`permission_code` IN ('schedule','ai','file','collab','openapi',
    'schedule:read','schedule:write','ai:read','ai:write','file:read','file:write',
    'collab:read','collab:write','openapi:read','openapi:write',
    'monitor:read','monitor:write');
-- 期望 17（5 个目录 + 12 个两档码）。少于 17 就是第 3 节没跑成。

-- ------------------------------------------------------------
-- 5. 【不执行，等拍板】role 2（admin）/ role 3（user）的授权矩阵草案
--    本轮的码一旦生效，非 super_admin 的默认值仍是"什么都干不了"，因为这两个角色在
--    sys_role_permission 里只有读码（dept 那轮给过 system:dept:list）。要发新权限得先定：
--      ① role 3（普通用户）要不要 `collab:write`？不给的话评论/订阅功能对他就是只读，
--         而这两个动作是**本人**的行为（不是管理动作），按模块粒度会被当成管理面。
--      ② `file:write` 给谁？大屏背景图上传打的就是 /api/file/upload，设计端 role 2 不给写码
--         会连背景都换不了 —— 这是本轮最容易当场露馅的一条。
--      ③ `openapi:write`（铸对外凭据）建议只给 role 2，且 role 2 本身要不要给得单独点头。
--      ④ `ai:write` 有真实现金成本，`monitor:write` 封的是机器端点，两者都建议先不给。
--    定完按本脚本第 3 节的形态追加 INSERT IGNORE，并且记得：改完授权要么让用户重新登录，
--    要么走接口触发 LoginSessionEvictor（直改库不会驱逐 Redis 快照，D55 边界①）。
-- ------------------------------------------------------------
-- INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
-- SELECT 2, p.`id` FROM `db_user`.`sys_permission` p
-- WHERE p.`deleted` = 0 AND p.`permission_code` IN (
--     'schedule:read','ai:read','file:read','file:write','collab:read','collab:write',
--     'openapi:read','monitor:read');
-- INSERT IGNORE INTO `db_user`.`sys_role_permission` (`role_id`, `permission_id`)
-- SELECT 3, p.`id` FROM `db_user`.`sys_permission` p
-- WHERE p.`deleted` = 0 AND p.`permission_code` IN ('file:read','file:write','collab:read');
