-- ============================================================================
-- BD5 演示大屏补丁：db_screen.screen 两块（901 静态回读 / 911 数据集直连）
-- 由 devlog/_ay/bd5-gen-screen.mjs 生成，数据来自 devlog/_ay/bd5-data.out（对 db_analysis 演示层的实查回读）
-- 改布局请改生成器再重跑，不要手改本文件：组件契约由 devlog/_ay/bd5-contract.cjs 对着渲染器核。
--
-- 为什么是两块而不是一块：
--   901 每个组件都带 request.staticData ⇒ 免登链接 /s/{token} 就能渲染，这是"看得见"的那半；
--   911 只带 chartConfig.datasetId ⇒ ScreenChart 调 executeQuery（POST /api/analysis/query/execute，
--   需登录）⇒ 它是"数据模型→分析→大屏"打通的那半，只能在登录态验收。
--   合在一起的屏没法分辨"配好了但查不出来"和"根本没配"。
--
-- 画布锁白（shared-types CANVAS_BG_COLOR）⇒ 前景一律深色墨值；浅色描边会被 normalizeScreenInk
--   改写成品牌蓝，所以这里不出现浅色 color 属性（亮度差由契约自检逐个算过）。
--
-- screen_component（旧镜像表，id 901-908）本补丁不动：渲染链只解析 screen.components_json
--   （ScreenServiceImpl.toVO），该表除 /api/screen/component 那组 CRUD 外没有任何读取方，
--   也没有任何前端调用。给它补一份镜像等于凭猜测定义一套没人消费的列语义 —— 宁可留旧行并写明。
-- ============================================================================

SET NAMES utf8mb4;
USE db_screen;

-- 901 原地重建：id 与分享令牌 smoke0921aaaa 保持不动，之前发出去的免登链接继续可用。
-- 整段包在事务里：先 DELETE 再 INSERT，INSERT 一旦失败（本轮真遇过 create_by 塞了字符串，
--   列是 bigint → 1366）不留"屏已删、新屏没进"的半状态 —— 免登链接会变 404。
START TRANSACTION;
DELETE FROM screen WHERE id IN (901, 911);

