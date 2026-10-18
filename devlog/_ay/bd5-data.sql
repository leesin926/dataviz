SET NAMES utf8mb4;
-- BD5 大屏静态数据回读：每块一行一记录，分隔符 ~|~（-B 模式会把真 tab 转义成字面 \t，不能用 tab）。
-- 目的：大屏上显示的每个数字都来自 BD2 造的演示数据层实查值，不是手写的好看数。
USE db_analysis;

-- kpi：全盘口径（销售额/订单/毛利/客单/目标达成/毛利率/门店数）
SELECT CONCAT('@@KPI ~|~ ',
  CAST(ROUND(SUM(amount), 2) AS CHAR), ' ~|~ ',
  CAST(SUM(order_count) AS CHAR), ' ~|~ ',
  CAST(ROUND(SUM(profit), 2) AS CHAR), ' ~|~ ',
  CAST(ROUND(SUM(amount) / SUM(order_count), 2) AS CHAR), ' ~|~ ',
  CAST(ROUND(SUM(amount) / SUM(target_amount) * 100, 1) AS CHAR), ' ~|~ ',
  CAST(ROUND(SUM(profit) / SUM(amount) * 100, 1) AS CHAR), ' ~|~ ',
  CAST(COUNT(*) AS CHAR))
FROM ads_store_performance;

-- trend：最近 14 天日销售额与毛利（先按日期取 14 天，再回到明细聚合 —— 直接 LIMIT 14 行拿到的是同一天的多条）
SELECT CONCAT('@@TREND ~|~ ', DATE_FORMAT(x.stat_date, '%m-%d'), ' ~|~ ',
  CAST(ROUND(SUM(d.amount), 0) AS CHAR), ' ~|~ ', CAST(ROUND(SUM(d.profit), 0) AS CHAR))
FROM (SELECT stat_date FROM dwd_sales_daily GROUP BY stat_date ORDER BY stat_date DESC LIMIT 14) x
  JOIN dwd_sales_daily d ON d.stat_date = x.stat_date
GROUP BY x.stat_date ORDER BY x.stat_date ASC;

-- region：大区销售额/毛利/订单
SELECT CONCAT('@@REGION ~|~ ', region, ' ~|~ ', CAST(ROUND(SUM(amount), 0) AS CHAR), ' ~|~ ',
  CAST(ROUND(SUM(profit), 0) AS CHAR), ' ~|~ ', CAST(SUM(order_count) AS CHAR))
FROM dwd_sales_daily GROUP BY region ORDER BY SUM(amount) DESC;

-- category：类目销售额
SELECT CONCAT('@@CAT ~|~ ', category, ' ~|~ ', CAST(ROUND(SUM(amount), 0) AS CHAR))
FROM dwd_sales_daily GROUP BY category ORDER BY SUM(amount) DESC;

-- channel×category：堆叠柱（最后一个维度当系列）
SELECT CONCAT('@@CC ~|~ ', channel, ' ~|~ ', category, ' ~|~ ', CAST(ROUND(SUM(amount), 0) AS CHAR))
FROM dwd_sales_daily GROUP BY channel, category ORDER BY channel, category;

-- store_top：门店销售额 TOP10
SELECT CONCAT('@@STORE ~|~ ', store_name, ' ~|~ ', CAST(ROUND(amount, 0) AS CHAR), ' ~|~ ',
  CAST(achievement_rate AS CHAR))
FROM ads_store_performance ORDER BY amount DESC LIMIT 10;

-- rate：全部门店的平均达成率（简单平均，非加权）
--   单独探一条是因为 901 的仪表盘的 911 的仪表盘是同一个指标；
--   早先 901 拿 TOP10 的均值(107.0)当"门店平均达成率"，跟 911 全量 AVG(107.58) 对不上。
SELECT CONCAT('@@RATE ~|~ ', CAST(ROUND(AVG(achievement_rate), 1) AS CHAR), ' ~|~ ',
  CAST(COUNT(*) AS CHAR))
FROM ads_store_performance;

-- hour：时段成交
SELECT CONCAT('@@HOUR ~|~ ', LPAD(stat_hour, 2, '0'), ' ~|~ ', CAST(orders AS CHAR), ' ~|~ ',
  CAST(ROUND(amount, 0) AS CHAR))
FROM ads_hour_heat ORDER BY stat_hour ASC;

-- member：会员等级价值
SELECT CONCAT('@@MEMBER ~|~ ', member_level, ' ~|~ ', CAST(ROUND(amount, 0) AS CHAR), ' ~|~ ',
  CAST(members AS CHAR), ' ~|~ ', CAST(repurchase_rate AS CHAR))
FROM ads_member_value ORDER BY amount DESC;

-- feed：实时流水最近 10 笔
SELECT CONCAT('@@FEED ~|~ ', DATE_FORMAT(feed_time, '%H:%i:%s'), ' ~|~ ', store_name, ' ~|~ ',
  category, ' ~|~ ', channel, ' ~|~ ', CAST(amount AS CHAR))
FROM ads_realtime_feed ORDER BY feed_time DESC LIMIT 10;

-- prog：大区目标达成（进度条；target_amount 已在 BD2 从 demo_stores.monthly_target 落进 ADS，不必再跨库）
SELECT CONCAT('@@PROG ~|~ ', region, ' ~|~ ', CAST(ROUND(SUM(amount), 0) AS CHAR), ' ~|~ ',
  CAST(ROUND(SUM(target_amount), 0) AS CHAR), ' ~|~ ',
  CAST(ROUND(SUM(amount) / SUM(target_amount) * 100, 1) AS CHAR))
FROM ads_store_performance GROUP BY region ORDER BY SUM(amount) DESC;

-- window：数据窗口（大屏副标题写真实区间，不写猜出来的日期）
SELECT CONCAT('@@WINDOW ~|~ ', DATE_FORMAT(MIN(stat_date), '%Y-%m-%d'), ' ~|~ ',
  DATE_FORMAT(MAX(stat_date), '%Y-%m-%d'), ' ~|~ ', CAST(COUNT(DISTINCT stat_date) AS CHAR))
FROM dwd_sales_daily;

-- wow：最近 7 日 vs 前 7 日的环比（指标卡 trend 用真实计算值）
SELECT CONCAT('@@WOW ~|~ ', k.tag, ' ~|~ ', CAST(ROUND((k.recent / k.prev - 1) * 100, 1) AS CHAR))
FROM (
  SELECT 'amount' AS tag,
    (SELECT SUM(amount) FROM dwd_sales_daily WHERE stat_date > DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 7 DAY)) AS recent,
    (SELECT SUM(amount) FROM dwd_sales_daily WHERE stat_date > DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 14 DAY)
       AND stat_date <= DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 7 DAY)) AS prev
  UNION ALL
  SELECT 'orders',
    (SELECT SUM(order_count) FROM dwd_sales_daily WHERE stat_date > DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 7 DAY)),
    (SELECT SUM(order_count) FROM dwd_sales_daily WHERE stat_date > DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 14 DAY)
       AND stat_date <= DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 7 DAY))
  UNION ALL
  SELECT 'profit',
    (SELECT SUM(profit) FROM dwd_sales_daily WHERE stat_date > DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 7 DAY)),
    (SELECT SUM(profit) FROM dwd_sales_daily WHERE stat_date > DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 14 DAY)
       AND stat_date <= DATE_SUB((SELECT MAX(stat_date) FROM dwd_sales_daily), INTERVAL 7 DAY))
) k WHERE k.prev > 0;
