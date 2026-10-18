package com.dataviz.schedule.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.schedule.dto.ScheduleJobCreateDTO;
import com.dataviz.schedule.dto.ScheduleJobUpdateDTO;
import com.dataviz.schedule.entity.ScheduleJob;
import com.dataviz.schedule.job.JobRegistry;
import com.dataviz.schedule.job.ScheduledJob;
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.dataviz.schedule.mapper.ScheduleJobMapper;
import com.dataviz.schedule.service.JobExecutionService;
import com.dataviz.schedule.service.ScheduleService;
import com.dataviz.schedule.vo.ScheduleJobVO;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 四条写路径都必须让"库里的状态"和"调度器里有没有触发器"保持一致。
 *
 * <p>这一组用例逐条对着旧版的四个失真点：</p>
 * <ol>
 *   <li>恢复 —— 旧版调 {@code scheduleService.resumeJob(id)}，而它读的
 *       {@code pausedTasks} 没有任何写入方，所以恢复是空操作，库里却写了 RUNNING；</li>
 *   <li>编辑暂停中的任务 —— 旧版无条件 remove+register，任务被偷偷启动而列表仍写"已暂停"；</li>
 *   <li>启动装载 —— 旧版根本没有装载器，重启后所有 RUNNING 行都不再触发；</li>
 *   <li>处理器不存在 —— 旧版把 {@code jobClass} 拿去 {@code Class.forName}，
 *       于是一张自由文本表单成了反射入口，现在改为注册表闭集，填不进就 400。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ScheduleJobServiceImplTest {

    private static final String VALID_CRON = "0 0/30 * * * ?";

    @Mock
    private ScheduleJobMapper scheduleJobMapper;

    @Mock
    private ScheduleJobLogMapper scheduleJobLogMapper;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private JobExecutionService jobExecutionService;

    @Mock
    private JobRegistry jobRegistry;

    private ScheduleJobServiceImpl newService() {
        return new ScheduleJobServiceImpl(scheduleJobMapper, scheduleJobLogMapper,
                scheduleService, jobExecutionService, jobRegistry);
    }

    private static ScheduledJob registeredHandler() {
        return new ScheduledJob() {
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
                return "测试用";
            }

            @Override
            public void execute(String params) {
                // 不由本用例负责
            }
        };
    }

    private static ScheduleJob job(Long id, String status, String jobClass) {
        ScheduleJob job = new ScheduleJob();
        job.setId(id);
        job.setJobName("演示任务");
        job.setJobGroup("MAINTENANCE");
        job.setCronExpression(VALID_CRON);
        job.setJobClass(jobClass);
        job.setJobParams("{\"keepDays\":90}");
        job.setStatus(status);
        return job;
    }

    private static ScheduleJobCreateDTO createDTO(String jobClass, String cron) {
        ScheduleJobCreateDTO dto = new ScheduleJobCreateDTO();
        dto.setJobName("演示任务");
        dto.setJobGroup("MAINTENANCE");
        dto.setCronExpression(cron);
        dto.setJobClass(jobClass);
        return dto;
    }

    @Test
    @DisplayName("新建时处理器不存在 ⇒ 400，且不落库也不登记触发器")
    void should_notPersist_when_handlerUnknownOnCreate() {
        when(jobRegistry.require("Ghost")).thenThrow(
                new BizException(ErrorCode.BAD_REQUEST, "未注册的任务处理器: Ghost"));

        assertThatThrownBy(() -> newService().createJob(createDTO("Ghost", VALID_CRON)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未注册的任务处理器");

        verify(scheduleJobMapper, never()).insert(any(ScheduleJob.class));
        verify(scheduleService, never()).registerJob(any(), anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("新建成功 ⇒ 落库拿到 id 之后按该 id 登记触发器，状态 RUNNING")
    void should_armTrigger_when_created() {
        when(jobRegistry.require("jobLogCleanup")).thenReturn(registeredHandler());
        when(scheduleJobMapper.insert(any(ScheduleJob.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ScheduleJob.class).setId(7L);
            return 1;
        });

        Long id = newService().createJob(createDTO("jobLogCleanup", VALID_CRON));

        assertThat(id).isEqualTo(7L);
        ArgumentCaptor<ScheduleJob> saved = ArgumentCaptor.forClass(ScheduleJob.class);
        verify(scheduleJobMapper).insert(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(saved.getValue().getMisfirePolicy()).isEqualTo("IGNORE");
        verify(scheduleService).registerJob(eq(7L), eq(VALID_CRON), any(Runnable.class));
    }

    @Test
    @DisplayName("cron 不合法 ⇒ 400 且不落库：绝不制造一条「运行中但没人打」的记录")
    void should_rejectInvalidCron_when_create() {
        when(jobRegistry.require("jobLogCleanup")).thenReturn(registeredHandler());

        assertThatThrownBy(() -> newService().createJob(createDTO("jobLogCleanup", "every 5 min")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Cron 表达式不合法");

        verify(scheduleJobMapper, never()).insert(any(ScheduleJob.class));
    }

    @Test
    @DisplayName("编辑一个已暂停的任务 ⇒ 只撤触发器，不重新登记（编辑不该把任务启动）")
    void should_stayDisarmed_when_updatePausedJob() {
        when(scheduleJobMapper.selectById(3L)).thenReturn(job(3L, "PAUSED", "jobLogCleanup"));
        when(jobRegistry.require("jobLogCleanup")).thenReturn(registeredHandler());

        ScheduleJobUpdateDTO dto = new ScheduleJobUpdateDTO();
        dto.setId(3L);
        dto.setJobName("改过名的任务");
        dto.setJobGroup("MAINTENANCE");
        dto.setCronExpression(VALID_CRON);
        dto.setJobClass("jobLogCleanup");
        newService().updateJob(dto);

        verify(scheduleService).removeJob(3L);
        verify(scheduleService, never()).registerJob(any(), anyString(), any(Runnable.class));
        ArgumentCaptor<ScheduleJob> updated = ArgumentCaptor.forClass(ScheduleJob.class);
        verify(scheduleJobMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo("PAUSED");
        assertThat(updated.getValue().getJobName()).isEqualTo("改过名的任务");
    }

    @Test
    @DisplayName("编辑一个运行中的任务 ⇒ 按新 cron 重建触发器")
    void should_rearm_when_updateRunningJob() {
        when(scheduleJobMapper.selectById(4L)).thenReturn(job(4L, "RUNNING", "jobLogCleanup"));
        when(jobRegistry.require("jobLogCleanup")).thenReturn(registeredHandler());

        ScheduleJobUpdateDTO dto = new ScheduleJobUpdateDTO();
        dto.setId(4L);
        dto.setJobName("运行中的任务");
        dto.setCronExpression("0 0 3 * * ?");
        dto.setJobClass("jobLogCleanup");
        newService().updateJob(dto);

        verify(scheduleService, never()).removeJob(4L);
        verify(scheduleService).registerJob(eq(4L), eq("0 0 3 * * ?"), any(Runnable.class));
    }

    @Test
    @DisplayName("恢复 ⇒ 从库里重建触发器。旧版这里只改状态，恢复是个空操作")
    void should_rearmFromDb_when_resume() {
        when(scheduleJobMapper.selectById(5L)).thenReturn(job(5L, "PAUSED", "jobLogCleanup"));
        when(jobRegistry.require("jobLogCleanup")).thenReturn(registeredHandler());

        newService().resumeJob(5L);

        ArgumentCaptor<ScheduleJob> updated = ArgumentCaptor.forClass(ScheduleJob.class);
        verify(scheduleJobMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo("RUNNING");
        verify(scheduleService).registerJob(eq(5L), eq(VALID_CRON), any(Runnable.class));
    }

    @Test
    @DisplayName("重复暂停是幂等的：已经暂停就不再写库，也不再撤一次触发器")
    void should_noop_when_pauseAnAlreadyPausedJob() {
        when(scheduleJobMapper.selectById(6L)).thenReturn(job(6L, "PAUSED", "jobLogCleanup"));

        newService().pauseJob(6L);

        verify(scheduleJobMapper, never()).updateById(any(ScheduleJob.class));
        verify(scheduleService, never()).removeJob(6L);
    }

    @Test
    @DisplayName("启动装载：处理器在 ⇒ 登记触发器且不改库")
    void should_armRunningJobs_when_handlerExists() {
        when(scheduleJobMapper.selectList(any())).thenReturn(
                Collections.singletonList(job(11L, "RUNNING", "jobLogCleanup")));
        when(jobRegistry.contains("jobLogCleanup")).thenReturn(true);

        newService().armRunningJobs();

        verify(scheduleService).registerJob(eq(11L), eq(VALID_CRON), any(Runnable.class));
        verify(scheduleJobMapper, never()).updateById(any(ScheduleJob.class));
    }

    @Test
    @DisplayName("启动装载：处理器已经不存在 ⇒ 置为异常，而不是继续谎称运行中")
    void should_markError_when_handlerGoneOnLoad() {
        // 演示数据里那三条 com.dataviz.schedule.job.*Job 就是这个形态：类从来没有存在过
        when(scheduleJobMapper.selectList(any())).thenReturn(
                Collections.singletonList(job(12L, "RUNNING", "com.dataviz.schedule.job.EtlTriggerJob")));
        when(jobRegistry.contains("com.dataviz.schedule.job.EtlTriggerJob")).thenReturn(false);

        newService().armRunningJobs();

        verify(scheduleService, never()).registerJob(any(), anyString(), any(Runnable.class));
        ArgumentCaptor<ScheduleJob> updated = ArgumentCaptor.forClass(ScheduleJob.class);
        verify(scheduleJobMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("装载到空表也不炸（首次部署就是这个状态）")
    void should_doNothing_when_noRunningJobs() {
        when(scheduleJobMapper.selectList(any())).thenReturn(Collections.<ScheduleJob>emptyList());

        newService().armRunningJobs();

        verify(scheduleService, never()).registerJob(any(), anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("详情要显式告诉调用方处理器还能不能解析 —— 不能让坏任务看起来正常")
    void should_flagMissingHandler_inVO() {
        when(scheduleJobMapper.selectById(13L)).thenReturn(job(13L, "RUNNING", "Ghost"));

        ScheduleJobVO vo = newService().getJobById(13L);

        assertThat(vo.getHandlerRegistered()).isFalse();
        assertThat(vo.getHandlerLabel()).isNull();
    }

    @Test
    @DisplayName("处理器在时 VO 带上中文名，前端下拉与列表用同一份名字")
    void should_fillHandlerLabel_inVO() {
        when(scheduleJobMapper.selectById(14L)).thenReturn(job(14L, "RUNNING", "jobLogCleanup"));
        when(jobRegistry.find("jobLogCleanup")).thenReturn(registeredHandler());

        ScheduleJobVO vo = newService().getJobById(14L);

        assertThat(vo.getHandlerRegistered()).isTrue();
        assertThat(vo.getHandlerLabel()).isEqualTo("清理任务执行日志");
    }

    @Test
    @DisplayName("立即执行一次不改变已登记的 cron，也不动状态")
    void should_triggerOnceWithoutTouchingState() {
        when(scheduleJobMapper.selectById(15L)).thenReturn(job(15L, "PAUSED", "jobLogCleanup"));

        newService().runOnce(15L);

        verify(scheduleService).triggerOnce(eq(15L), any(Runnable.class));
        verify(scheduleJobMapper, never()).updateById(any(ScheduleJob.class));
    }

    @Test
    @DisplayName("任务不存在时各 id 入口统一报「任务不存在」，不返回空对象")
    void should_fail_when_jobMissing() {
        when(scheduleJobMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> newService().getJobById(99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("任务不存在: 99");
        assertThatThrownBy(() -> newService().deleteJob(99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("任务不存在");
    }
}