-- 901 销售实时监控大屏：27 个组件
INSERT INTO screen (id, tenant_id, name, description, config_json, components_json, variants_json,
                            cover_url, status, adapt_mode, view_count, share_token, create_by, deleted)
  VALUES (901, 1, '销售实时监控大屏', 'BD5 演示屏①：21 个组件全部带 request.staticData，数值来自 devlog/_ay/bd5-data.sql 对 db_analysis 演示层的实查回读，免登链接即可整屏渲染（不依赖登录态与查询接口）。',
    '{"width":1920,"height":1080,"layers":[{"id":"ly_1","name":"头部","visible":true,"locked":false,"componentIds":["c1","c2","c3","c4","c5"]},{"id":"ly_2","name":"指标卡","visible":true,"locked":false,"componentIds":["c6","c7","c8","c9","c10"]},{"id":"ly_3","name":"趋势对比","visible":true,"locked":false,"componentIds":["c11","c12"]},{"id":"ly_4","name":"结构排行","visible":true,"locked":false,"componentIds":["c13","c14","c15"]},{"id":"ly_5","name":"明细实时","visible":true,"locked":false,"componentIds":["c16","c17","c18","c19"]},{"id":"ly_6","name":"页脚","visible":true,"locked":false,"componentIds":["c20","c21","c22","c23","c24","c25","c26","c27"]}],"adaptationMode":"scale","backgroundColor":"#ffffff","backgroundSize":"cover","backgroundRepeat":"no-repeat","gridSnap":true,"gridSize":10,"globalRefreshInterval":0}',
    '[{"id":"c1","type":"decoration","name":"顶部流动线","x":30,"y":104,"w":1860,"h":8,"zIndex":1,"visible":true,"locked":false,"props":{"variant":"flow","color":"#2f7cff","colorSecond":"#0f8a5f"}},{"id":"c2","type":"text","name":"主标题","x":30,"y":22,"w":640,"h":46,"zIndex":2,"visible":true,"locked":false,"props":{"text":"销售实时监控中心","fontSize":"32px","fontWeight":"700","color":"#16202f","textAlign":"left","lineHeight":"1.2"}},{"id":"c3","type":"text","name":"副标题","x":30,"y":66,"w":640,"h":28,"zIndex":3,"visible":true,"locked":false,"props":{"text":"演示数据 · 统计窗口 2026-07-21 ~ 2026-10-18（90 天）· 口径 DWD 日汇总","fontSize":"13px","color":"#5b6a80","textAlign":"left","lineHeight":"1.2"}},{"id":"c4","type":"marquee","name":"运行状态滚动","x":700,"y":30,"w":630,"h":40,"zIndex":4,"visible":true,"locked":false,"props":{"texts":["门店 21 家 / 订单 2000 笔","覆盖 6 大区 · 4 渠道 · 5 类目","目标达成率 105.6%","毛利率 18.7%","数据窗口 90 天"],"speed":32,"color":"#2f7cff","textColor":"#263243","fontSize":"15px"}},{"id":"c5","type":"clock","name":"时钟","x":1490,"y":20,"w":400,"h":72,"zIndex":5,"visible":true,"locked":false,"props":{"fontSize":"26px","color":"#263243","dateFormat":"YYYY-MM-DD"}},{"id":"c6","type":"statCard","name":"累计销售额","x":30,"y":118,"w":360,"h":96,"zIndex":6,"visible":true,"locked":false,"props":{"title":"累计销售额","value":5973361,"unit":"元","color":"#16202f","accent":"#2f7cff","bgColor":"rgba(22,32,47,0.06)","fontSize":"28px","trend":7.4}},{"id":"c7","type":"statCard","name":"订单总量","x":405,"y":118,"w":360,"h":96,"zIndex":7,"visible":true,"locked":false,"props":{"title":"订单总量","value":2000,"unit":"单","color":"#16202f","accent":"#0f8a5f","bgColor":"rgba(22,32,47,0.06)","fontSize":"28px","trend":-6.2}},{"id":"c8","type":"statCard","name":"累计毛利","x":780,"y":118,"w":360,"h":96,"zIndex":8,"visible":true,"locked":false,"props":{"title":"累计毛利","value":1118504,"unit":"元","color":"#16202f","accent":"#7a5af8","bgColor":"rgba(22,32,47,0.06)","fontSize":"28px","trend":-0.8}},{"id":"c9","type":"statCard","name":"客单价","x":1155,"y":118,"w":360,"h":96,"zIndex":9,"visible":true,"locked":false,"props":{"title":"客单价","value":2987,"unit":"元","color":"#16202f","accent":"#e8590c","bgColor":"rgba(22,32,47,0.06)","fontSize":"28px"}},{"id":"c10","type":"statCard","name":"目标达成率","x":1530,"y":118,"w":360,"h":96,"zIndex":10,"visible":true,"locked":false,"props":{"title":"目标达成率","value":105.6,"unit":"%","color":"#16202f","accent":"#b7791f","bgColor":"rgba(22,32,47,0.06)","fontSize":"28px","trend":5.6}},{"id":"c11","type":"areaChart","name":"近 14 日销售额与毛利","x":30,"y":228,"w":920,"h":296,"zIndex":11,"visible":true,"locked":false,"props":{"precision":0},"chartConfig":{"chartType":"line","datasetId":null,"dimensions":[{"field":"stat_date","type":"time"}],"measures":[{"field":"amount","aggregation":"sum"},{"field":"profit","aggregation":"sum"}],"options":{"title":{"text":"近 14 日销售额与毛利","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"area":true,"smooth":true},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"stat_date","type":"date"},{"field":"amount","type":"number"},{"field":"profit","type":"number"}],"rows":[{"stat_date":"10-05","amount":56453,"profit":8985},{"stat_date":"10-06","amount":66548,"profit":11025},{"stat_date":"10-07","amount":78754,"profit":14274},{"stat_date":"10-08","amount":41729,"profit":8553},{"stat_date":"10-09","amount":55482,"profit":12941},{"stat_date":"10-10","amount":69711,"profit":15262},{"stat_date":"10-11","amount":103577,"profit":22972},{"stat_date":"10-12","amount":61321,"profit":10994},{"stat_date":"10-13","amount":50614,"profit":8560},{"stat_date":"10-14","amount":65562,"profit":13614},{"stat_date":"10-15","amount":63904,"profit":13310},{"stat_date":"10-16","amount":68158,"profit":12220},{"stat_date":"10-17","amount":114658,"profit":19597},{"stat_date":"10-18","amount":83006,"profit":14977}]}}},{"id":"c12","type":"barChart","name":"大区销售额与毛利","x":970,"y":228,"w":920,"h":296,"zIndex":12,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":null,"dimensions":[{"field":"region"}],"measures":[{"field":"amount","aggregation":"sum"},{"field":"profit","aggregation":"sum"}],"options":{"title":{"text":"大区销售额与毛利","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}}},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"region","type":"string"},{"field":"amount","type":"number"},{"field":"profit","type":"number"}],"rows":[{"region":"华东","amount":1578142,"profit":298430},{"region":"华南","amount":1439337,"profit":256027},{"region":"华北","amount":1080887,"profit":206721},{"region":"西南","amount":870331,"profit":163899},{"region":"东北","amount":624400,"profit":116813},{"region":"西北","amount":380263,"profit":76613}]}}},{"id":"c13","type":"donutChart","name":"类目销售额结构","x":30,"y":538,"w":520,"h":288,"zIndex":13,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"pie","datasetId":null,"dimensions":[{"field":"category"}],"measures":[{"field":"amount","aggregation":"sum"}],"options":{"title":{"text":"类目销售额结构","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"donut":true},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"category","type":"string"},{"field":"amount","type":"number"}],"rows":[{"category":"数码","amount":4125791},{"category":"家居","amount":696620},{"category":"服饰","amount":505103},{"category":"生鲜","amount":345822},{"category":"美妆","amount":300024}]}}},{"id":"c14","type":"stackBarChart","name":"渠道 × 类目销售额","x":565,"y":538,"w":640,"h":288,"zIndex":14,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":null,"dimensions":[{"field":"channel"},{"field":"category"}],"measures":[{"field":"amount","aggregation":"sum"}],"options":{"title":{"text":"渠道 × 类目销售额","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"stack":true},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"channel","type":"string"},{"field":"category","type":"string"},{"field":"amount","type":"number"}],"rows":[{"channel":"APP","category":"家居","amount":109657},{"channel":"APP","category":"数码","amount":798418},{"channel":"APP","category":"服饰","amount":73629},{"channel":"APP","category":"生鲜","amount":62479},{"channel":"APP","category":"美妆","amount":53152},{"channel":"小程序","category":"家居","amount":194556},{"channel":"小程序","category":"数码","amount":1206626},{"channel":"小程序","category":"服饰","amount":162366},{"channel":"小程序","category":"生鲜","amount":108984},{"channel":"小程序","category":"美妆","amount":89883},{"channel":"第三方平台","category":"家居","amount":96129},{"channel":"第三方平台","category":"数码","amount":391849},{"channel":"第三方平台","category":"服饰","amount":59368},{"channel":"第三方平台","category":"生鲜","amount":41796},{"channel":"第三方平台","category":"美妆","amount":33447},{"channel":"门店","category":"家居","amount":296278},{"channel":"门店","category":"数码","amount":1728898},{"channel":"门店","category":"服饰","amount":209740},{"channel":"门店","category":"生鲜","amount":132564},{"channel":"门店","category":"美妆","amount":123542}]}}},{"id":"c15","type":"ranking","name":"门店销售额 TOP10","x":1220,"y":538,"w":670,"h":288,"zIndex":15,"visible":true,"locked":false,"props":{"items":[{"name":"优选广州滨江店","value":558674},{"name":"优选深圳中心店","value":487921},{"name":"优选厦门老城店","value":392742},{"name":"优选苏州滨江店","value":385553},{"name":"优选北京新城1店","value":377076},{"name":"优选杭州中心店","value":334800},{"name":"优选成都滨江店","value":308611},{"name":"优选上海滨江1店","value":307236},{"name":"优选南京中心店","value":291164},{"name":"优选重庆新城店","value":281718}],"color":"#263243","barColor":"#2f7cff","showValue":true,"fontSize":"13px"}},{"id":"c16","type":"barChart","name":"时段订单分布","x":30,"y":840,"w":470,"h":204,"zIndex":16,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":null,"dimensions":[{"field":"stat_hour"}],"measures":[{"field":"orders","aggregation":"sum"}],"options":{"title":{"text":"时段订单分布","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}}},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"stat_hour","type":"string"},{"field":"orders","type":"number"}],"rows":[{"stat_hour":"10","orders":91},{"stat_hour":"11","orders":156},{"stat_hour":"12","orders":255},{"stat_hour":"13","orders":166},{"stat_hour":"14","orders":116},{"stat_hour":"15","orders":104},{"stat_hour":"16","orders":144},{"stat_hour":"17","orders":164},{"stat_hour":"18","orders":188},{"stat_hour":"19","orders":259},{"stat_hour":"20","orders":214},{"stat_hour":"21","orders":143}]}}},{"id":"c17","type":"gaugeChart","name":"门店平均达成率","x":515,"y":840,"w":300,"h":204,"zIndex":17,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"gauge","datasetId":null,"title":"达成率 %","dimensions":[{"field":"name"}],"measures":[{"field":"value","aggregation":"sum"}],"options":{"max":150,"min":0},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"name","type":"string"},{"field":"value","type":"number"}],"rows":[{"name":"达成率","value":107.6}]}}},{"id":"c18","type":"scrollBoard","name":"实时成交流水","x":830,"y":840,"w":470,"h":204,"zIndex":18,"visible":true,"locked":false,"props":{"columns":["时间","门店","类目","金额"],"rows":[["20:41:43","优选北京新城1店","数码","2611.28"],["20:32:45","优选石家庄经开店","美妆","948.48"],["20:31:42","优选上海滨江1店","美妆","651.29"],["20:30:36","优选广州滨江店","家居","1037.08"],["20:14:03","优选重庆新城店","生鲜","1449.53"],["20:13:42","优选厦门老城店","美妆","576.47"],["20:03:44","优选成都滨江店","生鲜","959.74"],["20:01:50","优选大连高新店","生鲜","908.41"],["19:24:57","优选沈阳老城店","美妆","1594.93"],["19:15:25","优选大连高新店","生鲜","332.54"]],"interval":2,"fontSize":"13px","color":"#263243"}},{"id":"c19","type":"table","name":"会员等级价值","x":1315,"y":840,"w":575,"h":204,"zIndex":19,"visible":true,"locked":false,"props":{"columns":["等级","消费额","人数","复购率%"],"rows":[["普通","2336638","313","68.37"],["银卡","828706","114","71.05"],["钻石","430060","35","65.71"],["金卡","410232","65","70.77"]],"fontSize":"13px","color":"#263243"}},{"id":"c20","type":"text","name":"页脚血缘","x":30,"y":1052,"w":1560,"h":24,"zIndex":20,"visible":true,"locked":false,"props":{"text":"血缘：db_model.demo_orders → ods_order_detail → dwd_sales_daily → ads_*（BD2 造数 / BD3 ETL 装载）→ 数据集 912·915·916·917·918 → 本大屏静态回读","fontSize":"13px","color":"#5b6a80","textAlign":"left","lineHeight":"1.2"}},{"id":"c21","type":"decoration","name":"右下角律动柱","x":1620,"y":1044,"w":270,"h":30,"zIndex":21,"visible":true,"locked":false,"props":{"variant":"bars","color":"#2f7cff","colorSecond":"#0f8a5f"}},{"id":"c22","type":"progress","name":"华东目标达成","x":30,"y":1000,"w":300,"h":40,"zIndex":22,"visible":true,"locked":false,"props":{"label":"华东 达成","percent":100.3,"color":"#0f8a5f","trackColor":"rgba(22,32,47,0.06)","textColor":"#263243","height":10,"showValue":true}},{"id":"c23","type":"progress","name":"华南目标达成","x":342,"y":1000,"w":300,"h":40,"zIndex":23,"visible":true,"locked":false,"props":{"label":"华南 达成","percent":108.3,"color":"#0f8a5f","trackColor":"rgba(22,32,47,0.06)","textColor":"#263243","height":10,"showValue":true}},{"id":"c24","type":"progress","name":"华北目标达成","x":654,"y":1000,"w":300,"h":40,"zIndex":24,"visible":true,"locked":false,"props":{"label":"华北 达成","percent":116.4,"color":"#0f8a5f","trackColor":"rgba(22,32,47,0.06)","textColor":"#263243","height":10,"showValue":true}},{"id":"c25","type":"progress","name":"西南目标达成","x":966,"y":1000,"w":300,"h":40,"zIndex":25,"visible":true,"locked":false,"props":{"label":"西南 达成","percent":107.8,"color":"#0f8a5f","trackColor":"rgba(22,32,47,0.06)","textColor":"#263243","height":10,"showValue":true}},{"id":"c26","type":"progress","name":"东北目标达成","x":1278,"y":1000,"w":300,"h":40,"zIndex":26,"visible":true,"locked":false,"props":{"label":"东北 达成","percent":91.8,"color":"#b7791f","trackColor":"rgba(22,32,47,0.06)","textColor":"#263243","height":10,"showValue":true}},{"id":"c27","type":"progress","name":"西北目标达成","x":1590,"y":1000,"w":300,"h":40,"zIndex":27,"visible":true,"locked":false,"props":{"label":"西北 达成","percent":112.3,"color":"#0f8a5f","trackColor":"rgba(22,32,47,0.06)","textColor":"#263243","height":10,"showValue":true}}]',
    '{"mobile":{"width":390,"height":1064,"config":{"width":390,"height":1064,"layers":[{"id":"ly_m1","name":"移动一组","visible":true,"locked":false,"componentIds":["c2","c5","c11","c12","c13","c18"]}],"adaptationMode":"scale","backgroundColor":"#ffffff","backgroundSize":"cover","backgroundRepeat":"no-repeat","gridSnap":true,"gridSize":10,"globalRefreshInterval":0},"components":[{"id":"c2","type":"text","name":"主标题","x":12,"y":16,"w":366,"h":34,"zIndex":1,"visible":true,"locked":false,"props":{"text":"销售监控（移动版）","fontSize":"20px","fontWeight":"700","color":"#16202f","textAlign":"left","lineHeight":"1.2"}},{"id":"c5","type":"clock","name":"时钟","x":12,"y":54,"w":366,"h":40,"zIndex":2,"visible":true,"locked":false,"props":{"fontSize":"16px","color":"#263243","dateFormat":"YYYY-MM-DD"}},{"id":"c11","type":"areaChart","name":"近 14 日销售额与毛利","x":12,"y":104,"w":366,"h":240,"zIndex":3,"visible":true,"locked":false,"props":{"precision":0},"chartConfig":{"chartType":"line","datasetId":null,"dimensions":[{"field":"stat_date","type":"time"}],"measures":[{"field":"amount","aggregation":"sum"},{"field":"profit","aggregation":"sum"}],"options":{"title":{"text":"近 14 日销售额与毛利","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"area":true,"smooth":true},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"stat_date","type":"date"},{"field":"amount","type":"number"},{"field":"profit","type":"number"}],"rows":[{"stat_date":"10-05","amount":56453,"profit":8985},{"stat_date":"10-06","amount":66548,"profit":11025},{"stat_date":"10-07","amount":78754,"profit":14274},{"stat_date":"10-08","amount":41729,"profit":8553},{"stat_date":"10-09","amount":55482,"profit":12941},{"stat_date":"10-10","amount":69711,"profit":15262},{"stat_date":"10-11","amount":103577,"profit":22972},{"stat_date":"10-12","amount":61321,"profit":10994},{"stat_date":"10-13","amount":50614,"profit":8560},{"stat_date":"10-14","amount":65562,"profit":13614},{"stat_date":"10-15","amount":63904,"profit":13310},{"stat_date":"10-16","amount":68158,"profit":12220},{"stat_date":"10-17","amount":114658,"profit":19597},{"stat_date":"10-18","amount":83006,"profit":14977}]}}},{"id":"c12","type":"barChart","name":"大区销售额与毛利","x":12,"y":354,"w":366,"h":240,"zIndex":4,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":null,"dimensions":[{"field":"region"}],"measures":[{"field":"amount","aggregation":"sum"},{"field":"profit","aggregation":"sum"}],"options":{"title":{"text":"大区销售额与毛利","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}}},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"region","type":"string"},{"field":"amount","type":"number"},{"field":"profit","type":"number"}],"rows":[{"region":"华东","amount":1578142,"profit":298430},{"region":"华南","amount":1439337,"profit":256027},{"region":"华北","amount":1080887,"profit":206721},{"region":"西南","amount":870331,"profit":163899},{"region":"东北","amount":624400,"profit":116813},{"region":"西北","amount":380263,"profit":76613}]}}},{"id":"c13","type":"donutChart","name":"类目销售额结构","x":12,"y":604,"w":366,"h":220,"zIndex":5,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"pie","datasetId":null,"dimensions":[{"field":"category"}],"measures":[{"field":"amount","aggregation":"sum"}],"options":{"title":{"text":"类目销售额结构","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"donut":true},"theme":"brand","animation":true},"request":{"sourceType":"static","staticData":{"columns":[{"field":"category","type":"string"},{"field":"amount","type":"number"}],"rows":[{"category":"数码","amount":4125791},{"category":"家居","amount":696620},{"category":"服饰","amount":505103},{"category":"生鲜","amount":345822},{"category":"美妆","amount":300024}]}}},{"id":"c18","type":"scrollBoard","name":"实时成交流水","x":12,"y":834,"w":366,"h":220,"zIndex":6,"visible":true,"locked":false,"props":{"columns":["时间","门店","类目","金额"],"rows":[["20:41:43","优选北京新城1店","数码","2611.28"],["20:32:45","优选石家庄经开店","美妆","948.48"],["20:31:42","优选上海滨江1店","美妆","651.29"],["20:30:36","优选广州滨江店","家居","1037.08"],["20:14:03","优选重庆新城店","生鲜","1449.53"],["20:13:42","优选厦门老城店","美妆","576.47"],["20:03:44","优选成都滨江店","生鲜","959.74"],["20:01:50","优选大连高新店","生鲜","908.41"],["19:24:57","优选沈阳老城店","美妆","1594.93"],["19:15:25","优选大连高新店","生鲜","332.54"]],"interval":2,"fontSize":"13px","color":"#263243"}}],"layers":[{"id":"ly_m1","name":"移动一组","visible":true,"locked":false,"componentIds":["c2","c5","c11","c12","c13","c18"]}]}}',
    NULL, 1, 'scale', 0, 'smoke0921aaaa', 1, 0);

