package com.dataviz.schedule.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.schedule.entity.ScheduleJobLog;
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 清理过期的任务执行日志 —— 这是本服务唯一"数据在自己手里"的可调度动作，
 * 所以它是注册表里的第一个真处理器，而不是一个占位。
 *
 * <p>{@code job_params} 约定：{@code {"keepDays":90}}；留空按 90 天。
 * 删除条数直接进执行日志的 message，运维在页面上能看到"这一次清掉了多少条"，
 * 而不是只看一句"任务执行成功"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobLogCleanupJob implements ScheduledJob {

    /** 默认保留天数：与演示数据里那条"审计日志归档 keepDays=90"对齐，少一个需要解释的数字 */
    static final int DEFAULT_KEEP_DAYS = 90;

    private final ScheduleJobLogMapper scheduleJobLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "jobLogCleanup";
    }

    @Override
    public String label() {
        return "清理任务执行日志";
    }

    @Override
    public String description() {
        return "删除早于保留期的 schedule_job_log 记录。参数示例 {\"keepDays\":90}，留空按 90 天。";
    }

    @Override
    public void execute(String params) throws Exception {
        int keepDays = readKeepDays(params);
        LocalDateTime threshold = LocalDateTime.now().minusDays(keepDays);
        int deleted = scheduleJobLogMapper.delete(new LambdaQueryWrapper<ScheduleJobLog>()
                .lt(ScheduleJobLog::getCreateTime, threshold));
        log.info("任务执行日志清理完成: keepDays={}, threshold={}, deleted={}", keepDays, threshold, deleted);
    }

    /**
     * 参数写错必须炸在这里：如果"格式不对"退化成"用默认值"，一个 {@code keepDays: 9O} 的笔误
     * 会变成"每天删掉 90 天前的日志"而没有任何一次报错，而这是删除动作。
     */
    private int readKeepDays(String params) throws Exception {
        if (params == null || params.trim().isEmpty()) {
            return DEFAULT_KEEP_DAYS;
        }
        JsonNode root = objectMapper.readTree(params);
        JsonNode node = root.get("keepDays");
        if (node == null || node.isNull()) {
            return DEFAULT_KEEP_DAYS;
        }
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException("keepDays 必须是整数，实际收到: " + node.asText());
        }
        int keepDays = node.asInt();
        if (keepDays < 1) {
            throw new IllegalArgumentException("keepDays 至少为 1 天，实际收到: " + keepDays);
        }
        return keepDays;
    }
}
