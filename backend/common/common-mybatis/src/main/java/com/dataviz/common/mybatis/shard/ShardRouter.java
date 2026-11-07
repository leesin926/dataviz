package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 路由计算：纯函数，不依赖 Spring、不碰 JDBC、不读 ThreadLocal。
 * <p>
 * 三件事各管一段，任何一段出错都<b>点名到表</b>：
 * <ul>
 *   <li>{@link #instanceIndex(String, Object)} —— 哪个实例（库）</li>
 *   <li>{@link #suffix(ShardProperties.Rule, Object)} —— 哪张物理表（时间后缀）</li>
 *   <li>{@link #targetsOf(String, Object, Object)} —— 一次区间读要扫哪些 (实例 × 后缀)</li>
 * </ul>
 * 之所以坚持纯函数：分片错路由的后果是<b>数据丢失</b>（行写进别人/别的时段，按租户捞不回来），
 * 而纯函数能用一千个断言钉死，不需要起容器、不需要 Docker、不需要真库。
 */
public class ShardRouter {

    /** 只接受本层生成的后缀：{@code _yyyyMM} 或 {@code _yyyyMMdd}，或空串。 */
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(_[0-9]{6}|_[0-9]{8})?$");

    private final List<String> instanceIds;
    private final ShardProperties properties;
    private final int maxShardsPerRead;

    /**
     * @param properties 已通过 {@link ShardProperties#validate()} 的配置（实例清单非空、buckets 合法）
     */
    public ShardRouter(ShardProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties 不能为空");
        }
        this.properties = properties;
        this.instanceIds = Collections.unmodifiableList(properties.instanceIds());
        this.maxShardsPerRead = properties.getMaxShardsPerRead();
        if (this.instanceIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "构造 ShardRouter 前必须先配置 shard.instances（实例清单为空）");
        }
    }

    // ------------------------------------------------------------------
    // 实例路由
    // ------------------------------------------------------------------

    /**
     * 该路由值落在哪个实例下标：{@code floorMod(stableHash(routeValue), buckets)}。
     * <p>
     * {@link ShardRouteBy#NONE} 且 {@code buckets > 1} 时用时间后缀轮转，此时 {@code routeValue}
     * 传时间值（与 {@link #targetsOf} 的枚举口径一致，两边算出来的下标必须相同）。
     *
     * @throws BizException 表无规则 / 路由值为空 / 类型不受支持
     */
    public int instanceIndex(String table, Object routeValue) {
        ShardProperties.Rule rule = requireRule(table);
        int buckets = rule.getBuckets();
        if (rule.getRouteBy() == ShardRouteBy.NONE) {
            if (buckets == 1) {
                return 0;
            }
            return bucketIndexOf(stableHash(rotationKey(rule, toLocalDate(routeValue, table))), buckets);
        }
        if (routeValue == null || (routeValue instanceof String && ((String) routeValue).trim().isEmpty())) {
            throw ShardErrors.blankRouteValue(table, "tenant_id");
        }
        return bucketIndexOf(stableHash(routeValue), buckets);
    }

    /** 实例下标 → 实例 id；下标越界说明 buckets 配得比实例清单还大（配置期已挡，这里是兜底）。 */
    public String instanceId(int index) {
        if (index < 0 || index >= instanceIds.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "实例下标越界: " + index + "，shard.instances 只有 " + instanceIds.size() + " 个");
        }
        return instanceIds.get(index);
    }

    public List<String> getInstanceIds() {
        return instanceIds;
    }

    /**
     * 桶位哈希：{@code MD5(路由值的字符串形式)} 的<b>前 4 字节按大端拼出的无符号整数</b>（0 ~ 2^32-1）。
     *
     * <h3>为什么是这条式子，而不是 {@code String.hashCode()}</h3>
     * <p>
     * 同一个桶位有<b>两处独立计算</b>：应用写入走这里，回填脚本与运维核对走 MySQL 的
     * {@code MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)),8),16,10)) AS UNSIGNED), buckets)}。
     * 两边算出不同结果时，症状是"应用把这租户写进 ds1，回填把它塞进 ds0"——行在盘上，但按租户 +
     * 时间任何一侧都读不到，属于<b>静默数据丢失</b>。所以契约只有一条，写在
     * {@code deploy/sql/shard/README.md}，改这里必须同时改 SQL 生成器，并由
     * {@code ShardBucketParityTest} 用真 MySQL 测出来的数钉住。
     *
     * <h3>为什么拒绝其他类型</h3>
     * <p>
     * POJO 的 {@code toString()} 默认带身份哈希码，{@code byte[]} 带地址——同一条记录在下次启动后散到
     * 别的实例，后果和上面那句一模一样。只接受 {@code String/Number/Character}。
     * 非 ASCII 字符串按 UTF-8 取字节，与 MySQL 会话 {@code utf8mb4} 下的 {@code MD5()} 同字节
     * （路由键实际都是数字 id，这条只影响"以后有人拿中文名当租户号"）。
     */
    public static long stableHash(Object routeValue) {
        if (!(routeValue instanceof String) && !(routeValue instanceof Number)
                && !(routeValue instanceof Character)) {
            throw ShardErrors.unstableRouteValue(routeValue);
        }
        return md5LeadingInt(routeValue.toString());
    }

    /** MD5 前 8 个十六进制字符 = 前 4 字节大端，等价于 MySQL 的 {@code CONV(LEFT(MD5(s),8),16,10)}。 */
    static long md5LeadingInt(String value) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必备算法，正常进不来；真进来了也不能退回 hashCode()——那正是本方法要防的错
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "本 JVM 没有 MD5 实现，无法计算与 MySQL 一致的分片桶位: " + e.getClass().getName());
        }
        return ((digest[0] & 0xFFL) << 24) | ((digest[1] & 0xFFL) << 16)
                | ((digest[2] & 0xFFL) << 8) | (digest[3] & 0xFFL);
    }

    /** 非负哈希对桶数取模；单独一个方法是为了让"两侧同式"只有一处可写错。 */
    private static int bucketIndexOf(long hash, int buckets) {
        return (int) Math.floorMod(hash, (long) buckets);
    }

    // ------------------------------------------------------------------
    // 时间后缀
    // ------------------------------------------------------------------

    /**
     * {@code MONTH → _202611}、{@code DAY → _20261106}、{@code NONE → ""}。
     * <p>
     * 接受 {@link LocalDateTime}、{@link LocalDate}、{@link java.util.Date}（含 {@code java.sql.Date}
     * 与 {@link java.sql.Timestamp}）与 ISO 风格字符串（{@code 2026-11-06}、{@code 2026-11-06T10:15:30}、
     * {@code 2026-11-06 10:15:30}、{@code 2026-11}，以及运维常用的紧凑形 {@code 202611} / {@code 20261106}）。
     * 其余类型按类型名拒绝——宁可拒绝，也不要把 {@code new Date()} 和字符串混着猜。
     */
    public static String suffix(ShardProperties.Rule rule, Object timeValue) {
        if (rule == null || rule.getMode() == ShardMode.NONE) {
            return "";
        }
        LocalDate date = toLocalDate(timeValue, rule.getTable());
        return rule.getMode() == ShardMode.MONTH ? "_" + compactMonth(date) : "_" + compactDay(date);
    }

    /**
     * 物理表名拼接的<b>唯一</b>落点——{@link ShardTableNameHandler} 与后续的 DDL 生成器共用。
     * <p>
     * 两侧都做白名单校验：逻辑名过 {@link ShardProperties#NAME_PATTERN}，后缀只认本层生成的形状。
     * 这里是拼进 SQL 的字符串，白名单必须在生成侧，而不是指望调用方传得干净。
     */
    public static String physical(String logicalTable, String suffix) {
        if (logicalTable == null || !ShardProperties.NAME_PATTERN.matcher(logicalTable).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "逻辑表名不合法: " + logicalTable + "，需匹配 ^[a-z][a-z0-9_]{0,62}$");
        }
        String tail = suffix == null ? "" : suffix;
        if (!SUFFIX_PATTERN.matcher(tail).matches()) {
            throw ShardErrors.illegalPhysicalName(logicalTable, tail);
        }
        String physical = logicalTable + tail;
        if (!ShardProperties.NAME_PATTERN.matcher(physical).matches()) {
            throw ShardErrors.illegalPhysicalName(logicalTable, tail);
        }
        return physical;
    }

    // ------------------------------------------------------------------
    // 绑定态解析（供拦截器与数据源使用；结果写回 Binding，保证一次请求内只算一次）
    // ------------------------------------------------------------------

    /** 解析（并缓存）绑定所属实例 id。非分片表返回默认实例。 */
    public String resolveInstanceId(ShardContext.Binding binding) {
        if (binding == null) {
            throw ShardErrors.unboundContext("<unknown>", null);
        }
        if (binding.getResolvedInstanceId() != null) {
            return binding.getResolvedInstanceId();
        }
        ShardProperties.Rule rule = properties.ruleOf(binding.getLogicalTable());
        String resolved;
        if (rule == null) {
            resolved = properties.defaultInstanceId();
        } else if (rule.getRouteBy() == ShardRouteBy.NONE) {
            resolved = instanceId(instanceIndex(rule.getTable(), binding.getTimeValue()));
        } else {
            resolved = instanceId(instanceIndex(rule.getTable(), binding.getTenantId()));
        }
        binding.setResolvedInstanceId(resolved);
        return resolved;
    }

    /** 解析（并缓存）物理表后缀。非分片表返回空串（即逻辑名原样）。 */
    public String resolveSuffix(ShardContext.Binding binding) {
        if (binding == null) {
            throw ShardErrors.unboundContext("<unknown>", null);
        }
        if (binding.getResolvedSuffix() != null) {
            return binding.getResolvedSuffix();
        }
        ShardProperties.Rule rule = properties.ruleOf(binding.getLogicalTable());
        String suffix = rule == null ? "" : suffix(rule, binding.getTimeValue());
        binding.setResolvedSuffix(suffix);
        return suffix;
    }

    /** 表是否有分片规则。 */
    public boolean isSharded(String table) {
        return properties.ruleOf(table) != null;
    }

    public ShardProperties.Rule ruleOf(String table) {
        return properties.ruleOf(table);
    }

    private ShardProperties.Rule requireRule(String table) {
        ShardProperties.Rule rule = properties.ruleOf(table);
        if (rule == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "表 " + table + " 没有分片规则（不在 shard.rules 里），不该走分片路由");
        }
        return rule;
    }

    // ------------------------------------------------------------------
    // 区间扇出
    // ------------------------------------------------------------------

    /**
     * 跨租户区间读要扫的分片：<b>全部 buckets 实例 × 时间区间内每个月/日</b>，新→旧排序、去重、
     * 受 {@code shard.max-shards-per-read} 约束。
     *
     * @throws BizException 超过上限时点名分片数与配置键，提示收窄范围或调大上限
     */
    public List<ShardTarget> targetsOf(String table, Object from, Object to) {
        return scan(table, null, from, to, maxShardsPerRead, false, true).getTargets();
    }

    /**
     * 同 {@link #targetsOf(String, Object, Object)}，但 {@code routeScoped=true} 时把实例收窄到
     * {@code routeValue}（租户）实际所在的那一个——租户内查询走这条，一次区间读只打一个库。
     */
    public List<ShardTarget> targetsOf(String table, Object routeValue, Object from, Object to, boolean routeScoped) {
        return scan(table, routeScoped ? routeValue : null, from, to, maxShardsPerRead, routeScoped, true).getTargets();
    }

    /**
     * 上限之内的扇出计划：超限不抛异常，置 {@code truncated=true} 交给调用方如实上报。
     * <p>
     * {@link ShardQueryFanout} 走这条入口——分页查询宁可用 {@code truncated} 标记告诉前端"结果不完整"，
     * 也不要一个 500 把整页打回。硬拒绝留给不经检查的直接调用（{@link #targetsOf}）。
     */
    public Scan targetsWithin(String table, Object routeValue, Object from, Object to, boolean routeScoped) {
        return scan(table, routeScoped ? routeValue : null, from, to, maxShardsPerRead, routeScoped, false);
    }

    /** 不带路由值的全实例扇出计划。 */
    public Scan targetsWithin(String table, Object from, Object to) {
        return scan(table, null, from, to, maxShardsPerRead, false, false);
    }

    /**
     * 枚举 (实例下标集合 × 时间区间) 的分片计划。
     *
     * @param routeScoped 为真只看 {@code routeValue} 所在实例；为假该表所有 buckets 实例都扫
     * @param hardFail    为真时超过上限直接拒绝；为假时截断并置 {@code truncated}
     */
    private Scan scan(String table, Object routeValue, Object from, Object to,
                      int cap, boolean routeScoped, boolean hardFail) {
        ShardProperties.Rule rule = requireRule(table);
        LocalDate fromDate = toLocalDate(from, table);
        LocalDate toDate = toLocalDate(to, table);
        if (fromDate.isAfter(toDate)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "分片表 " + table + " 的区间反了: from=" + fromDate + " > to=" + toDate);
        }

        List<LocalDate> periods = periodStarts(rule, fromDate, toDate);
        int buckets = Math.max(1, Math.min(rule.getBuckets(), instanceIds.size()));
        int instanceFanout = routeScoped || rule.getRouteBy() == ShardRouteBy.NONE ? 1 : buckets;
        // 先算总量再决定拒绝还是截断：判断必须在生成 ShardTarget 之前，否则"半截计划"可能被当成完整计划用掉
        int needed = periods.size() * instanceFanout;
        if (needed > cap && hardFail) {
            throw ShardErrors.tooManyShards(table, needed, cap);
        }

        List<ShardTarget> targets = new ArrayList<ShardTarget>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        // 新→旧：读侧默认按时间倒序，最新的分片先出，翻页体感与"截断即不完整"的语义才对得上
        for (int i = periods.size() - 1; i >= 0 && targets.size() < cap; i--) {
            LocalDate period = periods.get(i);
            String suffix = suffix(rule, period);
            for (int index : instanceIndexes(rule, routeValue, period, buckets, routeScoped)) {
                if (!seen.add(index + "|" + suffix)) {
                    // 去重：mode=NONE 只有一个周期，或 routeBy=NONE 时相邻周期会落到同一个 (实例, 后缀)
                    continue;
                }
                targets.add(new ShardTarget(table, suffix, instanceIds.get(index), index,
                        periodStart(rule, period), periodEnd(rule, period)));
                if (targets.size() >= cap) {
                    break;
                }
            }
        }
        return new Scan(targets, needed > cap, needed, cap);
    }

    private List<Integer> instanceIndexes(ShardProperties.Rule rule, Object routeValue,
                                          LocalDate period, int buckets, boolean routeScoped) {
        List<Integer> indexes = new ArrayList<Integer>();
        if (rule.getRouteBy() == ShardRouteBy.NONE) {
            indexes.add(buckets == 1 ? 0 : bucketIndexOf(stableHash(rotationKey(rule, period)), buckets));
            return indexes;
        }
        if (routeScoped) {
            indexes.add(instanceIndex(rule.getTable(), routeValue));
            return indexes;
        }
        for (int i = 0; i < buckets; i++) {
            indexes.add(i);
        }
        return indexes;
    }

    /** 区间内每个周期的起点（MONTH 取每月 1 号，DAY 取每天，NONE 只取区间起点一个周期）。升序。 */
    private List<LocalDate> periodStarts(ShardProperties.Rule rule, LocalDate from, LocalDate to) {
        List<LocalDate> periods = new ArrayList<LocalDate>();
        if (rule.getMode() == ShardMode.NONE) {
            periods.add(from);
            return periods;
        }
        if (rule.getMode() == ShardMode.DAY) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                periods.add(d);
            }
            return periods;
        }
        YearMonth last = YearMonth.from(to);
        for (YearMonth m = YearMonth.from(from); !m.isAfter(last); m = m.plusMonths(1)) {
            periods.add(m.atDay(1));
        }
        return periods;
    }

    private LocalDateTime periodStart(ShardProperties.Rule rule, LocalDate period) {
        return rule.getMode() == ShardMode.NONE ? null : period.atStartOfDay();
    }

    private LocalDateTime periodEnd(ShardProperties.Rule rule, LocalDate period) {
        if (rule.getMode() == ShardMode.NONE) {
            return null;
        }
        if (rule.getMode() == ShardMode.DAY) {
            return period.plusDays(1).atStartOfDay();
        }
        return YearMonth.from(period).plusMonths(1).atDay(1).atStartOfDay();
    }

    /** {@code 202611}。 */
    private static String compactMonth(LocalDate period) {
        return String.format("%04d%02d", period.getYear(), period.getMonthValue());
    }

    /** {@code 20261106}。 */
    private static String compactDay(LocalDate period) {
        return String.format("%04d%02d%02d", period.getYear(), period.getMonthValue(), period.getDayOfMonth());
    }

    /** 按 {@code mode} 选实例轮转键：DAY 按天、其余按月，保证与后缀粒度同源。 */
    private static String rotationKey(ShardProperties.Rule rule, LocalDate period) {
        return rule.getMode() == ShardMode.DAY ? compactDay(period) : compactMonth(period);
    }

    // ------------------------------------------------------------------
    // 时间值归一
    // ------------------------------------------------------------------

    /**
     * 把接受的时间值收敛到 {@link LocalDate}。不接受的类型一律按类型名拒绝。
     * <p>
     * 时区口径：{@link java.util.Date}/{@link java.sql.Timestamp} 用 JVM 默认时区取日历日，
     * 与本仓库 {@code serverTimezone=Asia/Shanghai} 的连接串一致。跨时区部署时这里的口径要跟 DB 会话时区
     * 一起改，不能只改一侧——只改一侧的症状是月末/月初的记录被分到相邻的表里。
     */
    static LocalDate toLocalDate(Object timeValue, String table) {
        if (timeValue == null) {
            throw ShardErrors.blankRouteValue(table, "时间值");
        }
        if (timeValue instanceof LocalDateTime) {
            return ((LocalDateTime) timeValue).toLocalDate();
        }
        if (timeValue instanceof LocalDate) {
            return (LocalDate) timeValue;
        }
        if (timeValue instanceof java.util.Date) {
            // java.sql.Timestamp / java.sql.Date 都是 java.util.Date 的子类，走同一条路
            java.util.Date date = (java.util.Date) timeValue;
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault()).toLocalDate();
        }
        if (timeValue instanceof String) {
            return parseDate((String) timeValue, table);
        }
        throw ShardErrors.unsupportedTimeValue(timeValue, table);
    }

    private static LocalDate parseDate(String raw, String table) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw ShardErrors.blankRouteValue(table, "时间值");
        }
        try {
            if (value.length() == 6 && allDigits(value)) {
                // YearMonth.parse 只认 ISO 的 "2026-11"，紧凑形必须显式给格式，否则本方法宣称支持的
                // "202611" 会在这里抛"无法解析"——症状是运维照报错文案传值反而传不进
                return YearMonth.parse(value, java.time.format.DateTimeFormatter.ofPattern("uuuuMM")).atDay(1);
            }
            if (value.length() == 8 && allDigits(value)) {
                return LocalDate.parse(value, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            }
            String normalized = value.replace(' ', 'T');
            if (normalized.length() == 7) {
                return YearMonth.parse(normalized).atDay(1);
            }
            if (normalized.length() == 10) {
                return LocalDate.parse(normalized);
            }
            // 带时间/时区偏移的完整形态：前 10 位一定是 yyyy-MM-dd，取日历日即可
            if (normalized.length() > 10 && normalized.charAt(10) == 'T') {
                return LocalDate.parse(normalized.substring(0, 10));
            }
        } catch (DateTimeParseException e) {
            throw unparseable(raw, table);
        }
        throw unparseable(raw, table);
    }

    private static BizException unparseable(String raw, String table) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片表 " + table + " 的时间字符串无法解析: \"" + raw + "\"（接受 2026-11-06 / "
                        + "2026-11-06T10:15:30 / 2026-11 / 202611 / 20261106）");
    }

    private static boolean allDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 一次区间扇出的计划。
     * <p>
     * {@code truncated} 为真表示"分片数被上限截断，结果集不完整"——调用方<b>必须</b>把这个标志透传到
     * 响应里，不能只把已扫到的分片结果当成全部。
     */
    public static final class Scan {
        private final List<ShardTarget> targets;
        private final boolean truncated;
        private final int requiredShards;
        private final int cap;

        Scan(List<ShardTarget> targets, boolean truncated, int requiredShards, int cap) {
            this.targets = targets;
            this.truncated = truncated;
            this.requiredShards = requiredShards;
            this.cap = cap;
        }

        public List<ShardTarget> getTargets() {
            return targets;
        }

        public boolean isTruncated() {
            return truncated;
        }

        /** 区间本需要的分片数（可能大于 {@code cap}）。 */
        public int getRequiredShards() {
            return requiredShards;
        }

        public int getCap() {
            return cap;
        }
    }
}
