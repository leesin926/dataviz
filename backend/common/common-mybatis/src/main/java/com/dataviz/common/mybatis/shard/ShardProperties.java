package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分库分表配置（前缀 {@code shard}）。
 * <p>
 * <b>{@code shard.enabled} 缺省即 false</b>：关掉时 {@link ShardConfiguration} 整个被
 * {@code @ConditionalOnProperty} 挡住，一个 bean 都不建，拦截器链与数据源行为与今天完全一致；
 * 打开后本配置是<b>唯一</b>的数据源入口（{@code spring.datasource.*} 那套单库配置会被自动配置退让，
 * 见 {@link ShardConfiguration#shardDataSource}）。
 * <p>
 * 所有校验在绑定完成时（{@link InitializingBean#afterPropertiesSet()}）一次性做完，宁可启动失败也不要
 * 在第一条 SQL 上失败——配错一个 buckets 数字若在运行期才炸，症状是"某个租户偶发写错库"。
 * 错误文案只点出<b>键名和值</b>，实例口令永远不进消息。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shard")
public class ShardProperties implements InitializingBean {

    /**
     * 逻辑表名与列名的形状白名单。这些名字最终会被拼进 SQL 文本（表名没法参数化），
     * 所以在<b>配置期</b>就把 {@code logs; DROP TABLE x} 这类值挡掉，而不是等 SQL 层报错。
     * 首字符小写字母，总长 ≤ 63（MySQL 标识符上限 64，留出 6 位月后缀的余量）。
     */
    static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    /** 总开关。默认关：17 个模块与约 926 个现存测试的行为必须与今天逐字节一致。 */
    private boolean enabled = false;

    /** 单次区间读最多扇出多少个物理分片；超过直接拒绝，见 {@link ShardRouter#targetsOf}。 */
    private int maxShardsPerRead = 36;

    /** 物理实例清单；顺序有意义——下标 0 是"非分片表"的默认落点。 */
    private List<Instance> instances = new ArrayList<Instance>();

    /** 分片规则；只有列在这里的表才走分片，其余表（配置/CRUD 表）行为不变。 */
    private List<Rule> rules = new ArrayList<Rule>();

    /** 建表预创建（月度巡检）开关。 */
    private PreCreate precreate = new PreCreate();

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /**
     * 配置期校验。可单独调用（测试与非 Spring 场景），重复调用无副作用。
     *
     * @throws BizException 第一条不合法配置，消息点名出错的键与值
     */
    public void validate() {
        if (instances == null) {
            instances = new ArrayList<Instance>();
        }
        if (rules == null) {
            rules = new ArrayList<Rule>();
        }
        if (enabled && instances.isEmpty()) {
            throw reject("shard.instances", "shard.enabled=true 但实例清单为空，至少需要 1 个（id/url/username/password）");
        }
        if (maxShardsPerRead < 1) {
            throw reject("shard.max-shards-per-read", String.valueOf(maxShardsPerRead) + " 必须 ≥ 1");
        }
        if (precreate == null) {
            precreate = new PreCreate();
        }
        validateInstances();
        validateRules();
    }

    private void validateInstances() {
        Set<String> seen = new HashSet<String>();
        int index = 0;
        for (Instance instance : instances) {
            String key = "shard.instances[" + index + "]";
            if (instance == null) {
                throw reject(key, "为空");
            }
            requireName(key + ".id", instance.getId(), NAME_PATTERN);
            if (!seen.add(instance.getId())) {
                throw reject(key + ".id", instance.getId() + " 重复，实例 id 必须唯一（它是路由键）");
            }
            if (isBlank(instance.getUrl())) {
                throw reject(key + ".url", "为空");
            }
            if (isBlank(instance.getUsername())) {
                throw reject(key + ".username", "为空（实例 " + instance.getId() + "）");
            }
            Pool pool = instance.getPool();
            if (pool != null) {
                if (pool.getMaximumPoolSize() < 1) {
                    throw reject(key + ".pool.maximum-pool-size", String.valueOf(pool.getMaximumPoolSize()) + " 必须 ≥ 1");
                }
                if (pool.getMinimumIdle() < 0) {
                    throw reject(key + ".pool.minimum-idle", String.valueOf(pool.getMinimumIdle()) + " 不能为负");
                }
                if (pool.getMinimumIdle() > pool.getMaximumPoolSize()) {
                    throw reject(key + ".pool.minimum-idle", pool.getMinimumIdle()
                            + " 不能大于 maximum-pool-size " + pool.getMaximumPoolSize());
                }
                if (pool.getConnectionTimeout() < 1) {
                    throw reject(key + ".pool.connection-timeout", String.valueOf(pool.getConnectionTimeout()) + " 必须 ≥ 1 毫秒");
                }
            }
            index++;
        }
    }

    private void validateRules() {
        Set<String> seenTables = new HashSet<String>();
        int index = 0;
        for (Rule rule : rules) {
            String key = "shard.rules[" + index + "]";
            if (rule == null) {
                throw reject(key, "为空");
            }
            requireName(key + ".table", rule.getTable(), NAME_PATTERN);
            if (!seenTables.add(rule.getTable())) {
                throw reject(key + ".table", rule.getTable() + " 重复，一张逻辑表只能有一条规则");
            }
            if (rule.getMode() == null) {
                throw reject(key + ".mode", "为空，可选值 MONTH/DAY/NONE");
            }
            if (rule.getRouteBy() == null) {
                throw reject(key + ".route-by", "为空，可选值 TENANT/NONE");
            }
            if (rule.getMode() == ShardMode.NONE && rule.getRouteBy() == ShardRouteBy.NONE) {
                // 既不分表又不选实例 = 一条什么都不改的规则；留着只会在排查时误导
                throw reject(key, "table=" + rule.getTable()
                        + " 的 mode=NONE 且 route-by=NONE 无意义（既不分表也不路由实例），请删掉这条规则或改成 MONTH/DAY");
            }
            requireName(key + ".time-column", rule.getTimeColumn(), NAME_PATTERN);
            if (rule.getBuckets() < 1) {
                throw reject(key + ".buckets", rule.getTable() + " 的 buckets=" + rule.getBuckets() + " 必须 ≥ 1");
            }
            if (rule.getBuckets() > instances.size()) {
                throw reject(key + ".buckets", rule.getTable() + " 的 buckets=" + rule.getBuckets()
                        + " 超过实例数 " + instances.size() + "（分不到那么多库）");
            }
            if (rule.getPreCreateMonths() < 0) {
                throw reject(key + ".pre-create-months", rule.getTable() + " 的 pre-create-months="
                        + rule.getPreCreateMonths() + " 不能为负");
            }
            if (rule.getMode() == ShardMode.DAY && rule.getPreCreateMonths() > 0) {
                // DAY 表按天建，pre-create-months 语义不适用；不挡的话运维会以为自己在提前建日表
                throw reject(key + ".pre-create-months", rule.getTable()
                        + " 的 mode=DAY 时 pre-create-months 必须为 0（日表由日巡检负责，见 shard.precreate）");
            }
            index++;
        }
    }

    private static void requireName(String key, String value, Pattern pattern) {
        if (isBlank(value)) {
            throw reject(key, "为空");
        }
        if (!pattern.matcher(value).matches()) {
            throw reject(key, value + " 不合法：需匹配 ^[a-z][a-z0-9_]{0,62}$（会被拼进 SQL，形状不合法的标识符在配置期就拒）");
        }
    }

    private static BizException reject(String key, String detail) {
        return new BizException(ErrorCode.BAD_REQUEST, "分片配置非法 [" + key + "]: " + detail);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 规则查询（运行期热路径，线性扫一遍规则表；表数量个位数，不建索引）。 */
    public Rule ruleOf(String logicalTable) {
        if (logicalTable == null || rules == null) {
            return null;
        }
        String wanted = logicalTable.toLowerCase();
        for (Rule rule : rules) {
            if (rule != null && rule.getTable() != null && rule.getTable().equalsIgnoreCase(wanted)) {
                return rule;
            }
        }
        return null;
    }

    /** 实例 id 列表，顺序即 {@link Rule#getBuckets()} 的下标语义。 */
    public List<String> instanceIds() {
        List<String> ids = new ArrayList<String>();
        if (instances != null) {
            for (Instance instance : instances) {
                if (instance != null) {
                    ids.add(instance.getId());
                }
            }
        }
        return ids;
    }

    /** 第一个实例：非分片表的落点。调用方保证清单非空（{@link #validate()} 已挡）。 */
    public String defaultInstanceId() {
        List<String> ids = instanceIds();
        if (ids.isEmpty()) {
            throw reject("shard.instances", "为空，无法确定默认实例");
        }
        return ids.get(0);
    }

    /**
     * 单个物理实例。
     * <p>
     * {@code url/username/password} 只用于构造 Hikari 池对象；任何日志与异常文案都不引用它们，
     * 见 {@link ShardRoutingDataSource}。
     */
    @Getter
    @Setter
    public static class Instance {
        /** 路由键，如 {@code ds0}、{@code ds1}。 */
        private String id;
        private String url;
        private String username;
        /** 只进 HikariConfig 对象，不进任何字符串拼接。 */
        private String password;
        private Pool pool = new Pool();
    }

    /** Hikari 池参数。默认值是"分片池要小"的口径：总连接数 = 实例数 × maximum-pool-size。 */
    @Getter
    @Setter
    public static class Pool {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        /** 毫秒；取 5s，宁可快速失败也不要让请求线程挂在没起来的库上。 */
        private long connectionTimeout = 5000L;
    }

    /**
     * 一张逻辑表的分片规则。
     * <p>
     * 当前要分的是<b>只增表</b>：{@code alert_event}、{@code alert_notify_log}、{@code openapi_log}、
     * {@code sys_login_log}、{@code monitor_metric}、{@code query_history}、{@code audit_log}。
     * 配置/CRUD 表<b>不要</b>写进这里——它们要跨分片 update/join，本层不支持。
     */
    @Getter
    @Setter
    public static class Rule {
        /** 逻辑表名，如 {@code audit_log}；形状受 {@link #NAME_PATTERN} 约束。 */
        private String table;
        private ShardMode mode = ShardMode.MONTH;
        /** 时间列名，用于算后缀，默认 {@code create_time}。 */
        private String timeColumn = "create_time";
        private ShardRouteBy routeBy = ShardRouteBy.TENANT;
        /** 参与路由的实例个数，必须 ≤ {@code shard.instances} 的长度。 */
        private int buckets = 1;
        /** 月表提前建几个月，默认 1（当月 + 下月）。 */
        private int preCreateMonths = 1;
    }

    /** 预建表巡检开关，见 {@link ShardMonthMaintenance}。 */
    @Getter
    @Setter
    public static class PreCreate {
        /**
         * 默认 false。这是硬约束：本仓库有看门测试（alert-service 的
         * {@code AlertCheckEngineRetirementTest}）断言每个 {@code @Scheduled} 要么唯一要么默认关，
         * 未门控的定时器会直接把那道闸门撞红。
         */
        private boolean enabled = false;
        /**
         * DDL 模板的 classpath 位置（形如 {@code deploy/sql/shard/month-table.sql}），
         * 模板内用 {@code {PHYSICAL_TABLE}} / {@code {LOGICAL_TABLE}} / {@code {SUFFIX}} 占位。
         * 留空则退化为 {@code CREATE TABLE IF NOT EXISTS <物理> LIKE <逻辑>}——仅在模板尚未落地时用。
         */
        private String ddlTemplate;
        /** 巡检 cron，默认每天 03:15。仅在 {@link #enabled} 为 true 时生效。 */
        private String cron = "0 15 3 * * ?";
    }
}
