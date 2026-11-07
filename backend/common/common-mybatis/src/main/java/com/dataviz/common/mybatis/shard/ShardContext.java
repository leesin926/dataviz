package com.dataviz.common.mybatis.shard;

/**
 * 一条语句的分片作用域：ThreadLocal 里绑定的 {@code (租户, 时间)}。
 *
 * <h3>为什么键值由调用方<b>显式</b>声明，而不是从 SQL 里解析出来</h3>
 * <p>
 * 最直觉的做法是把 SQL 交给一个解析器，从 {@code WHERE tenant_id = ? AND create_time > ?} 里把路由键
 * 抠出来，业务代码零改动。这条路本项目<b>不走</b>，理由有三条，按重要性排：
 * <ol>
 *   <li><b>离线仓库里没有解析器可用</b>。本模块只拿到 {@code DynamicTableNameInnerInterceptor} 与
 *       {@code AbstractRoutingDataSource} 这两块地基，MyBatis 自带的 {@code TableNameParser} 只认
 *       "哪个词是表名"，不认"这个谓词等不等于那个常量"。真要自己写值提取，等于在仓库里养半个 SQL 引擎。</li>
 *   <li><b>解析出来的猜测比显式声明更容易出错，而且错得安静</b>。谓词可以写成 {@code create_time BETWEEN}、
 *       子查询、{@code OR}、函数包裹、也可以是 Mapper XML 里 {@code <if>} 动态拼出来根本没带时间条件。
 *       任何一种解析不出来，只有两种收场：退回默认实例（数据写进错误的库，事后按租户捞不到），或者
 *       每库都扫一遍（性能塌方）。两种都不是"报错"，是"看起来成功了"。</li>
 *   <li><b>调用方本来就知道这两个值</b>。租户号在 {@code SecurityContextHolder} 里，时间区间是请求参数。
 *       在 Service 入口写一行 {@code ShardContext.open(...)}，换来的是路由决策<b>可单测、可复现、可在
 *       日志里指名道姓</b>——账本类表（{@code audit_log}、{@code openapi_log}）要的就是这一点。</li>
 * </ol>
 * 代价说清楚：显式绑定要求"要分片的读写路径都得包一层"。漏包的地方不会静默跑偏——
 * {@link ShardInnerInterceptor}（SQL 层）与 {@link ShardRoutingDataSource}（连接层）都拒绝执行，
 * 报同一句话、点名表名。这是"宁可当场拒绝，不猜"的取舍。
 *
 * <h3>嵌套</h3>
 * <p>
 * {@link ShardScope#close()} 恢复的是<b>打开前那一刻</b>的绑定，因此区间扇出（外层已绑、内层逐分片覆盖）
 * 与 {@code try}-with-resources 组合是安全的；线程池复用线程时请确保 scope 一定关闭，否则下一个请求
 * 会继承上一个请求的分片键——这类 bug 的表象是"偶发查到别人的数据"。
 */
public final class ShardContext {

    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<Binding>();

    private ShardContext() {
    }

    /**
     * 绑定 {@code (表, 租户, 时间)} 并返回作用域句柄；配合 try-with-resources 使用。
     * <p>
     * 不做校验（保持本类零依赖），校验在 {@link ShardRouter} 解析时发生，届时错误会点名表名。
     */
    public static ShardScope open(String logicalTable, Object tenantId, Object timeValue) {
        Binding binding = new Binding(logicalTable, tenantId, timeValue, null, null);
        return activate(binding);
    }

    /** 无租户维度（{@code routeBy=NONE}）的绑定：只需要时间。 */
    public static ShardScope open(String logicalTable, Object timeValue) {
        return open(logicalTable, null, timeValue);
    }

    /**
     * 绑定一个<b>已解析</b>的分片目标（扇出读用）。
     * <p>
     * 走这条入口时实例与后缀都已确定，后续解析不再重算——扇出要求"遍历顺序由路由决定"，
     * 不允许每条子查询再各自算一遍可能不一致的结果。
     */
    public static ShardScope open(ShardTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("target 不能为空");
        }
        Binding binding = new Binding(target.getLogicalTable(), null, target.getTimeFrom(),
                target.getInstanceId(), target.getSuffix());
        return activate(binding);
    }

    private static ShardScope activate(Binding binding) {
        Binding previous = CURRENT.get();
        CURRENT.set(binding);
        return new ShardScope(previous);
    }

    /** 当前绑定；未绑定返回 {@code null}。 */
    public static Binding current() {
        return CURRENT.get();
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    /**
     * 无条件清空。给拦截器兜底、测试与线程归还前用；正常路径请用 try-with-resources。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 一次绑定的内容。{@code resolvedInstanceId/resolvedSuffix} 由 {@link ShardRouter} 填充，
     * 一旦填好就不再重算。
     */
    public static final class Binding {

        private final String logicalTable;
        private final Object tenantId;
        private final Object timeValue;
        private String resolvedInstanceId;
        private String resolvedSuffix;

        private Binding(String logicalTable, Object tenantId, Object timeValue,
                        String resolvedInstanceId, String resolvedSuffix) {
            this.logicalTable = logicalTable;
            this.tenantId = tenantId;
            this.timeValue = timeValue;
            this.resolvedInstanceId = resolvedInstanceId;
            this.resolvedSuffix = resolvedSuffix;
        }

        public String getLogicalTable() {
            return logicalTable;
        }

        public Object getTenantId() {
            return tenantId;
        }

        public Object getTimeValue() {
            return timeValue;
        }

        /** 目标实例 id；尚未解析时为 {@code null}。 */
        public String getResolvedInstanceId() {
            return resolvedInstanceId;
        }

        /** 物理表后缀；尚未解析时为 {@code null}（区别于"无后缀的空串"）。 */
        public String getResolvedSuffix() {
            return resolvedSuffix;
        }

        void setResolvedInstanceId(String instanceId) {
            this.resolvedInstanceId = instanceId;
        }

        void setResolvedSuffix(String suffix) {
            this.resolvedSuffix = suffix;
        }

        /** 日志安全：只有表名、实例与后缀，没有连接串与口令。 */
        @Override
        public String toString() {
            return "Binding{table=" + logicalTable + ", instance=" + resolvedInstanceId
                    + ", suffix=" + resolvedSuffix + "}";
        }
    }

    /**
     * try-with-resources 句柄：{@link #close()} 把绑定恢复成打开前的状态（外层绑定或空）。
     */
    public static final class ShardScope implements AutoCloseable {

        private final Binding previous;
        private boolean closed;

        private ShardScope(Binding previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                // 幂等：close 两次若真的恢复两次，会把上一帧的绑定错还原成更早的帧
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