-- 911 经营分析看板（数据集直连）：11 个组件
INSERT INTO screen (id, tenant_id, name, description, config_json, components_json, variants_json,
                            cover_url, status, adapt_mode, view_count, share_token, create_by, deleted)
  VALUES (911, 1, '经营分析看板（数据集直连）', 'BD5 演示屏②：9 个图表组件绑 BD4 的数据集，不写静态数据；登录态下每个组件发一次 /analysis/query/execute。datasetId 避开行级规则(913)与列级规则(915 遮 profit/margin_rate)会影响的字段，915 只取未受限的 achievement_rate。',
    '{"width":1920,"height":1080,"layers":[{"id":"ly_1","name":"标题与说明","visible":true,"locked":false,"componentIds":["d1","d2"]},{"id":"ly_2","name":"趋势与结构","visible":true,"locked":false,"componentIds":["d3","d4","d5"]},{"id":"ly_3","name":"渠道与品类","visible":true,"locked":false,"componentIds":["d6","d7"]},{"id":"ly_4","name":"门店与达成","visible":true,"locked":false,"componentIds":["d8","d9","d10","d11"]}],"adaptationMode":"scale","backgroundColor":"#ffffff","backgroundSize":"cover","backgroundRepeat":"no-repeat","gridSnap":true,"gridSize":10,"globalRefreshInterval":0}',
    '[{"id":"d1","type":"text","name":"主标题","x":30,"y":20,"w":900,"h":42,"zIndex":1,"visible":true,"locked":false,"props":{"text":"经营分析看板（数据集直连）","fontSize":"30px","fontWeight":"700","color":"#16202f","textAlign":"left","lineHeight":"1.2"}},{"id":"d2","type":"text","name":"链路说明","x":30,"y":64,"w":1860,"h":26,"zIndex":2,"visible":true,"locked":false,"props":{"text":"本屏 8 个图表组件只绑 model_dataset（912/915/916/917/919/920/921），渲染时经 ScreenChart → executeQuery → POST /api/analysis/query/execute 现取现画；该查询接口需登录，免登链接打开时组件会显式报\\"取数失败\\"（不再是静默空屏），登录态下同一链接出真数据 —— 这一屏的验收必须在登录态下做。","fontSize":"13px","color":"#5b6a80","textAlign":"left","lineHeight":"1.2"}},{"id":"d3","type":"areaChart","name":"每日销售趋势（数据集 921）","x":30,"y":104,"w":600,"h":292,"zIndex":3,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"line","datasetId":921,"dimensions":[{"field":"stat_date","type":"time"}],"measures":[{"field":"amount","aggregation":"sum"}],"sorts":[{"field":"stat_date","order":"asc"}],"options":{"title":{"text":"每日销售趋势（数据集 921）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"area":true},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d4","type":"barChart","name":"大区销售与毛利（数据集 912）","x":650,"y":104,"w":600,"h":292,"zIndex":4,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":912,"dimensions":[{"field":"region"}],"measures":[{"field":"amount","aggregation":"sum"},{"field":"profit","aggregation":"sum"}],"sorts":[{"field":"amount","order":"desc"}],"options":{"title":{"text":"大区销售与毛利（数据集 912）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}}},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d5","type":"donutChart","name":"会员等级价值（数据集 917）","x":1270,"y":104,"w":620,"h":292,"zIndex":5,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"pie","datasetId":917,"dimensions":[{"field":"member_level"}],"measures":[{"field":"amount","aggregation":"sum"}],"sorts":[{"field":"amount","order":"desc"}],"options":{"title":{"text":"会员等级价值（数据集 917）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"donut":true},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d6","type":"barChart","name":"时段成交分布（数据集 916）","x":30,"y":412,"w":600,"h":292,"zIndex":6,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":916,"dimensions":[{"field":"stat_hour"}],"measures":[{"field":"amount","aggregation":"sum"},{"field":"orders","aggregation":"sum"}],"sorts":[{"field":"stat_hour","order":"asc"}],"options":{"title":{"text":"时段成交分布（数据集 916）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}}},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d7","type":"stackBarChart","name":"渠道×品类毛利矩阵（数据集 920）","x":650,"y":412,"w":600,"h":292,"zIndex":7,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":920,"dimensions":[{"field":"channel"},{"field":"category"}],"measures":[{"field":"amount","aggregation":"sum"}],"sorts":[],"options":{"title":{"text":"渠道×品类毛利矩阵（数据集 920）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"stack":true},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d8","type":"pieChart","name":"渠道销售额占比（数据集 912）","x":1270,"y":412,"w":620,"h":292,"zIndex":8,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"pie","datasetId":912,"dimensions":[{"field":"channel"}],"measures":[{"field":"amount","aggregation":"sum"}],"sorts":[{"field":"amount","order":"desc"}],"options":{"title":{"text":"渠道销售额占比（数据集 912）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}}},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d9","type":"lineChart","name":"每日毛利趋势（数据集 921）","x":30,"y":720,"w":600,"h":292,"zIndex":9,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"line","datasetId":921,"dimensions":[{"field":"stat_date","type":"time"}],"measures":[{"field":"profit","aggregation":"sum"}],"sorts":[{"field":"stat_date","order":"asc"}],"options":{"title":{"text":"每日毛利趋势（数据集 921）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}}},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d10","type":"hBarChart","name":"门店销售额排行（数据集 919 跨库SQL）","x":650,"y":720,"w":600,"h":292,"zIndex":10,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"bar","datasetId":919,"dimensions":[{"field":"store_name"}],"measures":[{"field":"amount","aggregation":"sum"}],"sorts":[{"field":"amount","order":"desc"}],"options":{"title":{"text":"门店销售额排行（数据集 919 跨库SQL）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"horizontal":true},"theme":"brand","animation":true},"request":{"sourceType":"dataset"}},{"id":"d11","type":"gaugeChart","name":"门店平均达成率（数据集 915 未受限列）","x":1270,"y":720,"w":620,"h":292,"zIndex":11,"visible":true,"locked":false,"props":{},"chartConfig":{"chartType":"gauge","datasetId":915,"dimensions":[],"measures":[{"field":"achievement_rate","aggregation":"avg"}],"sorts":[],"options":{"title":{"text":"门店平均达成率（数据集 915 未受限列）","left":14,"top":8,"textStyle":{"color":"#16202f","fontSize":15,"fontWeight":600}},"max":160,"min":0},"theme":"brand","animation":true,"title":"平均达成率 %"},"request":{"sourceType":"dataset"}}]',
    NULL,
    NULL, 1, 'scale', 0, '2436790b215920ed79f812db07c38ddd', 1, 0);

