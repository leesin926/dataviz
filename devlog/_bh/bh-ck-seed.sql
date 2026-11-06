INSERT INTO dataviz.dwd_order_channel_daily
SELECT
    addDays(toDate('2026-08-01'), number % 92)                          AS stat_date,
    arrayElement(['线上商城', '小程序', '门店', '分销'], (number % 4) + 1)   AS channel,
    arrayElement(['华东', '华北', '华南', '西南'], (intDiv(number, 4) % 4) + 1) AS region,
    round(500 + (rand() % 9500) / 10, 2)                                AS amount,
    1 + (rand() % 20)                                                   AS order_cnt
FROM numbers(4000)
