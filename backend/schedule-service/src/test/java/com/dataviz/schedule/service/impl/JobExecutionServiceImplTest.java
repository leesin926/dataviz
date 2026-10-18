package com.dataviz.schedule.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.schedule.entity.ScheduleJobLog;
import com.dataviz.schedule.job.JobRegistry;
import com.dataviz.schedule.job.ScheduledJob;
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 记账口径：**没真跑到就不写 SUCCESS**。
 *
 * <p>上一版有三条路会写下"任务执行成功"而其实什么都没做：{@code jobClass} 为空（整段 if 跳过）、
 * 反射到的类没有 {@code execute(String)}（只 log.warn 一句）、以及参数被吞。
 * 现在这三条都落在 FAILED 分支，并且 message 说清楚为什么。</p>
 */
@ExtendWith(MockitoExtension.class)
class JobExecutionServiceImplTest {

    @Mock
    private ScheduleJobLogMapper scheduleJobLogMapper;

    @Mock
    private JobRegistry jobRegistry;

    /** 记录自己被调过一次，用来证明"成功"是真的跑过了处理器 */
    private static final class RecordingJob implements ScheduledJob {
        private boolean executed;

        @Override
        public String key() {
            return "rec";
        }

        @Override
        public String label() {
            return "记录用处理器";
        }

        @Override
        public String description() {
            return "只用来证明被调用";
        }

        @Override
        public void execute(String params) {
            executed = true;
        }
    }

    private ScheduleJobLog capturedLog() {
        ArgumentCaptor<ScheduleJobLog> captor = ArgumentCaptor.forClass(ScheduleJobLog.class);
        verify(scheduleJobLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("处理器存在时真执行，日志写成功并带上执行了什么")
    void should_writeSuccess_when_handlerRan() {
        RecordingJob job = new RecordingJob();
        when(jobRegistry.require("rec")).thenReturn(job);

        new JobExecutionServiceImpl(scheduleJobLogMapper, jobRegistry)
                .executeJob(1L, "清理日志", "rec", "{}");

        assertThat(job.executed).isTrue();
        ScheduleJobLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getMessage()).contains("已执行 rec");
        assertThat(log.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("处理器未注册 ⇒ FAILED，而且不能落到执行器上")
    void should_writeFailed_when_handlerMissing() {
        when(jobRegistry.require("Ghost")).thenThrow(
                new BizException(ErrorCode.BAD_REQUEST, "未注册的任务处理器: Ghost，可选: rec"));

        new JobExecutionServiceImpl(scheduleJobLogMapper, jobRegistry)
                .executeJob(2L, "老任务", "Ghost", null);

        ScheduleJobLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getMessage()).contains("未注册的任务处理器");
    }

    @Test
    @DisplayName("jobClass 为空也记失败：这是「什么都不做却报成功」那一条路的回归")
    void should_writeFailed_when_jobClassBlank() {
        when(jobRegistry.require(null)).thenThrow(
                new BizException(ErrorCode.BAD_REQUEST, "任务处理器不能为空，可选: rec"));

        new JobExecutionServiceImpl(scheduleJobLogMapper, jobRegistry)
                .executeJob(3L, "空任务", null, null);

        assertThat(capturedLog().getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("处理器抛异常时消息原样进日志，异常没有消息时退回类名")
    void should_describeFailure_when_handlerThrows() {
        ScheduledJob boom = new ScheduledJob() {
            @Override
            public String key() {
                return "boom";
            }

            @Override
            public String label() {
                return "会炸的";
            }

            @Override
            public String description() {
                return "测试用";
            }

            @Override
            public void execute(String params) {
                throw new IllegalStateException("目标表写入超时");
            }
        };
        when(jobRegistry.require("boom")).thenReturn(boom);

        new JobExecutionServiceImpl(scheduleJobLogMapper, jobRegistry)
                .executeJob(4L, "会炸的任务", "boom", null);

        assertThat(capturedLog().getMessage()).contains("目标表写入超时");
    }

    @Test
    @DisplayName("异常没有 message 时日志里不留 null，而是留异常类名")
    void should_fallbackToClassName_when_exceptionHasNoMessage() {
        ScheduledJob quiet = new ScheduledJob() {
            @Override
            public String key() {
                return "quiet";
            }

            @Override
            public String label() {
                return "空消息";
            }

            @Override
            public String description() {
                return "测试用";
            }

            @Override
            public void execute(String params) {
                throw new NullPointerException();
            }
        };
        when(jobRegistry.require("quiet")).thenReturn(quiet);

        new JobExecutionServiceImpl(scheduleJobLogMapper, jobRegistry)
                .executeJob(5L, "空消息任务", "quiet", null);

        assertThat(capturedLog().getMessage()).contains("java.lang.NullPointerException");
    }

    @Test
    @DisplayName("require 之前就抛出的异常不该让日志漏记（每条任务都必须留下一行）")
    void should_alwaysWriteOneLogRow() {
        when(jobRegistry.require(any())).thenThrow(
                new BizException(ErrorCode.BAD_REQUEST, "未注册的任务处理器: x"));

        new JobExecutionServiceImpl(scheduleJobLogMapper, jobRegistry)
                .executeJob(6L, "任一任务", "x", null);

        verify(scheduleJobLogMapper).insert(any(ScheduleJobLog.class));
    }
}