COMMIT;

-- ============ 自检 ============
-- 跑法：mysql --force（默认遇错即退，退掉的那条永远不产出 @@BAD，看起来就"全过"了）
-- 判据：stderr 里 ERROR 行数为 0，且 stdout 里以 @@BAD 开头的行数为 0，且最后一行是 @@CHECKS-END。
--   （只看"行首"：mysql -B 会把 SELECT 表达式本身当列头回显，头一行里就带着 @@BAD 字面量）
-- 三条缺一不可 —— "@@BAD 缺席"只有和"全部执行完毕"同时成立才是通过。
-- 1. 数量与发布态：两块屏都必须 status=1 且有令牌，否则免登链接回"尚未发布"
SELECT CONCAT('@@COUNT screen_demo = ', COUNT(*)) FROM screen WHERE id IN (901, 911) AND deleted = 0 AND status = 1 AND share_token IS NOT NULL;

-- 2. 组件数：JSON_LENGTH 与声明一致（少了就是 INSERT 时被截断，典型症状是页面缺半屏）
SELECT CONCAT('@@LEN ', id, ' ~|~ ', JSON_LENGTH(components_json)) FROM screen WHERE id IN (901, 911);

-- 3. 图层引用完整性：layers 里每个 componentId 都必须真的在 components_json 里
SELECT CONCAT('@@BAD layer-ref ', s.id, ' ~|~ ', l.id, ' ~|~ ', l.cid)
FROM screen s
JOIN JSON_TABLE(s.config_json, '$.layers[*]' COLUMNS (
  id VARCHAR(64) PATH '$.id',
  NESTED PATH '$.componentIds[*]' COLUMNS (cid VARCHAR(64) PATH '$')
)) l
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(s.components_json, '$[*]' COLUMNS (
      cid VARCHAR(64) PATH '$.id')) j WHERE j.cid = l.cid);
