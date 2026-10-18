package com.dataviz.schedule.job;

import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 唯一那个真处理器的参数口径：{@code keepDays} 写错必须炸，不能退化成默认值 ——
 * 因为这个动作是<b>删除</b>，"笔误被宽容处理"意味着按一个没人想要的期限删数据。
 */
@ExtendWith(MockitoExtension.class)
class JobLogCleanupJobTest {

    @Mock
    private ScheduleJobLogMapper scheduleJobLogMapper;

    private JobLogCleanupJob newJob() {
        return new JobLogCleanupJob(scheduleJobLogMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("留空参数按 90 天，并且真的下压了一次删除")
    void should_useDefaultDays_when_paramsBlank() throws Exception {
        when(scheduleJobLogMapper.delete(any())).thenReturn(7);

        newJob().execute(null);

        verify(scheduleJobLogMapper).delete(any());
    }

    @Test
    @DisplayName("显式 keepDays 生效")
    void should_honourKeepDays_when_given() throws Exception {
        when(scheduleJobLogMapper.delete(any())).thenReturn(0);

        newJob().execute("{\"keepDays\":7}");

        verify(scheduleJobLogMapper).delete(any());
    }

    @Test
    @DisplayName("keepDays 不是整数时抛错，不静默用默认值")
    void should_throw_when_keepDaysNotInteger() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Exception {
                newJob().execute("{\"keepDays\":\"9O\"}");
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("必须是整数");
    }

    @Test
    @DisplayName("keepDays 小于 1 时抛错：0 天等于清空全部执行日志")
    void should_throw_when_keepDaysNotPositive() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Exception {
                newJob().execute("{\"keepDays\":0}");
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("至少为 1");
    }

    @Test
    @DisplayName("参数不是合法 JSON 时抛错，而且一条删除都不发")
    void should_notDelete_when_paramsNotJson() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Exception {
                newJob().execute("{oops");
            }
        }).isInstanceOf(Exception.class);

        verify(scheduleJobLogMapper, never()).delete(any());
    }

    @Test
    @DisplayName("键名与标签是前端下拉要显示的内容")
    void should_describeItself() {
        JobLogCleanupJob job = newJob();
        assertThat(job.key()).isEqualTo("jobLogCleanup");
        assertThat(job.label()).isNotEmpty();
        assertThat(job.description()).contains("keepDays");
    }
}
