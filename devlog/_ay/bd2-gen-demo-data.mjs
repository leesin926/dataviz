/**
 * 阶段 BD2：生成演示数据层 SQL（业务库 + ODS/DWD/DWS/ADS 分层）。
 *
 * 为什么要脚本而不是手写 SQL：金额、达成率、环比这些数字要在「明细 → 汇总 → 大屏 KPI」之间对得上，
 * 手抄一遍就必然漂。这里业务明细行由脚本按确定性伪随机生成，派生层一律交给 MySQL 的
 * INSERT..SELECT 现算，所以分层对账是数据库自己保证的，不是我保证的。
 *
 * 产出：deploy/sql/patch/2026-10-18-bd-demo-data.sql
 * 用法：node devlog/_ay/bd2-gen-demo-data.mjs
 */
import { writeFileSync } from 'node:fs'

/* ---------------- 确定性伪随机（同一台机器重跑得到同一份数据） ---------------- */
function mulberry32(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const rnd = mulberry32(20261018)
const pick = (list) => list[Math.floor(rnd() * list.length) % list.length]
const range = (min, max) => min + rnd() * (max - min)
const weighted = (entries) => {
  const total = entries.reduce((s, [, w]) => s + w, 0)
  let roll = rnd() * total
  for (const [value, w] of entries) {
    roll -= w
    if (roll <= 0) return value
  }
  return entries[entries.length - 1][0]
}

/* ---------------- 业务口径 ---------------- */
const DAY_START = '2026-07-21'
const DAYS = 90 // 2026-07-21 .. 2026-10-18（含今天，演示不能出现"近 30 天为空"）

const REGIONS = [
  { name: '华东', w: 1.3, spend: 1.15, cities: ['上海', '杭州', '南京', '苏州'] },
  { name: '华南', w: 1.1, spend: 1.08, cities: ['广州', '深圳', '厦门'] },
  { name: '华北', w: 1.0, spend: 1.0, cities: ['北京', '天津', '石家庄'] },
  { name: '西南', w: 0.8, spend: 0.92, cities: ['成都', '重庆', '昆明'] },
  { name: '东北', w: 0.6, spend: 0.86, cities: ['沈阳', '大连', '长春'] },
  { name: '西北', w: 0.45, spend: 0.8, cities: ['西安', '兰州', '乌鲁木齐'] },
]
const CATEGORIES = [
  { name: '生鲜', w: 1.35, min: 60, max: 420, margin: 0.18 },
  { name: '数码', w: 1.1, min: 780, max: 5800, margin: 0.12 },
  { name: '服饰', w: 0.95, min: 150, max: 920, margin: 0.42 },
  { name: '家居', w: 0.75, min: 210, max: 1560, margin: 0.3 },
  { name: '美妆', w: 0.65, min: 110, max: 780, margin: 0.48 },
]
const CHANNELS = [
  { name: '门店', w: 0.42 },
  { name: '小程序', w: 0.31 },
  { name: 'APP', w: 0.16 },
  { name: '第三方平台', w: 0.11 },
]
const LEVELS = [
  { name: '普通', w: 0.55, factor: 1.0 },
  { name: '银卡', w: 0.25, factor: 1.06 },
  { name: '金卡', w: 0.14, factor: 1.18 },
  { name: '钻石', w: 0.06, factor: 1.35 },
]
const STATUS = [
  { name: 'PAID', w: 0.54 },
  { name: 'SHIPPED', w: 0.34 },
  { name: 'REFUNDING', w: 0.07 },
  { name: 'CANCELLED', w: 0.05 },
]
// 星期系数：周末明显放量，趋势线才有"形状"而不是一条平的
const WEEKDAY_FACTOR = [1.28, 0.82, 0.85, 0.9, 0.95, 1.12, 1.35] // 0=周日
// 大促日历：单日尖峰 + 国庆周整体抬升
const PROMO = { '2026-08-08': 1.8, '2026-09-09': 2.05, '2026-09-28': 1.3, '2026-10-01': 1.9, '2026-10-02': 1.7, '2026-10-03': 1.55, '2026-10-07': 1.35 }
const HOUR_SLOTS = [
  [10, 0.05], [11, 0.08], [12, 0.12], [13, 0.09], [14, 0.06], [15, 0.05], [16, 0.06],
  [17, 0.08], [18, 0.1], [19, 0.13], [20, 0.12], [21, 0.07],
]

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const base = new Date(`${DAY_START}T00:00:00`)
const dates = Array.from({ length: DAYS }, (_, i) => {
  const d = new Date(base.getTime() + i * 86400000)
  return { date: iso(d), dow: d.getDay(), i }
})

/* ---------------- 门店 ---------------- */
const CITY_GEO = {
  上海: [121.47, 31.23], 杭州: [120.16, 30.27], 南京: [118.78, 32.06], 苏州: [120.62, 31.32],
  广州: [113.28, 23.13], 深圳: [114.06, 22.55], 厦门: [118.09, 24.48], 北京: [116.4, 39.9],
  天津: [117.2, 39.13], 石家庄: [114.51, 38.04], 成都: [104.06, 30.67], 重庆: [106.55, 29.56],
  昆明: [102.83, 24.88], 沈阳: [123.43, 41.8], 大连: [121.62, 38.91], 长春: [125.32, 43.9],
  西安: [108.94, 34.34], 兰州: [103.83, 36.06], 乌鲁木齐: [87.62, 43.83],
}
const DISTRICTS = ['中心', '新城', '老城', '滨江', '经开', '高新']
const stores = []
let sid = 501
for (const r of REGIONS) {
  for (const city of r.cities) {
    const n = city === '上海' || city === '北京' ? 2 : 1
    for (let k = 0; k < n; k++) {
      const district = pick(DISTRICTS)
      const [lon, lat] = CITY_GEO[city]
      stores.push({
        storeId: sid++,
        storeName: `优选${city}${district}${n > 1 ? `${k + 1}` : ''}店`,
        region: r.name,
        city,
        lon: +(lon + range(-0.06, 0.06)).toFixed(6),
        lat: +(lat + range(-0.05, 0.05)).toFixed(6),
        openDate: iso(new Date(2019 + Math.floor(rnd() * 6), Math.floor(rnd() * 12), 1 + Math.floor(rnd() * 27))),
        areaSqm: Math.round(range(320, 2600)),
        manager: `店长${sid}`,
        monthlyTarget: 0, // 由实际门店营收反推，让达成率落在 80%~135% 这段"可讲"的区间
      })
    }
  }
}
const storesByRegion = {}
for (const s of stores) (storesByRegion[s.region] ||= []).push(s)

/* ---------------- 会员 ---------------- */
const members = []
for (let i = 0; i < 600; i++) {
  const r = weighted(REGIONS.map((x) => [x, x.w]))
  const level = weighted(LEVELS.map((x) => [x, x.w]))
  const join = new Date(2023 + Math.floor(rnd() * 4), Math.floor(rnd() * 12), 1 + Math.floor(rnd() * 27))
  members.push({
    memberId: 9001 + i,
    level: level.name,
    region: r.name,
    city: pick(r.cities),
    joinDate: iso(join),
    points: Math.round(range(120, 48000)),
    totalSpent: 0, // 由订单回填
    orderCount: 0,
  })
}

/* ---------------- 订单明细 ---------------- */
const orders = []
let oid = 90000
for (const day of dates) {
  const promo = PROMO[day.date] || 1
  const growth = 1 + 0.0035 * day.i
  const count = Math.max(6, Math.round(20 * WEEKDAY_FACTOR[day.dow] * growth * promo))
  for (let k = 0; k < count; k++) {
    const region = weighted(REGIONS.map((x) => [x, x.w]))
    const category = weighted(CATEGORIES.map((x) => [x, x.w]))
    const channel = weighted(CHANNELS.map((x) => [x, x.w]))
    const status = weighted(STATUS.map((x) => [x, x.w]))
    const store = pick(storesByRegion[region.name])
    const isMember = rnd() < 0.62
    const member = isMember ? pick(members) : null
    const level = member ? LEVELS.find((l) => l.name === member.level) : { factor: 0.95 }
    const qty = Math.round(range(1, 4))
    const unit = range(category.min, category.max)
    const amount = +(unit * qty * level.factor * region.spend * (0.85 + 0.3 * rnd())).toFixed(2)
    const profit = +(amount * category.margin * (0.85 + 0.3 * rnd())).toFixed(2)
    const hour = weighted(HOUR_SLOTS)
    const minute = Math.floor(rnd() * 60)
    const second = Math.floor(rnd() * 60)
    if (member) {
      if (status === 'PAID' || status === 'SHIPPED') {
        member.totalSpent = +(member.totalSpent + amount).toFixed(2)
        member.orderCount += 1
      }
    }
    orders.push({
      orderId: ++oid,
      userId: member ? member.memberId : 0,
      region: region.name,
      payAmount: amount,
      status: status.name,
      createdAt: `${day.date} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`,
      channel: channel.name,
      category: category.name,
      storeId: store.storeId,
      qty,
      profit,
    })
  }
}

// 门店季度目标 = 门店近 90 天有效营收 / 一个 0.80~1.35 的系数，达成率因此天然落在"可讲"的区间
const storeRevenue = {}
for (const o of orders) {
  if (o.status === 'PAID' || o.status === 'SHIPPED') storeRevenue[o.storeId] = (storeRevenue[o.storeId] || 0) + o.payAmount
}
for (const s of stores) {
  s.monthlyTarget = +((storeRevenue[s.storeId] || 0) / range(0.8, 1.35)).toFixed(2)
}

/* ---------------- SQL 产出 ---------------- */
const q = (v) => (typeof v === 'number' ? String(v) : `'${String(v).replace(/'/g, "''")}'`)

function valuesOf(row, mapper) {
  return mapper(row)
    .map((v) => (typeof v === 'number' ? String(v) : q(v)))
    .join(', ')
}

function chunkInsert(table, columns, rows, mapper, size = 400) {
  const out = []
  for (let i = 0; i < rows.length; i += size) {
    const tuples = rows
      .slice(i, i + size)
      .map((r) => '  (' + valuesOf(r, mapper) + ')')
      .join(',\n')
    out.push('INSERT INTO ' + table + ' (' + columns.join(', ') + ') VALUES\n' + tuples + ';')
  }
  return out.join('\n')
}

const sql = `-- 阶段 BD2 · 演示数据层（优选零售 90 天经营数据）
-- 生成器：devlog/_ay/bd2-gen-demo-data.mjs（数据由脚本按确定性伪随机产出，重跑同一份）
-- 口径：
--   · 窗口 ${DAY_START} .. 2026-10-18（含今天，演示不能出现"近 30 天为空"）
--   · 有效成交 = demo_orders.status IN ('PAID','SHIPPED')，REFUNDING/CANCELLED 不入分析层
--   · 派生层（ods→dwd→dws→ads）一律 INSERT..SELECT 现场聚合，分层对账由 MySQL 保证
--   · 目标表只 TRUNCATE 我自己建的 bd 演示表；demo_orders 重建前先落 zz_bak 备份表
-- 执行（缺 --default-character-set=utf8mb4 会把中文写成 ?，不可逆）：
--   docker exec -i dataviz-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -B' < deploy/sql/patch/2026-10-18-bd-demo-data.sql
SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 200000;

-- ============ 0. 备份（只在缺失时建，重跑不会覆盖首份快照） ============
CREATE TABLE IF NOT EXISTS db_model.zz_bak_20261018_demo_orders AS SELECT * FROM db_model.demo_orders;

-- ============ 1. 业务库 db_model（数据源 902 指向这里） ============
USE db_model;

DROP TABLE IF EXISTS demo_stores;
CREATE TABLE demo_stores (
  store_id        BIGINT       NOT NULL COMMENT '门店ID',
  store_name      VARCHAR(64)  NOT NULL COMMENT '门店名称',
  region          VARCHAR(32)  NOT NULL COMMENT '所属大区',
  city            VARCHAR(32)  NOT NULL COMMENT '所在城市',
  lon             DECIMAL(9,6) NOT NULL COMMENT '经度',
  lat             DECIMAL(9,6) NOT NULL COMMENT '纬度',
  open_date       DATE         NOT NULL COMMENT '开业日期',
  area_sqm        INT          NOT NULL COMMENT '营业面积（平方米）',
  manager         VARCHAR(32)  NOT NULL COMMENT '店长',
  monthly_target  DECIMAL(18,2) NOT NULL COMMENT '季度销售目标（元）',
  PRIMARY KEY (store_id),
  KEY idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店主数据（演示）';

DROP TABLE IF EXISTS demo_members;
CREATE TABLE demo_members (
  member_id    BIGINT        NOT NULL COMMENT '会员ID',
  level        VARCHAR(16)   NOT NULL COMMENT '会员等级：普通/银卡/金卡/钻石',
  region       VARCHAR(32)   NOT NULL COMMENT '常去大区',
  city         VARCHAR(32)   NOT NULL COMMENT '常去城市',
  join_date    DATE          NOT NULL COMMENT '入会日期',
  points       INT           NOT NULL COMMENT '当前积分',
  total_spent  DECIMAL(18,2) NOT NULL COMMENT '累计有效消费（元）',
  order_count  INT           NOT NULL COMMENT '累计有效订单数',
  PRIMARY KEY (member_id),
  KEY idx_level (level),
  KEY idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员主数据（演示）';

-- demo_orders 重建：保留 model_dataset 903 已引用的 6 列，追加分析需要的 5 列
DROP TABLE IF EXISTS demo_orders;
CREATE TABLE demo_orders (
  order_id    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单号',
  user_id     BIGINT        NOT NULL COMMENT '会员ID，0=散客',
  region      VARCHAR(32)   NOT NULL COMMENT '下单大区',
  pay_amount  DECIMAL(18,2) NOT NULL COMMENT '实付金额（元）',
  status      VARCHAR(16)   NOT NULL COMMENT '订单状态：PAID/SHIPPED/REFUNDING/CANCELLED',
  created_at  DATETIME      NOT NULL COMMENT '下单时间',
  channel     VARCHAR(32)   NOT NULL COMMENT '成交渠道',
  category    VARCHAR(32)   NOT NULL COMMENT '商品品类',
  store_id    BIGINT        NOT NULL COMMENT '履约门店',
  qty         INT           NOT NULL COMMENT '件数',
  profit      DECIMAL(18,2) NOT NULL COMMENT '毛利（元）',
  PRIMARY KEY (order_id),
  KEY idx_created (created_at),
  KEY idx_region (region),
  KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细（演示业务库）';

${chunkInsert('demo_stores', ['store_id', 'store_name', 'region', 'city', 'lon', 'lat', 'open_date', 'area_sqm', 'manager', 'monthly_target'], stores, (s) => [s.storeId, s.storeName, s.region, s.city, s.lon, s.lat, s.openDate, s.areaSqm, s.manager, s.monthlyTarget])}

${chunkInsert('demo_members', ['member_id', 'level', 'region', 'city', 'join_date', 'points', 'total_spent', 'order_count'], members, (m) => [m.memberId, m.level, m.region, m.city, m.joinDate, m.points, +m.totalSpent.toFixed(2), m.orderCount])}

${chunkInsert('demo_orders', ['order_id', 'user_id', 'region', 'pay_amount', 'status', 'created_at', 'channel', 'category', 'store_id', 'qty', 'profit'], orders, (o) => [o.orderId, o.userId, o.region, o.payAmount, o.status, o.createdAt, o.channel, o.category, o.storeId, o.qty, o.profit])}

-- ============ 2. 分析库 db_analysis（数据源 901 指向这里，ETL 落地层） ============
USE db_analysis;

DROP TABLE IF EXISTS ods_order_detail;
CREATE TABLE ods_order_detail (
  order_id      BIGINT        NOT NULL COMMENT '订单号（ETL 幂等键）',
  stat_date     DATE          NOT NULL COMMENT '下单日期',
  stat_hour     TINYINT       NOT NULL COMMENT '下单小时',
  region        VARCHAR(32)   NOT NULL COMMENT '大区',
  city          VARCHAR(32)   NOT NULL COMMENT '城市',
  store_id      BIGINT        NOT NULL COMMENT '门店ID',
  store_name    VARCHAR(64)   NOT NULL COMMENT '门店名称',
  channel       VARCHAR(32)   NOT NULL COMMENT '渠道',
  category      VARCHAR(32)   NOT NULL COMMENT '品类',
  member_id     BIGINT        NOT NULL COMMENT '会员ID，0=散客',
  member_level  VARCHAR(16)   NOT NULL COMMENT '会员等级，散客记"散客"',
  pay_amount    DECIMAL(18,2) NOT NULL COMMENT '实付金额',
  profit        DECIMAL(18,2) NOT NULL COMMENT '毛利',
  qty           INT           NOT NULL COMMENT '件数',
  order_status  VARCHAR(16)   NOT NULL COMMENT '订单状态',
  created_at    DATETIME      NOT NULL COMMENT '下单时间',
  loaded_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入仓时间',
  PRIMARY KEY (order_id),
  KEY idx_date_region (stat_date, region),
  KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ODS：有效订单明细（由 etl_task 911 抽取）';

DROP TABLE IF EXISTS dwd_sales_daily;
CREATE TABLE dwd_sales_daily (
  stat_date    DATE          NOT NULL COMMENT '统计日期',
  region       VARCHAR(32)   NOT NULL COMMENT '大区',
  category     VARCHAR(32)   NOT NULL COMMENT '品类',
  channel      VARCHAR(32)   NOT NULL COMMENT '渠道',
  amount       DECIMAL(18,2) NOT NULL COMMENT '销售额',
  profit       DECIMAL(18,2) NOT NULL COMMENT '毛利',
  order_count  INT           NOT NULL COMMENT '订单量',
  qty          INT           NOT NULL COMMENT '件数',
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '刷新时间',
  PRIMARY KEY (stat_date, region, category, channel),
  KEY idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DWD：日×大区×品类×渠道销售汇总（由 etl_task 912 聚合）';

DROP TABLE IF EXISTS dws_store_sales;
CREATE TABLE dws_store_sales (
  store_id        BIGINT        NOT NULL COMMENT '门店ID',
  store_name      VARCHAR(64)   NOT NULL COMMENT '门店名称',
  region          VARCHAR(32)   NOT NULL COMMENT '大区',
  city            VARCHAR(32)   NOT NULL COMMENT '城市',
  amount          DECIMAL(18,2) NOT NULL COMMENT '销售额',
  profit          DECIMAL(18,2) NOT NULL COMMENT '毛利',
  order_count     INT           NOT NULL COMMENT '订单量',
  avg_pay_amount  DECIMAL(18,2) NOT NULL COMMENT '客单价',
  PRIMARY KEY (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DWS：门店销售宽表（由 etl_task 913 聚合）';

DROP TABLE IF EXISTS dws_member_level;
CREATE TABLE dws_member_level (
  member_level   VARCHAR(16)   NOT NULL COMMENT '会员等级',
  amount         DECIMAL(18,2) NOT NULL COMMENT '销售额',
  order_count    INT           NOT NULL COMMENT '订单量',
  avg_pay_amount DECIMAL(18,2) NOT NULL COMMENT '客单价',
  PRIMARY KEY (member_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DWS：会员等级销售汇总（由 etl_task 914 聚合）';

DROP TABLE IF EXISTS ads_store_performance;
CREATE TABLE ads_store_performance (
  store_id          BIGINT        NOT NULL COMMENT '门店ID',
  store_name        VARCHAR(64)   NOT NULL COMMENT '门店名称',
  region            VARCHAR(32)   NOT NULL COMMENT '大区',
  city              VARCHAR(32)   NOT NULL COMMENT '城市',
  amount            DECIMAL(18,2) NOT NULL COMMENT '销售额',
  profit            DECIMAL(18,2) NOT NULL COMMENT '毛利',
  order_count       INT           NOT NULL COMMENT '订单量',
  avg_pay_amount    DECIMAL(18,2) NOT NULL COMMENT '客单价',
  target_amount     DECIMAL(18,2) NOT NULL COMMENT '目标',
  achievement_rate  DECIMAL(8,2)  NOT NULL COMMENT '达成率（%）',
  margin_rate       DECIMAL(8,2)  NOT NULL COMMENT '毛利率（%）',
  PRIMARY KEY (store_id),
  KEY idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADS：门店绩效（dws 关联目标现算）';

DROP TABLE IF EXISTS ads_member_value;
CREATE TABLE ads_member_value (
  member_level      VARCHAR(16)   NOT NULL COMMENT '会员等级',
  members           INT           NOT NULL COMMENT '有单会员数',
  amount            DECIMAL(18,2) NOT NULL COMMENT '销售额',
  avg_amount        DECIMAL(18,2) NOT NULL COMMENT '人均贡献',
  order_count       INT           NOT NULL COMMENT '订单量',
  repurchase_rate   DECIMAL(8,2)  NOT NULL COMMENT '复购率（%）',
  PRIMARY KEY (member_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADS：会员价值分层';

DROP TABLE IF EXISTS ads_hour_heat;
CREATE TABLE ads_hour_heat (
  stat_hour   TINYINT       NOT NULL COMMENT '小时',
  orders      INT           NOT NULL COMMENT '订单量',
  amount      DECIMAL(18,2) NOT NULL COMMENT '销售额',
  avg_amount  DECIMAL(18,2) NOT NULL COMMENT '客单价',
  PRIMARY KEY (stat_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADS：时段分布';

DROP TABLE IF EXISTS ads_realtime_feed;
CREATE TABLE ads_realtime_feed (
  feed_id     BIGINT        NOT NULL COMMENT '订单号',
  feed_time   DATETIME      NOT NULL COMMENT '成交时间',
  store_name  VARCHAR(64)   NOT NULL COMMENT '门店',
  city        VARCHAR(32)   NOT NULL COMMENT '城市',
  category    VARCHAR(32)   NOT NULL COMMENT '品类',
  channel     VARCHAR(32)   NOT NULL COMMENT '渠道',
  amount      DECIMAL(18,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (feed_id),
  KEY idx_time (feed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADS：实时成交流水（最近 40 笔）';

-- ---- 分层装载：与 etl_task 911/912/913/914 的 DAG 语义一一对应 ----
-- etl 911：业务库有效订单 → ODS（input.sql 节点原文见 db_etl.etl_task.transform_config）
INSERT INTO db_analysis.ods_order_detail
  (order_id, stat_date, stat_hour, region, city, store_id, store_name, channel, category,
   member_id, member_level, pay_amount, profit, qty, order_status, created_at)
SELECT o.order_id,
       DATE(o.created_at),
       HOUR(o.created_at),
       o.region,
       s.city,
       o.store_id,
       s.store_name,
       o.channel,
       o.category,
       o.user_id,
       IFNULL(m.level, '散客'),
       o.pay_amount,
       o.profit,
       o.qty,
       o.status,
       o.created_at
  FROM db_model.demo_orders o
  JOIN db_model.demo_stores s ON s.store_id = o.store_id
  LEFT JOIN db_model.demo_members m ON m.member_id = o.user_id
 WHERE o.status IN ('PAID', 'SHIPPED');

-- etl 912：ODS → DWD 日汇总（分组键必须是普通列，所以 ODS 里预先投影出 stat_date）
INSERT INTO db_analysis.dwd_sales_daily (stat_date, region, category, channel, amount, profit, order_count, qty)
SELECT stat_date, region, category, channel,
       SUM(pay_amount), SUM(profit), COUNT(order_id), SUM(qty)
  FROM db_analysis.ods_order_detail
 GROUP BY stat_date, region, category, channel;

-- etl 913：ODS → DWS 门店汇总
INSERT INTO db_analysis.dws_store_sales
  (store_id, store_name, region, city, amount, profit, order_count, avg_pay_amount)
SELECT store_id, store_name, region, city,
       SUM(pay_amount), SUM(profit), COUNT(order_id), AVG(pay_amount)
  FROM db_analysis.ods_order_detail
 GROUP BY store_id, store_name, region, city;

-- etl 914：ODS 会员单 → DWS 等级汇总（member_id > 0 由过滤节点给出）
INSERT INTO db_analysis.dws_member_level (member_level, amount, order_count, avg_pay_amount)
SELECT member_level, SUM(pay_amount), COUNT(order_id), AVG(pay_amount)
  FROM db_analysis.ods_order_detail
 WHERE member_id > 0
 GROUP BY member_level;

-- ADS 层：门店绩效 = DWS 关联目标；达成率是演示里最容易被质疑的数，所以在这里现算而不是写死
INSERT INTO db_analysis.ads_store_performance
  (store_id, store_name, region, city, amount, profit, order_count, avg_pay_amount, target_amount, achievement_rate, margin_rate)
SELECT d.store_id, d.store_name, d.region, d.city, d.amount, d.profit, d.order_count, d.avg_pay_amount,
       s.monthly_target,
       ROUND(d.amount / NULLIF(s.monthly_target, 0) * 100, 2),
       ROUND(d.profit / NULLIF(d.amount, 0) * 100, 2)
  FROM db_analysis.dws_store_sales d
  JOIN db_model.demo_stores s ON s.store_id = d.store_id;

INSERT INTO db_analysis.ads_member_value (member_level, members, amount, avg_amount, order_count, repurchase_rate)
SELECT t.member_level,
       t.members,
       t.amount,
       ROUND(t.amount / NULLIF(t.members, 0), 2),
       t.order_count,
       ROUND(t.repeat_members / NULLIF(t.members, 0) * 100, 2)
  FROM (SELECT o.member_level,
               COUNT(DISTINCT o.member_id) AS members,
               COUNT(DISTINCT CASE WHEN o.cnt >= 2 THEN o.member_id END) AS repeat_members,
               SUM(o.amount) AS amount,
               SUM(o.cnt) AS order_count
          FROM (SELECT member_level, member_id, SUM(pay_amount) AS amount, COUNT(order_id) AS cnt
                  FROM db_analysis.ods_order_detail
                 WHERE member_id > 0
                 GROUP BY member_level, member_id) o
         GROUP BY o.member_level) t;

INSERT INTO db_analysis.ads_hour_heat (stat_hour, orders, amount, avg_amount)
SELECT stat_hour, COUNT(order_id), SUM(pay_amount), AVG(pay_amount)
  FROM db_analysis.ods_order_detail
 GROUP BY stat_hour;

INSERT INTO db_analysis.ads_realtime_feed (feed_id, feed_time, store_name, city, category, channel, amount)
SELECT order_id, created_at, store_name, city, category, channel, pay_amount
  FROM db_analysis.ods_order_detail
 ORDER BY created_at DESC
 LIMIT 40;

-- ============ 3. 自检：编码没坏 + 分层对账得上 ============
SELECT '编码自检(应为中文而非 ?)' AS check_name, store_name AS value FROM db_model.demo_stores WHERE store_id = 501;
SELECT '业务库订单行数' AS check_name, COUNT(*) AS value FROM db_model.demo_orders;
SELECT 'ODS 行数' AS check_name, COUNT(*) AS value FROM db_analysis.ods_order_detail;
SELECT 'DWD 行数' AS check_name, COUNT(*) AS value FROM db_analysis.dwd_sales_daily;
SELECT '对账 DWD-ODS 销售额差(必须 0.00)' AS check_name,
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.dwd_sales_daily) -
       (SELECT ROUND(SUM(pay_amount), 2) FROM db_analysis.ods_order_detail) AS value;
SELECT '对账 DWS-ODS 销售额差(必须 0.00)' AS check_name,
       (SELECT ROUND(SUM(amount), 2) FROM db_analysis.dws_store_sales) -
       (SELECT ROUND(SUM(pay_amount), 2) FROM db_analysis.ods_order_detail) AS value;
SELECT '门店达成率区间' AS check_name,
       CONCAT(MIN(achievement_rate), '% ~ ', MAX(achievement_rate), '%') AS value FROM db_analysis.ads_store_performance;
`;

writeFileSync(new URL('../../deploy/sql/patch/2026-10-18-bd-demo-data.sql', import.meta.url), sql, 'utf8')

const totals = orders.filter((o) => o.status === 'PAID' || o.status === 'SHIPPED')
console.log(
  JSON.stringify(
    {
      orders: orders.length,
      validOrders: totals.length,
      stores: stores.length,
      members: members.length,
      window: [DAY_START, '2026-10-18'],
      validAmount: +totals.reduce((s, o) => s + o.payAmount, 0).toFixed(2),
      validProfit: +totals.reduce((s, o) => s + o.profit, 0).toFixed(2),
    },
    null,
    2,
  ),
)