-- 3b. 反向：有组件没进任何图层（编辑器图层面板会漏项）
SELECT CONCAT('@@BAD orphan ', s.id, ' ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id')) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(s.config_json, '$.layers[*]' COLUMNS (
      NESTED PATH '$.componentIds[*]' COLUMNS (cid VARCHAR(64) PATH '$'))) t WHERE t.cid = c.cid);

-- 4. 组件类型必须在渲染注册表里（拼错 type 的症状是 ScreenUnknown 灰块，不是报错）
SELECT CONCAT('@@BAD type ', s.id, ' ~|~ ', c.type)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (type VARCHAR(32) PATH '$.type')) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND c.type NOT IN ('chart','barChart','stackBarChart','hBarChart','lineChart','areaChart','pieChart','donutChart',
                    'roseChart','radarChart','gaugeChart','text','marquee','countdown','clock','image','border',
                    'decoration','map','video','iframe','table','scrollBoard','numberFlop','statCard','progress',
                    'ranking','waterPolo','flyline');

-- 5. 图表组件必须真的带 chartConfig（这是原 901 种子的缺陷：只有 chartPreset 字符串，
--    ScreenChart 的 v-if 拿不到 chartConfig ⇒ 三张图画成"未配置图表"占位框）
SELECT CONCAT('@@BAD no-chartConfig ', s.id, ' ~|~ ', c.cid, ' ~|~ ', c.type)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', type VARCHAR(32) PATH '$.type',
  cfg JSON PATH '$.chartConfig'
)) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND c.type IN ('chart','barChart','stackBarChart','hBarChart','lineChart','areaChart','pieChart','donutChart','roseChart','radarChart','gaugeChart','treemapChart','sunburstChart','heatmapChart')
  AND c.cfg IS NULL;

