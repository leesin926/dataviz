/**
 * BD3 等价性自检：每条 DAG 的转换语义 vs 2026-10-18-bd-demo-data.sql 实际装载出来的表。
 * 两边对不上的话，界面上点一次「执行」就会算出另一套数 —— 演示里同一张图出现两个数是最难看的错。
 *
 * 聚合算子在 Java 侧用 double 累加、按组四舍五入到 2 位，与 MySQL 的 DECIMAL 精确累加最多漂几分钱，
 * 所以聚合类任务给 0.10 容差并把实际差值打出来；纯 SQL 直取（ods/ads_store/ads_member/ads_feed）要求分毫不差。
 */
const fs = require('fs')

const EXPECT = {
  ods: ['911 ODS 订单明细', 'DAG 源端 SQL 的行数与销售额 == ods_order_detail', 0],
  dwd: ['912 DWD 日汇总', '按 日期×大区×品类×渠道 重算 == dwd_sales_daily', 0.1],
  dws_store: ['913 DWS 门店汇总', '按门店重算 == dws_store_sales', 0.1],
  dws_member: ['914 DWS 会员等级', '剔散客后按等级重算 == dws_member_level', 0.1],
  ads_store: ['915 ADS 门店绩效', '门店汇总×月度目标 == ads_store_performance', 0],
  ads_hour: ['916 ADS 时段热力', '按小时重算 == ads_hour_heat', 0.1],
  ads_member: ['917 ADS 会员价值', '会员去重分层 == ads_member_value', 0],
  ads_feed: ['918 ADS 实时流水', '最近 40 笔 == ads_realtime_feed', 0],
}

const rows = fs.readFileSync('devlog/bd3-equiv.out', 'utf8').replace(/\r/g, '')
  .split('\n').filter(Boolean).slice(1)

let bad = 0
for (const line of rows) {
  const [tag, dagRows, tblRows, dagAmount, tblAmount] = line.split('\t')
  const [name, rule, tol] = EXPECT[tag] || [tag, '?', 0]
  const rowDelta = Number(dagRows) - Number(tblRows)
  const amountDelta = Math.round((Number(dagAmount) - Number(tblAmount)) * 100) / 100
  const ok = rowDelta === 0 && Math.abs(amountDelta) <= tol
  if (!ok) bad += 1
  console.log(`${ok ? '通过  ' : '不通过'} ${name.padEnd(18)} 行数 ${dagRows}/${tblRows}`
    + `  金额差 ${String(amountDelta).padStart(7)}（容差 ${tol}）  ${rule}`)
}
console.log(bad === 0 ? '等价性自检：8/8 通过（DAG 语义 = 装载 SQL 语义）' : `等价性自检：${bad} 条不等价`)
process.exit(bad === 0 ? 0 : 1)
