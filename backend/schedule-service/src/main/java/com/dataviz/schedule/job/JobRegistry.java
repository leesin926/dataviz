package com.dataviz.schedule.job;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link ScheduledJob} 的注册表：键 → Bean，由 Spring 收集所有实现类。
 *
 * <p>它是"能被调度的东西"的唯一来源：新建/编辑/恢复任务时先在这里查一遍，
 * 查不到就直接 400，而不是先把 {@code RUNNING} 写进库、再让每次触发都记一条失败日志。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRegistry {

    private final List<ScheduledJob> handlers;

    /** 保持插入顺序，前端下拉的次序就是代码里 Bean 的次序，不会每次重启换个样子 */
    private Map<String, ScheduledJob> byKey = Collections.emptyMap();

    @PostConstruct
    void index() {
        Map<String, ScheduledJob> map = new LinkedHashMap<String, ScheduledJob>();
        for (ScheduledJob handler : handlers) {
            String key = handler.key() == null ? null : handler.key().trim();
            if (key == null || key.isEmpty()) {
                throw new IllegalStateException("任务处理器键名不能为空: " + handler.getClass().getName());
            }
            ScheduledJob dup = map.put(key, handler);
            if (dup != null) {
                throw new IllegalStateException("任务处理器键名重复: " + key
                        + " (" + dup.getClass().getName() + " / " + handler.getClass().getName() + ")");
            }
        }
        byKey = Collections.unmodifiableMap(map);
        log.info("任务处理器注册完成，共 {} 个: {}", byKey.size(), byKey.keySet());
    }

    public boolean contains(String key) {
        return key != null && byKey.containsKey(key.trim());
    }

    /** 只查不炸：给"列表里显式告诉用户这条任务的处理器已经不存在了"这类展示用 */
    public ScheduledJob find(String key) {
        return key == null ? null : byKey.get(key.trim());
    }

    /** 未注册就抛 400 并把可选项带上 —— 用户看得到"能填什么"，而不是一个空泛的参数错误 */
    public ScheduledJob require(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "任务处理器不能为空，可选: " + String.join(", ", byKey.keySet()));
        }
        ScheduledJob handler = byKey.get(key.trim());
        if (handler == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "未注册的任务处理器: " + key + "，可选: " + String.join(", ", byKey.keySet()));
        }
        return handler;
    }

    /** 给前端下拉用；返回的是只读副本 */
    public List<ScheduledJob> available() {
        return Collections.unmodifiableList(new ArrayList<ScheduledJob>(byKey.values()));
    }
}