-- 6. 901 的每个静态组件必须有行（request.staticData.rows 非空），否则免登面是一片空框
SELECT CONCAT('@@BAD empty-static ', s.id, ' ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id',
  src VARCHAR(16) PATH '$.request.sourceType',
  rows_j JSON PATH '$.request.staticData.rows'
)) c
WHERE s.id = 901 AND s.deleted = 0
  AND (c.src = 'static' OR c.rows_j IS NOT NULL)
  AND (c.rows_j IS NULL OR JSON_LENGTH(c.rows_j) = 0);
-- 6b. 911 反过来：图表组件不许带 staticData（带了就证明不了真链路）
--     只核图表类型：文本/装饰这类组件本来就既没有 datasetId 也没有 rows，
--     早先不带类型条件时把 d1/d2 两个 text 也报成了 BAD。
SELECT CONCAT('@@BAD static-in-live-screen ', s.id, ' ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', type VARCHAR(32) PATH '$.type',
  src VARCHAR(16) PATH '$.request.sourceType',
  dsid BIGINT PATH '$.chartConfig.datasetId'
)) c
WHERE s.id = 911 AND s.deleted = 0
  AND c.type IN ('chart','barChart','stackBarChart','hBarChart','lineChart','areaChart','pieChart','donutChart','roseChart','radarChart','gaugeChart','treemapChart','sunburstChart','heatmapChart')
  AND (c.src = 'static' OR c.dsid IS NULL);

