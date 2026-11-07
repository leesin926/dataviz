package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.openapi.dto.OpenApiLogQueryDTO;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.mapper.OpenApiClientMapper;
import com.dataviz.openapi.mapper.OpenApiLogMapper;
import com.dataviz.openapi.service.ApiLogService;
import com.dataviz.openapi.vo.OpenApiLogVO;
import com.dataviz.common.mybatis.shard.ShardContext;
import com.dataviz.common.mybatis.shard.ShardQueryFanout;
import com.dataviz.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * openapi_log 的写（代理审计）与读（管理端分页）。
 *
 * <p><b>读路径的两条底座口径，先说清楚，因为它们决定了能给什么条件、能给什么列：</b>
 * <ol>
 *   <li><b>租户</b>：{@code openapi_log.tenant_id} 是阶段 PI 才补上的<b>派生可空</b>列（pi4-01 从
 *       {@code openapi_client}/{@code openapi_app} 反查，鉴权失败的行至今与将来都留空），而它在
 *       {@code common-mybatis} 的 {@code MybatisPlusConfig.IGNORE_TENANT_TABLES} 黑名单里，拦截器
 *       <b>不会</b>给它注入租户条件。同族日志表（{@code etl_task_log} / {@code schedule_job_log} /
 *       {@code alert_notify_log}）的收口方式都是「靠父表定租户」（MybatisPlusConfig 的类注释就是这句），
 *       这里照办：先按会话租户列出可见的 {@code openapi_client}（该表有 tenant_id、由拦截器自动过滤），
 *       再用它的 id 集合圈住日志。<br>
 *       <b>为什么有了这一列还不改走它</b>：可空列当唯一收口会同时犯两种错 —— 历史行（列还没补时写的）
 *       凭空消失，而"租户号填了但客户端已删"的行凭空可见。父表收口这两条都不成立。
 *       列本身的用处是审计与分片排障，不是权限边界。</li>
 *   <li><b>deleted=0</b>：{@code application.yml} 全局配了 {@code logic-delete-field: deleted}，
 *       但 {@code openapi_log} 的 DDL 与实体都<b>没有</b> deleted 列/字段，MyBatis-Plus 对没有该字段的
 *       实体不拼逻辑删除条件，所以这里既不需要也没有「deleted=0」可加。不为此往实体补字段 ——
 *       库里没有列，补了只会让每一次读都 500（Unknown column）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLogServiceImpl implements ApiLogService {

    /** 分片层认的逻辑表名；与 {@code @TableName("openapi_log")} 必须同一个字符串，所以只在读侧类里写一次 */
    private static final String SHARD_TABLE = OpenApiLogShardReader.TABLE;

    /** result 允许值；SUCCESS 按 2xx 判定，与 OpenApiController 的 is2xxSuccess 同口径 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String RESULT_CLIENT_ERROR = "CLIENT_ERROR";
    public static final String RESULT_SERVER_ERROR = "SERVER_ERROR";
    public static final String RESULT_OTHER = "OTHER";

    private static final List<String> ALLOWED_RESULTS = Collections.unmodifiableList(Arrays.asList(
            RESULT_SUCCESS, RESULT_FAIL, RESULT_CLIENT_ERROR, RESULT_SERVER_ERROR, RESULT_OTHER));

    /** 服务代码只可能是配置里的开放接口目标名（见 openapi.proxy.services），形状收死再拼前缀 */
    private static final Pattern SERVICE_CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final OpenApiLogMapper logMapper;
    private final OpenApiClientMapper clientMapper;
    /**
     * 分片扇出执行器。用 {@link ObjectProvider} 而不是直接注入：它只在 {@code shard.enabled=true} 时存在，
     * 而这一层的立身之本就是"关掉等于不存在"—— 构造期就要求它存在，等于逼每个测试都得配一套分片。
     */
    private final ObjectProvider<ShardQueryFanout> shardFanout;

    /**
     * 调用方没给时间范围时服务端收窄成最近几个月。收窄要同时体现在两处：
     * 扇出的分片数，以及 SQL 里的 {@code create_time} 边界 —— 最早那张月表装的是整月的行，
     * 滚动窗口只覆盖它后半段，不画边界 total 就会虚高。单库路径不受这个开关影响。
     */
    @Value("${openapi.log.default-query-months:3}")
    private int defaultQueryMonths = 3;

    @Override
    public void saveLog(OpenApiLog apiLog) {
        // 写侧就把分片键说死：这条审计落哪张月表由 create_time 决定（openapi_log 是 route-by=NONE，
        // 按月份轮转实例，租户只是审计列 —— 因为鉴权失败的行天生没有租户，拿租户当路由键会写不进去）。
        // shard.enabled=false 时这层绑定没有任何副作用（拦截器没注册，没人读它）；打开后它决定路由，
        // 缺了就是一条当场拒绝的 400，而不是"写进一张谁都不读的表"。
        LocalDateTime when = apiLog.getCreateTime() != null ? apiLog.getCreateTime() : LocalDateTime.now();
        try (ShardContext.ShardScope scope = ShardContext.open(SHARD_TABLE, when)) {
            logMapper.insert(apiLog);
        }
    }

    @Override
    public PageResult<OpenApiLogVO> pageLogs(OpenApiLogQueryDTO query) {
        OpenApiLogQueryDTO condition = query != null ? query : new OpenApiLogQueryDTO();
        int pageNum = condition.getPageNum() > 0 ? condition.getPageNum() : 1;
        int pageSize = condition.getPageSize() > 0 ? condition.getPageSize() : 10;
        validate(condition);

        // 1) 租户与调用方都从父表 openapi_client 定位：只 select 定位需要的两列，app_secret 一次都不读
        List<Long> visibleClientIds = resolveVisibleClientIds(condition);
        if (visibleClientIds != null && visibleClientIds.isEmpty()) {
            // null = 没给调用方条件、按全部可见客户端不过滤；空集合 = 当前租户一个客户端都没有 ⇒ 合法空表
            return PageResult.empty(pageNum, pageSize);
        }

        ShardQueryFanout fanout = shardFanout == null ? null : shardFanout.getIfAvailable();
        if (fanout == null) {
            // 2) 分片关着 ⇒ 一条单表查询，行为与本轮之前逐字节相同（绝大多数部署与全部现存测试走这条）
            Page<OpenApiLog> result = logMapper.selectPage(new Page<OpenApiLog>(pageNum, pageSize),
                    rowWrapper(condition, visibleClientIds, condition.getStartTime(), condition.getEndTime()));
            return PageResult.of(toVOs(result.getRecords()), result.getTotal(), pageNum, pageSize);
        }

        // 3) 分片开着 ⇒ 时间范围必须有界：openapi_log 没有租户维度可收窄实例（route-by=NONE），
        //    不设限就等于"从有记录以来一直扫到当月"，那是一条会在生产挂死页面的查询。
        Window window = resolveWindow(condition);
        final LocalDateTime from = window.from;
        final LocalDateTime to = window.to;
        OpenApiLogShardReader.ShardOutcome outcome = new OpenApiLogShardReader(fanout, logMapper)
                .page(() -> rowWrapper(condition, visibleClientIds, from, to),
                        () -> countWrapper(condition, visibleClientIds, from, to),
                        from, to, pageNum, pageSize);

        // 不完整必须让前端画得出来：截断时 total 是下界、这一页也可能少行，
        // 只把已扫到的当成全部 = 用户以为"这个租户就调用了这么多次"（API-23 同族，只是这次是分片）
        return PageResult.ofSharded(toVOs(outcome.getRecords()), outcome.getTotal(), pageNum, pageSize,
                outcome.isTruncated(), Math.max(outcome.getScannedShards(), outcome.getCountedShards()),
                window.defaulted);
    }

    /** 没给区间时收窄成最近 defaultQueryMonths 个月；给了就照用（倒置区间在 validate 里已经挡了）。 */
    private Window resolveWindow(OpenApiLogQueryDTO condition) {
        LocalDateTime to = condition.getEndTime();
        LocalDateTime from = condition.getStartTime();
        if (from != null && to != null) {
            return new Window(from, to, false);
        }
        LocalDateTime end = to != null ? to : LocalDateTime.now();
        LocalDateTime start = from != null ? from : end.minusMonths(Math.max(1, defaultQueryMonths));
        return new Window(start, end, true);
    }

    private static List<OpenApiLogVO> toVOs(List<OpenApiLog> rows) {
        List<OpenApiLogVO> records = new ArrayList<OpenApiLogVO>();
        for (OpenApiLog row : rows) {
            records.add(toVO(row));
        }
        return records;
    }

    /** 生效窗口 + 是否由服务端补的。{@code defaulted} 必须一路带到响应，不能只在这层知道。 */
    private static final class Window {
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final boolean defaulted;

        private Window(LocalDateTime from, LocalDateTime to, boolean defaulted) {
            this.from = from;
            this.to = to;
            this.defaulted = defaulted;
        }
    }

    /**
     * 日志侧的行查询：条件 + 列白名单 + 全局排序。<b>每个分片新建一个</b>（扇出时由
     * {@code OpenApiLogShardReader} 反复调用）：MP 的分页插件会往 wrapper 上挂中间状态，
     * 同一个实例喂多个分片会互相污染。时间边界由参数传入，不读 {@code condition} 的原始字段 ——
     * 扇出要的是 {@link #resolveWindow} 补出来的生效窗口，单表要的是调用方原样给的边界。
     */
    private LambdaQueryWrapper<OpenApiLog> rowWrapper(OpenApiLogQueryDTO condition, List<Long> visibleClientIds,
            LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<OpenApiLog> wrapper = new LambdaQueryWrapper<OpenApiLog>();
        // 列白名单：request_params 不在其中（代理把 params 原样转成 SQL 体，那列可能带凭据内容）
        wrapper.select(OpenApiLog::getId, OpenApiLog::getClientId, OpenApiLog::getAppName,
                OpenApiLog::getApiPath, OpenApiLog::getMethod, OpenApiLog::getResponseCode,
                OpenApiLog::getExecutionTime, OpenApiLog::getIp, OpenApiLog::getCreateTime);
        applyConditions(wrapper, condition, visibleClientIds, from, to);
        // create_time 同秒的行很多（测试数据就是按分钟造的），补 id 兜底才能保证翻页不重不漏
        wrapper.orderByDesc(OpenApiLog::getCreateTime).orderByDesc(OpenApiLog::getId);
        return wrapper;
    }

    /**
     * 日志侧的计数查询：<b>只有 WHERE，不带列投影也不带排序</b>。
     *
     * <p>这不是风格问题，是分片读路径 500 的根因：MP 的 {@code selectCount} 会把 wrapper 上的
     * select 投影原样搬进 {@code COUNT(%s)}，两列以上就成了 {@code COUNT(id, client_id, ...)} ——
     * MySQL 的 COUNT 只接一个表达式，当场 1064。单表路径没踩到，是因为分页插件自己生成 count SQL 时
     * 会先把投影剥掉；扇出的计数是我们自己发的第二条 SQL，没人替我们剥。
     * 排序同理：{@code COUNT(...) ... ORDER BY} 在 only_full_group_by 下也是死路。
     */
    private LambdaQueryWrapper<OpenApiLog> countWrapper(OpenApiLogQueryDTO condition, List<Long> visibleClientIds,
            LocalDateTime from, LocalDateTime to) {
        return applyConditions(new LambdaQueryWrapper<OpenApiLog>(), condition, visibleClientIds, from, to);
    }

    private LambdaQueryWrapper<OpenApiLog> applyConditions(LambdaQueryWrapper<OpenApiLog> wrapper,
            OpenApiLogQueryDTO condition, List<Long> visibleClientIds, LocalDateTime from, LocalDateTime to) {
        if (visibleClientIds != null) {
            wrapper.in(OpenApiLog::getClientId, visibleClientIds);
        }
        if (from != null) {
            wrapper.ge(OpenApiLog::getCreateTime, from);
        }
        if (to != null) {
            wrapper.le(OpenApiLog::getCreateTime, to);
        }
        if (condition.getResponseCode() != null) {
            wrapper.eq(OpenApiLog::getResponseCode, condition.getResponseCode());
        }
        applyResultFilter(wrapper, condition.getResult());
        if (StringUtils.hasText(condition.getApiPath())) {
            wrapper.like(OpenApiLog::getApiPath, condition.getApiPath().trim());
        }
        if (StringUtils.hasText(condition.getServiceCode())) {
            // 没有 service_code 列：它就是 api_path 的 /api/<代码>/ 段，按该列做前缀匹配
            wrapper.likeRight(OpenApiLog::getApiPath, "/api/" + condition.getServiceCode().trim() + "/");
        }
        return wrapper;
    }

    /**
     * 把调用方条件（clientId / appKey）与当前租户交集，解析成日志的 client_id 集合。
     *
     * @return {@code null} = 不圈定（无会话的内部/免登调用且没给调用方条件，此时不给日志拼租户条件，
     *         与拦截器「无会话不注入」的同口径）；空集合 = 当前租户一个客户端都没有，调用方直接回空表；
     *         非空 = 用来 IN 的 id。带调用方条件却解析出空 ⇒ 抛 404，不降级成空表。
     */
    private List<Long> resolveVisibleClientIds(OpenApiLogQueryDTO condition) {
        boolean filtered = condition.getClientId() != null || StringUtils.hasText(condition.getAppKey());
        if (!filtered && !hasTenantSession()) {
            // 无会话（内部调用/免登）时不拿全库客户端 id 去拼一个可能几千元素的 IN
            return null;
        }

        LambdaQueryWrapper<OpenApiClient> clientWrapper = new LambdaQueryWrapper<OpenApiClient>();
        clientWrapper.select(OpenApiClient::getId, OpenApiClient::getAppKey);
        if (condition.getClientId() != null) {
            clientWrapper.eq(OpenApiClient::getId, condition.getClientId());
        }
        if (StringUtils.hasText(condition.getAppKey())) {
            clientWrapper.eq(OpenApiClient::getAppKey, condition.getAppKey().trim());
        }
        // openapi_client 有 tenant_id 且不在拦截器黑名单里 ⇒ 有会话时这里已经被租户收口
        List<OpenApiClient> clients = clientMapper.selectList(clientWrapper);

        List<Long> ids = new ArrayList<Long>();
        for (OpenApiClient client : clients) {
            if (client.getId() != null) {
                ids.add(client.getId());
            }
        }
        if (ids.isEmpty() && filtered) {
            // 打错的条件不能显示成「这个 key 没调用过」：那是把配置错误伪装成业务事实
            throw new BizException(ErrorCode.NOT_FOUND, describeUnknownClientFilter(condition));
        }
        return ids;
    }

    private boolean hasTenantSession() {
        return StringUtils.hasText(SecurityContextHolder.getTenantId());
    }

    private static String describeUnknownClientFilter(OpenApiLogQueryDTO condition) {
        StringBuilder sb = new StringBuilder("未找到对应的 API 客户端（不存在或不属于当前租户）：");
        if (condition.getClientId() != null) {
            sb.append("clientId=").append(condition.getClientId());
        }
        if (StringUtils.hasText(condition.getAppKey())) {
            if (condition.getClientId() != null) {
                sb.append(" / ");
            }
            sb.append("appKey=").append(condition.getAppKey().trim());
        }
        sb.append("。日志按 openapi_client.id 记调用方，开放应用（openapi_app）的调用不进这张表。");
        return sb.toString();
    }

    private void validate(OpenApiLogQueryDTO condition) {
        LocalDateTime start = condition.getStartTime();
        LocalDateTime end = condition.getEndTime();
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "startTime 不能晚于 endTime：" + start + " > " + end + "（倒置区间只会给出空表，那看着像「没有调用」）");
        }
        if (StringUtils.hasText(condition.getResult())) {
            String result = condition.getResult().trim().toUpperCase();
            if (!ALLOWED_RESULTS.contains(result)) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "result 只支持 " + ALLOWED_RESULTS + "，收到的是 [" + condition.getResult() + "]");
            }
        }
        if (StringUtils.hasText(condition.getServiceCode())
                && !SERVICE_CODE_PATTERN.matcher(condition.getServiceCode().trim()).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "serviceCode 只能是服务代码本身（如 model / datasource），收到的是 ["
                            + condition.getServiceCode() + "]；它会被拼成 /api/<服务代码>/ 去匹配 api_path");
        }
    }

    /**
     * 状态码分类过滤。分类判据按 response_code 列的区间，表里没有成功标志列可依赖，
     * 也没有 message 列 —— 所以「为什么失败」不在这一面回答。
     */
    private void applyResultFilter(LambdaQueryWrapper<OpenApiLog> wrapper, String rawResult) {
        if (!StringUtils.hasText(rawResult)) {
            return;
        }
        String result = rawResult.trim().toUpperCase();
        if (RESULT_SUCCESS.equals(result)) {
            wrapper.between(OpenApiLog::getResponseCode, 200, 299);
        } else if (RESULT_CLIENT_ERROR.equals(result)) {
            wrapper.between(OpenApiLog::getResponseCode, 400, 499);
        } else if (RESULT_SERVER_ERROR.equals(result)) {
            wrapper.between(OpenApiLog::getResponseCode, 500, 599);
        } else if (RESULT_FAIL.equals(result)) {
            wrapper.and(nested -> nested.isNull(OpenApiLog::getResponseCode)
                    .or().lt(OpenApiLog::getResponseCode, 200)
                    .or().ge(OpenApiLog::getResponseCode, 300));
        } else if (RESULT_OTHER.equals(result)) {
            // 既不是 2xx 也不是 4xx/5xx（3xx、1xx、以及历史上写进过库的异常值）
            wrapper.and(nested -> nested.isNull(OpenApiLog::getResponseCode)
                    .or().notBetween(OpenApiLog::getResponseCode, 200, 599));
        }
    }

    /**
     * 逐字段显式赋值，不用 {@code BeanUtils.copyProperties}：那样一旦实体将来多出敏感字段
     * （requestParams 就在实体上），映射会「自动」把它带上，而这正是不能出面的东西。
     */
    private static OpenApiLogVO toVO(OpenApiLog row) {
        return OpenApiLogVO.builder()
                .id(row.getId())
                .clientId(row.getClientId())
                .appName(row.getAppName())
                .apiPath(row.getApiPath())
                .method(row.getMethod())
                .responseCode(row.getResponseCode())
                .executionTime(row.getExecutionTime())
                .ip(row.getIp())
                .createTime(row.getCreateTime())
                .build();
    }
}
