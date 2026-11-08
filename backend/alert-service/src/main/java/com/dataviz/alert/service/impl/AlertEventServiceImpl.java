package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.mapper.AlertEventMapper;
import com.dataviz.alert.service.AlertEventService;
import com.dataviz.alert.vo.AlertEventVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.mybatis.shard.ShardContext;
import com.dataviz.common.mybatis.shard.ShardQueryFanout;
import com.dataviz.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AlertEventService} 的实现：所有 {@code alert_event} 的 SQL 都从这一层出去。
 *
 * <p><b>两条路径的语义差别，先说清楚，免得被当成"开开关前后一样"：</b>
 * <ul>
 *   <li>{@code shard.enabled=false}（默认，也是绝大多数部署）：与本轮之前逐字节相同 ——
 *       一条单表 SQL，列表与计数覆盖 {@code alert_event} 全历史，没有窗口、没有分片读数。</li>
 *   <li>{@code shard.enabled=true}：列表/计数/定位都只在<b>最近 {@code alert.event.query-months} 个月</b>
 *       的月表上做。这个窗口不是保守，是<b>物理边界</b>：月表只按 {@code deploy/sql/shard} 的窗口
 *       （202608~202702）建出来，把窗口拉到不存在的月份上就是 {@code Table doesn't exist}。
 *       所以这件事必须让调用方知道：分页带回 {@code windowDefaulted}，计数带回 {@code partial}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventServiceImpl implements AlertEventService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String SEVERITY_CRITICAL = "CRITICAL";

    private final AlertEventMapper alertEventMapper;

    /**
     * 只在 {@code shard.enabled=true} 时存在。用 {@link ObjectProvider} 而不是直接注入：
     * 立身之本是"关掉等于不存在"，构造期就要求它存在等于逼每个不开分片的测试配一套分片。
     */
    private final ObjectProvider<ShardQueryFanout> shardFanout;

    /**
     * 分片模式下的读窗口（月）。默认 3 与月表实际建出来的范围对齐 —— 今天往前推 3 个月正好落在
     * 最早那张已建月表上；调大之前必须先去建月表，否则不是"多扫几张表"而是当场报表不存在。
     */
    @Value("${alert.event.query-months:3}")
    private int queryMonths = 3;

    @Override
    @Transactional
    public AlertEvent create(AlertEvent event) {
        // 路由键必须在绑定之前就是最终值。createTime 平时由 FieldFill.INSERT 填，但那发生在 mapper 内部、
        // 作用域已经打开之后 —— 于是会出现"按 A 月路由、行里写的是 B 月"。这里先把值定下来并写回对象，
        // 让路由键与落库值是同一个对象字段，不是两次 now()。
        LocalDateTime when = event.getCreateTime() != null ? event.getCreateTime() : LocalDateTime.now();
        event.setCreateTime(when);
        try (ShardContext.ShardScope scope =
                     ShardContext.open(AlertEventShardReader.TABLE, event.getTenantId(), when)) {
            alertEventMapper.insert(event);
        }
        return event;
    }

    @Override
    @Transactional
    public void update(AlertEvent event) {
        if (event.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回写告警事件必须带 id，否则 updateById 无从定位");
        }
        // 路由键取自这一行自己（create() 或 locate() 已经把它带上来了），不去读会话：
        // 调度线程上没有租户，而"用请求者的租户去改别人的事件"本身就是越权
        if (event.getCreateTime() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "回写告警事件缺少 create_time（分表键）：event id=" + event.getId()
                            + "。这条必须由 locate()/create() 带回整行后再改，不能凭裸 id 更新 —— 裸 id 定不出月表。");
        }
        try (ShardContext.ShardScope scope =
                     ShardContext.open(AlertEventShardReader.TABLE, event.getTenantId(), event.getCreateTime())) {
            alertEventMapper.updateById(event);
        }
    }

    @Override
    public boolean hasOpenEvent(Long ruleId, Long tenantId) {
        ShardQueryFanout fanout = shardFanout == null ? null : shardFanout.getIfAvailable();
        if (fanout == null) {
            return alertEventMapper.selectCount(openWrapper(ruleId, null, null)) > 0;
        }
        Window window = window();
        AlertEventShardReader.CountOutcome counted = new AlertEventShardReader(fanout, alertEventMapper)
                .count(() -> openWrapper(ruleId, window.from, window.to), tenantId, window.from, window.to);
        if (counted.isTruncated()) {
            // 抑制判定被截断 ⇒ 宁可少发一条重复告警，也不能按"没找到"去刷屏：
            // 少算是这里唯一会放大用户可见影响的量（错删那类在这里不存在，这张表没有删除路径）
            log.warn("重复告警抑制的分片计数被截断（ruleId={}, 已扫 {} 片），本轮按\"已有未处理事件\"处理",
                    ruleId, counted.getShards());
            return true;
        }
        return counted.getTotal() > 0;
    }

    @Override
    public AlertEvent locate(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "定位告警事件需要 id");
        }
        ShardQueryFanout fanout = shardFanout == null ? null : shardFanout.getIfAvailable();
        if (fanout == null) {
            return alertEventMapper.selectById(id);
        }
        Window window = window();
        return new AlertEventShardReader(fanout, alertEventMapper)
                .locate(id, sessionTenant(), window.from, window.to);
    }

    @Override
    @Transactional
    public void markStatus(Long id, String status, boolean resolved) {
        AlertEvent event = locate(id);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "没有找到告警事件 id=" + id + "（分片模式下只在本窗口内按 id 逐张月表找：" + describeWindow() + "）");
        }
        event.setStatus(status);
        if (resolved) {
            event.setResolvedAt(LocalDateTime.now());
        }
        update(event);
    }

    @Override
    public PageResult<AlertEventVO> page(Long ruleId, String rawStatus, String rawSeverity,
                                         int pageNum, int pageSize) {
        int page = pageNum > 0 ? pageNum : 1;
        int size = pageSize > 0 ? pageSize : 10;
        String status = StringUtils.hasText(rawStatus) ? rawStatus.trim() : null;
        String severity = StringUtils.hasText(rawSeverity) ? rawSeverity.trim() : null;

        ShardQueryFanout fanout = shardFanout == null ? null : shardFanout.getIfAvailable();
        if (fanout == null) {
            Page<AlertEvent> result = alertEventMapper.selectPage(new Page<AlertEvent>(page, size),
                    rowWrapper(ruleId, status, severity, null, null));
            return PageResult.of(toVOs(result.getRecords()), result.getTotal(), page, size);
        }

        Window window = window();
        Long tenant = sessionTenant();
        final LocalDateTime from = window.from;
        final LocalDateTime to = window.to;
        AlertEventShardReader.PageOutcome outcome = new AlertEventShardReader(fanout, alertEventMapper)
                .page(() -> rowWrapper(ruleId, status, severity, from, to),
                        () -> conditionsOnly(ruleId, status, severity, from, to),
                        tenant, from, to, page, size);
        return PageResult.ofSharded(toVOs(outcome.getRecords()), outcome.getTotal(), page, size,
                outcome.isTruncated(), outcome.getShards(), true);
    }

    @Override
    public EventCounts counts() {
        ShardQueryFanout fanout = shardFanout == null ? null : shardFanout.getIfAvailable();
        if (fanout == null) {
            // 没有会话就没有租户条件，与租户拦截器的"无会话不注入"同口径：这里不猜一个默认租户
            return EventCounts.builder()
                    .pending(countWhere(null, STATUS_PENDING, null, null, null))
                    .acknowledged(countWhere(null, STATUS_ACKNOWLEDGED, null, null, null))
                    .resolved(countWhere(null, STATUS_RESOLVED, null, null, null))
                    .critical(countWhere(null, null, SEVERITY_CRITICAL, null, null))
                    // 单表路径就是全历史计数，没有"这只是窗口内的数"这回事
                    .partial(false)
                    .build();
        }
        Long tenant = sessionTenant();
        Window window = window();
        final LocalDateTime from = window.from;
        final LocalDateTime to = window.to;
        AlertEventShardReader reader = new AlertEventShardReader(fanout, alertEventMapper);
        long pending = reader.count(() -> conditionsOnly(null, STATUS_PENDING, null, from, to), tenant, from, to).getTotal();
        long acknowledged = reader.count(() -> conditionsOnly(null, STATUS_ACKNOWLEDGED, null, from, to), tenant, from, to).getTotal();
        long resolved = reader.count(() -> conditionsOnly(null, STATUS_RESOLVED, null, from, to), tenant, from, to).getTotal();
        long critical = reader.count(() -> conditionsOnly(null, null, SEVERITY_CRITICAL, from, to), tenant, from, to).getTotal();
        return EventCounts.builder()
                .pending(pending).acknowledged(acknowledged).resolved(resolved).critical(critical)
                // 窗口是服务端补的 ⇒ 这四个数不是全历史。数字看起来完全正常，所以这个标记必须带出去
                .partial(true)
                .build();
    }

    // ------------------------------------------------------------------
    // wrapper 构造：行查询与计数各一个，差别只在 ORDER BY
    // ------------------------------------------------------------------

    private long countWhere(Long ruleId, String status, String severity, LocalDateTime from, LocalDateTime to) {
        Long n = alertEventMapper.selectCount(conditionsOnly(ruleId, status, severity, from, to));
        return n == null ? 0L : n;
    }

    /**
     * 行查询：条件 + 排序。<b>不带列投影</b> —— {@code alert_event} 没有需要屏蔽的列
     * （{@code message} 是规则自己的告警文案，本来就要展示），而 openapi 那条之所以要白名单，
     * 是因为那里有 {@code request_params}。少了投影，也就少了一个"计数 wrapper 忘剥投影"的坑。
     */
    private LambdaQueryWrapper<AlertEvent> rowWrapper(Long ruleId, String status, String severity,
                                                     LocalDateTime from, LocalDateTime to) {
        return conditionsOnly(ruleId, status, severity, from, to)
                // create_time 同秒的事件很多（一轮巡检里多条规则同时越界），补 id 兜底才能保证翻页不重不漏；
                // 归并用的 AlertEventShardReader.NEWEST_FIRST 必须与这里同序
                .orderByDesc(AlertEvent::getCreateTime).orderByDesc(AlertEvent::getId);
    }

    /**
     * 只有 WHERE 的条件构造 —— 计数走它。
     * <p>MP 的 {@code selectCount} 会把 wrapper 上的排序原样带出去，{@code COUNT(...) ... ORDER BY
     * create_time} 在 {@code only_full_group_by} 下就是死路；投影更是要命（{@code COUNT(a, b)} → 1064）。
     * 这两条都是 PI5 在真进程里踩过的，形状上一次性避开。
     *
     * <p>{@code from}/{@code to} 非空时必须进 SQL：分片窗口不只是"扫哪几张月表"，还得画出行级边界 ——
     * 最早那张月表装的是整月的行，滚动下界落在它的中间，不画边界 total 就会把窗口外的事件算进来
     * （PI5 的 API-173，同一个坑不打算在这里再犯一次）。单表路径传 null，保持"覆盖全历史"的既有语义。
     */
    private LambdaQueryWrapper<AlertEvent> conditionsOnly(Long ruleId, String status, String severity,
                                                         LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>();
        if (ruleId != null) {
            wrapper.eq(AlertEvent::getRuleId, ruleId);
        }
        if (status != null) {
            wrapper.eq(AlertEvent::getStatus, status);
        }
        if (severity != null) {
            wrapper.eq(AlertEvent::getSeverity, severity);
        }
        if (from != null) {
            wrapper.ge(AlertEvent::getCreateTime, from);
        }
        if (to != null) {
            wrapper.le(AlertEvent::getCreateTime, to);
        }
        return wrapper;
    }

    /** 抑制判定用的条件：某条规则还有未处理事件就不要再生成一条 */
    private LambdaQueryWrapper<AlertEvent> openWrapper(Long ruleId, LocalDateTime from, LocalDateTime to) {
        return conditionsOnly(ruleId, STATUS_PENDING, null, from, to);
    }

    private Window window() {
        LocalDateTime to = LocalDateTime.now();
        return new Window(to.minusMonths(Math.max(1, queryMonths)), to);
    }

    private String describeWindow() {
        Window w = window();
        return "自 " + w.from + " 起 " + Math.max(1, queryMonths) + " 个月";
    }

    /** 会话租户号（路由键与权限边界都用它）；没有会话就拒绝分片读，不猜一个默认租户 */
    private Long sessionTenant() {
        String raw = SecurityContextHolder.getTenantId();
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "分片模式下按 alert_event 的租户路由需要租户会话；当前没有租户，不能猜一个默认实例");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "会话租户号不是数字: [" + raw + "]");
        }
    }

    private static List<AlertEventVO> toVOs(List<AlertEvent> rows) {
        List<AlertEventVO> records = new ArrayList<AlertEventVO>(rows.size());
        for (AlertEvent row : rows) {
            AlertEventVO vo = new AlertEventVO();
            BeanUtils.copyProperties(row, vo);
            records.add(vo);
        }
        return records;
    }

    /** 服务端补出来的读窗口。 */
    private static final class Window {
        private final LocalDateTime from;
        private final LocalDateTime to;

        private Window(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to = to;
        }
    }
}