-- 7. 数据集引用完整性：911 绑的 datasetId 必须存在于 db_model.model_dataset 且未删除
SELECT CONCAT('@@BAD dataset ', s.id, ' ~|~ ', c.cid, ' ~|~ ', c.dsid)
FROM db_screen.screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', dsid BIGINT PATH '$.chartConfig.datasetId')) c
LEFT JOIN db_model.model_dataset d ON d.id = c.dsid AND d.deleted = 0
WHERE s.id = 911 AND s.deleted = 0 AND c.dsid IS NOT NULL AND d.id IS NULL;

-- 8. 画布越界：组件必须落在画布内（超出去的部分不会滚动，是"少了一块"而不是"报错"）
SELECT CONCAT('@@BAD geometry ', s.id, ' ~|~ ', c.cid, ' ~|~ ', c.x + c.w, '/', c.y + c.h)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', x INT PATH '$.x', y INT PATH '$.y', w INT PATH '$.w', h INT PATH '$.h')) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND (c.x < 0 OR c.y < 0 OR c.x + c.w > JSON_EXTRACT(s.config_json, '$.width')
                    OR c.y + c.h > JSON_EXTRACT(s.config_json, '$.height'));

-- 9. 令牌唯一：两个令牌指向两块屏时，后写的那块会盖掉前一块的免登入口
SELECT CONCAT('@@BAD token-dup ', share_token, ' ~|~ ', COUNT(*)) FROM screen WHERE deleted = 0 AND share_token IS NOT NULL GROUP BY share_token HAVING COUNT(*) > 1;

