package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.dto.NotifyChannelDTO;
import com.dataviz.alert.engine.notifier.AlertNotifier;
import com.dataviz.alert.engine.notifier.ChannelField;
import com.dataviz.alert.engine.notifier.OutboundUrlGuard;
import com.dataviz.alert.engine.notifier.NotifyTargets;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.mapper.NotifyChannelMapper;
import com.dataviz.alert.service.AlertNotifyTargetService;
import com.dataviz.alert.service.NotifyChannelService;
import com.dataviz.alert.vo.ChannelSchemaVO;
import com.dataviz.alert.vo.ChannelTestVO;
import com.dataviz.alert.vo.NotifyChannelVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 通知渠道配置服务。三件事是这一层存在的理由，控制器直接操作 mapper 时全都缺位：
 * <ol>
 *   <li><b>脱敏</b>：{@code config} 里存的是群机器人 token、SMTP 口令、短信 AK/SK，
 *       原控制器把实体整条回给任何 {@code alert:read} 的账号 ⇒ 读接口必须掩码，
 *       而掩码之后又要能"打开表单点保存但不把口令冲没"，于是有了 {@link #resolveFieldValue}；</li>
 *   <li><b>保存侧校验</b>：必填项按 {@link AlertNotifier#fields()} 的声明查，URL 项过
 *       {@code OutboundUrlGuard}（发送侧那一道判据照旧保留，两道不互斥）；</li>
 *   <li><b>引用完整性与遮蔽</b>：规则按<b>类型</b>关联渠道（{@code alert_rule.notify_channels}），
 *       而派发侧每种类型只取 id 最小的那条启用记录 —— 所以"删掉最后一条 WEBHOOK"和"给 WEBHOOK 配第二条"
 *       这两种操作都会静默改变告警去向，必须由接口把事实说清楚。</li>
 * </ol>
 */
@Slf4j
@Service
public class NotifyChannelServiceImpl implements NotifyChannelService {

    /** TEXT 列上限 65535，留出头；配置页的表单不可能写到这个体量，写到了就是有人在贴二进制 */
    private static final int MAX_CONFIG_CHARS = 60000;

    private final NotifyChannelMapper notifyChannelMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, AlertNotifier> notifiers = new TreeMap<>();
    private final AlertNotifyTargetService targetService;

    public NotifyChannelServiceImpl(NotifyChannelMapper notifyChannelMapper,
                                    AlertRuleMapper alertRuleMapper,
                                    ObjectMapper objectMapper,
                                    List<AlertNotifier> notifierList,
                                    AlertNotifyTargetService targetService) {
        this.notifyChannelMapper = notifyChannelMapper;
        this.alertRuleMapper = alertRuleMapper;
        this.objectMapper = objectMapper;
        this.targetService = targetService;
        for (AlertNotifier notifier : notifierList) {
            this.notifiers.put(notifier.channel().toUpperCase(), notifier);
        }
    }

    @Override
    public List<ChannelSchemaVO> schemas() {
        List<ChannelSchemaVO> schemas = new ArrayList<>();
        for (Map.Entry<String, AlertNotifier> entry : notifiers.entrySet()) {
            schemas.add(ChannelSchemaVO.builder()
                    .type(entry.getKey())
                    .deliverable(entry.getValue().deliverable())
                    .targetKind(entry.getValue().targetKind())
                    .fields(entry.getValue().fields())
                    .build());
        }
        return schemas;
    }

    @Override
    public PageResult<NotifyChannelVO> page(PageQuery pageQuery, String keyword, String type) {
        int pageNum = pageQuery.getPageNum() <= 0 ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery.getPageSize() <= 0 ? 10 : pageQuery.getPageSize();
        LambdaQueryWrapper<NotifyChannel> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(NotifyChannel::getName, keyword.trim());
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(NotifyChannel::getType, type.trim().toUpperCase());
        }
        wrapper.orderByAsc(NotifyChannel::getType).orderByAsc(NotifyChannel::getId);
        Page<NotifyChannel> result = notifyChannelMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<NotifyChannelVO> rows = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        markShadowed(rows);
        return PageResult.of(rows, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public NotifyChannelVO detail(Long id) {
        return toVO(requireById(id));
    }

    @Override
    @Transactional
    public Long create(NotifyChannelDTO dto) {
        AlertNotifier notifier = requireNotifier(dto.getType());
        Map<String, Object> config = normalize(notifier, dto.getConfig(), Collections.<String, Object>emptyMap());
        NotifyChannel entity = NotifyChannel.builder()
                .name(requireName(dto.getName()))
                .type(notifier.channel().toUpperCase())
                .config(toJson(config))
                .enabled(dto.getEnabled() == null || dto.getEnabled())
                .build();
        notifyChannelMapper.insert(entity);
        log.info("通知渠道已创建: id={}, type={}, name={}", entity.getId(), entity.getType(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(NotifyChannelDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "更新通知渠道必须带 id");
        }
        NotifyChannel stored = requireById(dto.getId());
        AlertNotifier notifier = requireNotifier(dto.getType());
        Map<String, Object> config = normalize(notifier, dto.getConfig(), parseConfig(stored.getConfig()));
        stored.setName(requireName(dto.getName()));
        // 换类型是允许的：配置里新类型用不到的键原样留着（不静默删用户写过的东西），
        // 新类型的必填项则按合并后的结果查，缺的就是真缺的
        stored.setType(notifier.channel().toUpperCase());
        stored.setConfig(toJson(config));
        if (dto.getEnabled() != null) {
            stored.setEnabled(dto.getEnabled());
        }
        notifyChannelMapper.updateById(stored);
        log.info("通知渠道已更新: id={}, type={}", stored.getId(), stored.getType());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        NotifyChannel stored = requireById(id);
        // 规则是按类型找渠道的，删掉某类型的最后一条启用记录 ⇒ 相关规则的该路通知从此静默失败
        List<String> referencing = rulesUsingType(stored.getType(), true);
        if (!referencing.isEmpty() && countOtherEnabled(stored.getType(), stored.getId()) == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "仍有启用中的告警规则按 " + stored.getType() + " 渠道通知（" + joinSample(referencing)
                            + "），删掉这条后该类型没有任何可用配置；请先改规则或改用其它渠道");
        }
        notifyChannelMapper.deleteById(id);
        log.info("通知渠道已删除: id={}, type={}", id, stored.getType());
    }

    @Override
    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        NotifyChannel stored = requireById(id);
        if (!enabled) {
            List<String> referencing = rulesUsingType(stored.getType(), true);
            if (!referencing.isEmpty() && countOtherEnabled(stored.getType(), id) == 0) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "停用后 " + stored.getType() + " 渠道没有任何可用配置，而规则按类型通知（"
                                + joinSample(referencing) + "）；这些规则的该路告警会记为失败");
            }
        }
        NotifyChannel patch = new NotifyChannel();
        patch.setId(stored.getId());
        patch.setEnabled(enabled);
        notifyChannelMapper.updateById(patch);
        log.info("通知渠道启停: id={}, enabled={}", id, enabled);
    }

    /**
     * 测试发送。
     * <p>
     * <b>不写 {@code alert_notify_log}</b>：那张表的 {@code event_id} 是 NOT NULL，
     * 而这里根本没有告警事件 —— 要么伪造一个事件行污染排查数据，要么改表结构，两者都不该由"点一下测一下"
     * 触发。结果直接回给调用方，界面上就是这一条的回显。
     * <p>
     * 失败<b>不抛异常</b>：这类失败九成是配置填错或地址不通，抛出去在界面只剩一条 500，
     * 用户分不清"我配错了"和"服务坏了"；把原话放进 {@code error} 才是可操作的信息。
     */
    @Override
    public ChannelTestVO test(Long id, String recipients) {
        NotifyChannel stored = requireById(id);
        AlertNotifier notifier = notifiers.get(stored.getType() == null ? "" : stored.getType().toUpperCase());
        String recipient = null;
        long start = System.currentTimeMillis();
        if (notifier == null) {
            return ChannelTestVO.builder()
                    .success(false).channel(stored.getType()).deliverable(false)
                    .error("服务端没有 " + stored.getType() + " 渠道的实现（当前可用："
                            + String.join("、", notifiers.keySet()) + "）")
                    .elapsedMs(0L).build();
        }
        NotifyTargets targets = targetsForTest(notifier, stored, recipients);
        try {
            recipient = notifier.recipientOf(stored, targets);
        } catch (Exception ignored) {
            // 接收方只是展示用；解析不出来正好说明配置有问题，让它以 success=false 的形式暴露，而不是在这里替它圆场
        }
        try {
            notifier.send(stored, syntheticRule(stored), syntheticEvent(stored), targets);
            return ChannelTestVO.builder()
                    .success(true).channel(stored.getType()).recipient(recipient)
                    .recipientSource(targets.source().name())
                    .deliverable(notifier.deliverable())
                    .elapsedMs(System.currentTimeMillis() - start).build();
        } catch (Exception e) {
            log.warn("渠道测试发送失败: id={}, type={}", id, stored.getType(), e);
            return ChannelTestVO.builder()
                    .success(false).channel(stored.getType()).recipient(recipient)
                    .recipientSource(targets.source().name())
                    .deliverable(notifier.deliverable())
                    .error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start).build();
        }
    }

    // ---------------- 内部 ----------------

    /**
     * 测试发送的收件人：临时填的那串优先，其次走跟派发<b>同一条</b>解析路
     * （合成规则没有 id ⇒ 解析不出通知组 ⇒ 实际命中的是渠道里存量的历史收件人）。
     * 走同一条路是刻意的：两条路各写一遍"从哪拿收件人"，测试通过而真实告警发错人就又回来了。
     */
    private NotifyTargets targetsForTest(AlertNotifier notifier, NotifyChannel stored, String recipients) {
        List<String> tokens = splitRecipients(recipients);
        if (!tokens.isEmpty()) {
            String kind = notifier.targetKind();
            if (AlertNotifier.TARGET_EMAIL.equals(kind)) {
                return NotifyTargets.of(tokens, Collections.<String>emptyList(), NotifyTargets.Source.ADHOC);
            }
            if (AlertNotifier.TARGET_MOBILE.equals(kind)) {
                return NotifyTargets.of(Collections.<String>emptyList(), tokens, NotifyTargets.Source.ADHOC);
            }
            // 这条渠道根本没有"收件人"概念（Webhook、不 @ 人的机器人）：填了也不取，来源照实报 NONE
            return NotifyTargets.none();
        }
        return targetService.resolveTargets(syntheticRule(stored), stored);
    }

    private List<String> splitRecipients(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : raw.split("[,;\\s]+")) {
            if (StringUtils.hasText(part)) {
                tokens.add(part.trim());
            }
        }
        return tokens;
    }

    private AlertRule syntheticRule(NotifyChannel channel) {
        AlertRule rule = new AlertRule();
        rule.setId(null);
        rule.setName("渠道连通性测试（非真实告警）：" + channel.getName());
        rule.setCondition(null);
        rule.setThreshold(null);
        return rule;
    }

    private AlertEvent syntheticEvent(NotifyChannel channel) {
        AlertEvent event = new AlertEvent();
        event.setSeverity("INFO");
        event.setMessage("这条由管理端「测试发送」产生，用于确认 " + channel.getType() + " 渠道的配置可用。"
                + "收到它说明通道已接通；它不代表任何真实告警。");
        event.setTriggerValue(null);
        return event;
    }

    private NotifyChannel requireById(Long id) {
        NotifyChannel stored = id == null ? null : notifyChannelMapper.selectById(id);
        if (stored == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知渠道不存在: " + id);
        }
        return stored;
    }

    private AlertNotifier requireNotifier(String type) {
        String key = type == null ? "" : type.trim().toUpperCase();
        AlertNotifier notifier = notifiers.get(key);
        if (notifier == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "没有这种通知渠道类型: " + type + "（可用：" + String.join("、", notifiers.keySet()) + "）");
        }
        return notifier;
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "渠道名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 128) {
            throw new BizException(ErrorCode.BAD_REQUEST, "渠道名称超长（最多 128 字）");
        }
        return trimmed;
    }

    /**
     * 按渠道声明整理配置：口令沿用库里那份、类型归一、必填查全、URL 过 SSRF 判据、多余键保留。
     *
     * @param incoming 前端提交的配置
     * @param stored   库里已有的配置（新增时传空 map）
     */
    private Map<String, Object> normalize(AlertNotifier notifier,
                                          Map<String, Object> incoming,
                                          Map<String, Object> stored) {
        Map<String, Object> source = incoming == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(incoming);
        Map<String, Object> merged = new LinkedHashMap<>();
        // 先落声明过的键，再把用户自己写过的多余键原样带上：配置页不该有"隐形删数据"的能力
        for (ChannelField field : notifier.fields()) {
            Object value = resolveFieldValue(field, source.get(field.getKey()), stored.get(field.getKey()));
            if (value != null) {
                merged.put(field.getKey(), value);
            }
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!merged.containsKey(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        for (ChannelField field : notifier.fields()) {
            Object value = merged.get(field.getKey());
            if (field.isRequired() && isEmptyValue(value)) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        notifier.channel() + " 渠道缺少必填配置: " + field.getKey());
            }
            if (field.isUrl() && value != null && !ChannelField.MASK.equals(String.valueOf(value))) {
                // MASK 表示"沿用库里那份"，那条地址在发送侧还会被同一条判据再挡一次，这里不重复解 DNS
                OutboundUrlGuard.requireAllowed(String.valueOf(value), notifier.channel());
            }
        }
        return merged;
    }

    private Object resolveFieldValue(ChannelField field, Object incoming, Object storedValue) {
        if (field.isSecret()) {
            // 掩码/缺键 = 不改；其它值（含空串）= 用户明确改写，空串就是撤销这条口令
            if (incoming == null || ChannelField.MASK.equals(String.valueOf(incoming))) {
                return storedValue;
            }
        } else if (incoming == null) {
            return null;
        }
        return coerce(field, incoming);
    }

    private Object coerce(ChannelField field, Object raw) {
        if (raw == null) {
            return null;
        }
        if (ChannelField.KIND_NUMBER.equals(field.getKind())) {
            if (raw instanceof Number) {
                return raw;
            }
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.BAD_REQUEST, field.getKey() + " 必须是数字，当前是: " + text);
            }
        }
        if (ChannelField.KIND_SWITCH.equals(field.getKind())) {
            if (raw instanceof Boolean) {
                return raw;
            }
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                return null;
            }
            return "true".equalsIgnoreCase(text) || "1".equals(text) || "on".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
        }
        if (ChannelField.KIND_LIST.equals(field.getKind())) {
            List<String> items = new ArrayList<>();
            if (raw instanceof Iterable) {
                for (Object item : (Iterable<?>) raw) {
                    if (item != null && StringUtils.hasText(String.valueOf(item))) {
                        items.add(String.valueOf(item).trim());
                    }
                }
            } else {
                // 允许逗号/分号/换行分隔：配置页用的是标签输入，但直接贴一串手机号也该能用
                for (String part : String.valueOf(raw).split("[,;\\n]")) {
                    if (StringUtils.hasText(part)) {
                        items.add(part.trim());
                    }
                }
            }
            return items.isEmpty() ? null : items;
        }
        if (ChannelField.KIND_JSON.equals(field.getKind())) {
            if (raw instanceof Map) {
                return raw;
            }
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() { });
            } catch (Exception e) {
                throw new BizException(ErrorCode.BAD_REQUEST, field.getKey() + " 必须是 JSON 对象，当前解析失败");
            }
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (field.getOptions() != null && !field.getOptions().isEmpty() && !field.getOptions().contains(text)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    field.getKey() + " 只能取 " + String.join("、", field.getOptions()) + "，当前是: " + text);
        }
        return text;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        return String.valueOf(value).trim().isEmpty();
    }

    private Map<String, Object> parseConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            // 库里这一行本来就坏（多半是手写的 SQL 补丁）：读接口不能因此 500，
            // 否则用户连"把配置页打开改回来"这条路都没有
            log.warn("通知渠道配置不是合法 JSON，按空配置处理: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            if (json.length() > MAX_CONFIG_CHARS) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "渠道配置过大（" + json.length() + " 字符 > " + MAX_CONFIG_CHARS + "）");
            }
            return json;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "渠道配置无法序列化: " + e.getMessage());
        }
    }

    private NotifyChannelVO toVO(NotifyChannel entity) {
        Map<String, Object> config = parseConfig(entity.getConfig());
        AlertNotifier notifier = notifiers.get(entity.getType() == null ? "" : entity.getType().toUpperCase());
        Map<String, Object> masked = new LinkedHashMap<>(config);
        if (notifier != null) {
            for (ChannelField field : notifier.fields()) {
                // 口令只回掩码：库里那串不该离开服务端，界面上它只用来表示"这里已经配过值"
                if (field.isSecret() && !isEmptyValue(config.get(field.getKey()))) {
                    masked.put(field.getKey(), ChannelField.MASK);
                }
            }
        }
        return NotifyChannelVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .enabled(entity.getEnabled())
                .config(masked)
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .deliverable(notifier != null && notifier.deliverable())
                .build();
    }

    /**
     * 标出"同类型里还有更靠前的启用记录"。派发侧每种类型取<b>启用记录中 id 最小</b>的那条，
     * 所以后配的那条永远不会被用到 —— 不标出来，用户看到的就是"配了、启用了、没收到"。
     */
    private void markShadowed(List<NotifyChannelVO> rows) {
        for (NotifyChannelVO row : rows) {
            if (!Boolean.TRUE.equals(row.getEnabled())) {
                continue;
            }
            Long shadow = firstEnabledIdOfType(row.getType(), row.getId());
            if (shadow != null) {
                row.setShadowedByOtherRow(true);
                row.setShadowId(shadow);
            }
        }
    }

    private Long firstEnabledIdOfType(String type, Long excludeId) {
        LambdaQueryWrapper<NotifyChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotifyChannel::getType, type)
                .eq(NotifyChannel::getEnabled, true)
                .ne(NotifyChannel::getId, excludeId)
                .orderByAsc(NotifyChannel::getId)
                .last("LIMIT 1");
        List<NotifyChannel> found = notifyChannelMapper.selectList(wrapper);
        return found.isEmpty() ? null : found.get(0).getId();
    }

    private long countOtherEnabled(String type, Long excludeId) {
        LambdaQueryWrapper<NotifyChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotifyChannel::getType, type).eq(NotifyChannel::getEnabled, true)
                .ne(NotifyChannel::getId, excludeId);
        return notifyChannelMapper.selectCount(wrapper);
    }

    /** 引用某渠道类型的规则名（notify_channels 存的是类型名数组，不是渠道 id） */
    private List<String> rulesUsingType(String type, boolean enabledOnly) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(AlertRule::getNotifyChannels, "\"" + type + "\"");
        if (enabledOnly) {
            wrapper.eq(AlertRule::getEnabled, true);
        }
        List<String> names = new ArrayList<>();
        for (AlertRule rule : alertRuleMapper.selectList(wrapper)) {
            names.add(rule.getName());
        }
        return names;
    }

    private String joinSample(List<String> names) {
        if (names.size() <= 3) {
            return String.join("、", names);
        }
        return String.join("、", names.subList(0, 3)) + " 等 " + names.size() + " 条";
    }
}
