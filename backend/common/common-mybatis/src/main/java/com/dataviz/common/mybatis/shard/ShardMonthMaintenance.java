package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 月表预建：把"下个月的分片表不存在"这件事从凌晨第一条写入的报错，变成一次可查日志的巡检。
 *
 * <h3>开关是硬门控，默认关</h3>
 * <p>
 * {@code shard.precreate.enabled} 缺省 {@code false}。本仓库有看门测试（alert-service 的
 * {@code AlertCheckEngineRetirementTest}）断言"每个 {@code @Scheduled} 要么唯一、要么显式默认关"，
 * 未门控的定时器会直接把那道闸门撞红；更实际的理由是：克隆下来就能改生产库结构的任务不该默认在跑。
 * 门控做了两层——{@link ShardConfiguration} 里本 bean 带 {@code @ConditionalOnProperty}（关时不建 bean），
 * 方法里再读一次 {@link #precreateEnabled}（防有人绕过装配直接 new）。
 * <p>
 * 另：本类<b>不</b>声明 {@code @EnableScheduling}。各服务的定时开关归各服务，在这里开等于悄悄改了 17 个模块的运行时行为。
 *
 * <h3>逻辑表必须先在该实例上存在</h3>
 * <p>
 * 兜底 DDL 用的是 {@code CREATE TABLE IF NOT EXISTS <物理> LIKE <逻辑>}。{@code LIKE} 依赖逻辑表在<b>同一个库</b>
 * 里存在；分片实例上缺逻辑表（新库没跑 init.sql）时 MySQL 直接报错，运维看到的是"建表失败"而不是"缺基表"。
 * 所以这里先查一次 {@code SHOW TABLES LIKE ?}，缺表就带<b>实例 id + 表名</b>失败，不给半截结果。
 */
@Slf4j
public class ShardMonthMaintenance {

    /** {@code CREATE TABLE IF NOT EXISTS} 兜底模板；仅在 {@code shard.precreate.ddl-template} 未配置时用。 */
    static final String FALLBACK_TEMPLATE = "CREATE TABLE IF NOT EXISTS {PHYSICAL_TABLE} LIKE {LOGICAL_TABLE}";

    private final ShardProperties properties;
    private final ShardRouter router;
    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;

    /**
     * 看门测试要能静态看到的那个门：属性名 {@code shard.precreate.enabled}，默认值 {@code false}。
     */
    @Value("${shard.precreate.enabled:false}")
    private boolean precreateEnabled;

    public ShardMonthMaintenance(ShardProperties properties, ShardRouter router,
                                 DataSource dataSource, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.router = router;
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 每月预建巡检。默认不启用（见类注释）；启用时每天 03:15 跑一次，把当月到 {@code pre-create-months}
     * 之后的那个月补齐。
     * <p>
     * 整个方法吞掉异常只记日志：定时任务抛出去会被 Spring 的线程池吃掉，反而看不到栈。
     */
    @Scheduled(cron = "${shard.precreate.cron:0 15 3 * * ?}")
    public void preCreateNextMonthTables() {
        if (!precreateEnabled) {
            log.debug("分片月表预建未启用（shard.precreate.enabled=false），跳过");
            return;
        }
        try {
            int executed = execute(LocalDate.now());
            log.info("分片月表预建完成：执行 {} 条 DDL", executed);
        } catch (Exception e) {
            log.error("分片月表预建失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 只算不执行：枚举 {@code (月表规则 × 提前月数 × 实例)} 的 DDL 任务。
     * <p>
     * 抽出来是为了能在没有数据库的情况下断言"下个月跨年时建的是哪张表"。
     */
    public List<DdlTask> plan(LocalDate baseDate) {
        List<DdlTask> tasks = new ArrayList<DdlTask>();
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate 不能为空");
        }
        LocalDate today = baseDate;
        for (ShardProperties.Rule rule : properties.getRules()) {
            if (rule == null || rule.getMode() != ShardMode.MONTH) {
                // DAY 表由日巡检负责（配置期已强制 pre-create-months=0）；NONE 没有月表可建
                continue;
            }
            String template = templateFor(rule.getTable());
            for (int i = 0; i <= Math.max(0, rule.getPreCreateMonths()); i++) {
                LocalDate month = today.plusMonths(i);
                String suffix = ShardRouter.suffix(rule, month);
                String physical = ShardRouter.physical(rule.getTable(), suffix);
                String ddl = render(template, rule.getTable(), physical, suffix);
                int buckets = Math.max(1, Math.min(rule.getBuckets(), properties.getInstances().size()));
                for (int index = 0; index < buckets; index++) {
                    String instanceId = properties.instanceIds().get(index);
                    tasks.add(new DdlTask(instanceId, index, rule.getTable(), physical, suffix, ddl));
                }
            }
        }
        return tasks;
    }

    /** 按计划执行 DDL，返回执行的 DDL 条数。 */
    public int execute(LocalDate baseDate) {
        List<DdlTask> tasks = plan(baseDate);
        int executed = 0;
        for (DdlTask task : tasks) {
            // 绑定一个显式目标：连接要从这个实例拿，逻辑表存在性检查也必须在同一个库上做
            try (ShardContext.ShardScope scope = ShardContext.open(
                    new ShardTarget(task.getLogicalTable(), task.getSuffix(), task.getInstanceId(),
                            task.getInstanceIndex(), null, null))) {
                try (Connection connection = dataSource.getConnection()) {
                    if (FALLBACK_TEMPLATE.equals(templateFor(task.getLogicalTable()))) {
                        // 只有兜底 LIKE 模板依赖基表存在；有模板时模板自带结构定义，不该拿"缺逻辑表"挡新库
                        requireLogicalTable(connection, task);
                    }
                    for (String statement : splitStatements(task.getDdl())) {
                        try (Statement st = connection.createStatement()) {
                            st.execute(statement);
                            executed++;
                        }
                    }
                } catch (SQLException e) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "分片月表预建失败 instance=" + task.getInstanceId() + " table=" + task.getPhysicalTable()
                                    + "：" + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
        return executed;
    }

    /** 逻辑表在该实例上不存在 → 带实例 id 与表名失败（{@code LIKE} 没有基表可用）。 */
    private void requireLogicalTable(Connection connection, DdlTask task) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SHOW TABLES LIKE ?")) {
            ps.setString(1, task.getLogicalTable());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "分片实例 " + task.getInstanceId() + " 上缺逻辑表 " + task.getLogicalTable()
                                    + "，无法用 CREATE TABLE ... LIKE 预建 " + task.getPhysicalTable()
                                    + "。请先在该实例执行 deploy/sql/init 下的建表脚本。");
                }
            }
        }
    }

    /** 该表的 DDL 模板内容：配置了就用配置的 classpath 资源，没配置用兜底 {@code LIKE} 模板。 */
    String templateFor(String logicalTable) {
        ShardProperties.PreCreate precreate = properties.getPrecreate();
        String location = precreate == null ? null : precreate.getDdlTemplate();
        if (location == null || location.trim().isEmpty()) {
            return FALLBACK_TEMPLATE;
        }
        try {
            return StreamUtils.copyToString(
                    resourceLoader.getResource(location.trim()).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "分片 DDL 模板读取失败 shard.precreate.ddl-template=" + location + ": "
                            + e.getClass().getSimpleName());
        }
    }

    static String render(String template, String logicalTable, String physicalTable, String suffix) {
        return template.replace("{PHYSICAL_TABLE}", quoted(physicalTable))
                .replace("{LOGICAL_TABLE}", quoted(logicalTable))
                .replace("{SUFFIX}", suffix == null ? "" : suffix);
    }

    /**
     * 反引号包裹。标识符已经过 {@link ShardRouter#physical} 的白名单，这里的引号是"再挡一道"而不是唯一防线：
     * 一旦去掉白名单，反引号里塞 {@code `;} 就能绕过去，两者必须同时在。
     */
    private static String quoted(String identifier) {
        return "`" + identifier + "`";
    }

    /** 按分号切分多条 DDL；模板里不允许出现带分号的字符串字面量（注释掉也没用，直接写单条更稳）。 */
    static List<String> splitStatements(String ddl) {
        List<String> statements = new ArrayList<String>();
        for (String part : ddl.split(";")) {
            String statement = stripComments(part).trim();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }

    private static String stripComments(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.startsWith("#") || trimmed.isEmpty()) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    public boolean isPrecreateEnabled() {
        return precreateEnabled;
    }

    /** 一条待执行的建表任务：落在哪个实例、建哪张物理表、执行什么 DDL。 */
    @Getter
    public static final class DdlTask {

        private final String instanceId;
        private final int instanceIndex;
        private final String logicalTable;
        private final String physicalTable;
        private final String suffix;
        private final String ddl;

        DdlTask(String instanceId, int instanceIndex, String logicalTable, String physicalTable,
                String suffix, String ddl) {
            this.instanceId = instanceId;
            this.instanceIndex = instanceIndex;
            this.logicalTable = logicalTable;
            this.physicalTable = physicalTable;
            this.suffix = suffix;
            this.ddl = ddl;
        }

        @Override
        public String toString() {
            return instanceId + " -> " + physicalTable;
        }
    }
}