-- 10. 旧种子缺陷回归：901 不许再出现"只有 chartPreset 没有 chartConfig"的组件
SELECT CONCAT(IF(s.id IN (901,911), '@@BAD ', '@@NOTE '), s.id, ' ~|~ preset-only ~|~ ', c.cid, ' ~|~ ', c.preset)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', preset VARCHAR(64) PATH '$.chartPreset', cfg JSON PATH '$.chartConfig')) c
WHERE s.deleted = 0 AND c.preset IS NOT NULL AND c.cfg IS NULL;

-- 11. statCard 的数值必须落在 props.value（渲染器读 props，不读 staticData.rows；
--     旧种子把数塞在 staticData.rows[0].value 里，卡片显示成 0）
SELECT CONCAT(IF(s.id IN (901,911), '@@BAD ', '@@NOTE '), s.id, ' ~|~ statCard-no-props.value ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', type VARCHAR(32) PATH '$.type', val JSON PATH '$.props.value')) c
WHERE s.deleted = 0 AND c.type = 'statCard' AND c.val IS NULL;

-- 12. 三端变体：901 的 mobile 变体组件数与令牌（变体缺 components 的症状是移动端整屏空白）
SELECT CONCAT('@@VARIANT ', id, ' ~|~ mobile ~|~ ', JSON_LENGTH(JSON_EXTRACT(variants_json, '$.mobile.components')))
FROM screen WHERE id = 901;

-- 13. 字段核对交给 Node：图表点名的字段是否真的存在于数据集/静态行里，SQL 层核不动（渲染器语义在前端）
SELECT CONCAT('@@NOTE 组件契约核对请跑 node devlog/_ay/bd5-contract.cjs') AS n;
SELECT '@@CHECKS-END' AS end_marker;

-- ============================================================================
-- rollback（人工执行）：901 退回本补丁之前的种子（6 个组件、无变体）不在本文件职责内，
--   需要回退时按 devlog/bd5-dump.out 里留档的原 components_json 逐字 UPDATE 回去。
--   USE db_screen;
--   DELETE FROM screen WHERE id = 911;
--   -- 901 用留档 JSON 还原，别直接删（删了免登链接就 404，而链接已经发出去了）
-- ============================================================================
