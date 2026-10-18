package com.dataviz.etl.service.impl;

import com.dataviz.etl.engine.DagExecutionException;
import com.dataviz.etl.engine.DagExecutor;
import com.dataviz.etl.engine.DagRunSummary;
import com.dataviz.etl.entity.EtlTask;
import com.dataviz.etl.entity.EtlTaskLog;
import com.dataviz.etl.entity.EtlTaskStatus;
import com.dataviz.etl.mapper.EtlTaskLogMapper;
import com.dataviz.etl.mapper.EtlTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一次执行的落账：任务历史里的读取数/写入数/状态就是从这里写出去的。
 * <p>
 * 判据是"历史列表里的读数能反映这一批真搬了多少"，所以这里既不许把失败记成成功，
 * 也不许把已经读到的行数抹成 0。
 */
@ExtendWith(MockitoExtension.class)
class EtlExecutionServiceImplTest {

    @Mock
    private EtlTaskMapper etlTaskMapper;

    @Mock
    private EtlTaskLogMapper etlTaskLogMapper;

    @Mock
    private DagExecutor dagExecutor;

    @InjectMocks
    private EtlExecutionServiceImpl executionService;

    private static final Long TASK_ID = 902L;

    private EtlTask taskWithDag() {
        EtlTask task = new EtlTask();
        task.setId(TASK_ID);
        task.setName("订单同步");
        task.setStatus(EtlTaskStatus.RUNNING.name());
        task.setTransformConfig("{\"nodes\":[]}");
        when(etlTaskMapper.selectById(TASK_ID)).thenReturn(task);
        return task;
    }

    private DagRunSummary summary(long read, long written, boolean cancelled, String... warnings) {
        return DagRunSummary.builder()
                .rowsRead(read)
                .rowsWritten(written)
                .executedNodes(2)
                .plannedNodes(2)
                .cancelled(cancelled)
                .warnings(java.util.Arrays.asList(warnings))
                .build();
    }

    private EtlTaskLog capturedLog() {
        ArgumentCaptor<EtlTaskLog> captor = ArgumentCaptor.forClass(EtlTaskLog.class);
        verify(etlTaskLogMapper).updateById(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("落账 - 成功时读取数与写入数分别入账，任务状态不被一次执行改写")
    void should_recordRealCounts_when_runSucceeds() {
        EtlTask task = taskWithDag();
        when(dagExecutor.execute(anyString(), anyLong())).thenReturn(summary(1200, 1180, false));

        executionService.executeTask(TASK_ID);

        EtlTaskLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getRecordsRead()).isEqualTo(1200L);
        assertThat(log.getRecordsWritten()).isEqualTo(1180L);
        assertThat(log.getErrorMessage()).isNull();
        assertThat(log.getEndTime()).isNotNull();

        ArgumentCaptor<EtlTask> taskCaptor = ArgumentCaptor.forClass(EtlTask.class);
        verify(etlTaskMapper).updateById(taskCaptor.capture());
        // status 是"要不要按 cron 跑"，成败记在 lastRunStatus；混用的话一次失败会把任务永久标成异常
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(EtlTaskStatus.RUNNING.name());
        assertThat(taskCaptor.getValue().getLastRunStatus()).isEqualTo("SUCCESS");
        assertThat(task.getStatus()).isEqualTo(EtlTaskStatus.RUNNING.name());
    }

    @Test
    @DisplayName("落账 - 跑完但结果不完整时把提示写进 errorMessage，而不是只留在服务日志里")
    void should_surfaceWarning_when_runTruncated() {
        taskWithDag();
        when(dagExecutor.execute(anyString(), anyLong())).thenReturn(
                summary(5000, 5000, false, "源端节点 n1 一次读取到上限 5000 行，源表可能还有数据没搬完"));

        executionService.executeTask(TASK_ID);

        EtlTaskLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getErrorMessage()).contains("源表可能还有数据没搬完");
    }

    @Test
    @DisplayName("落账 - 被停止的执行记 CANCELLED，并说明计划几个节点、完成几个")
    void should_recordCancelled_when_runStopped() {
        taskWithDag();
        when(dagExecutor.execute(anyString(), anyLong()))
                .thenReturn(summary(500, 0, true));

        executionService.executeTask(TASK_ID);

        EtlTaskLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo("CANCELLED");
        assertThat(log.getRecordsRead()).isEqualTo(500L);
        assertThat(log.getErrorMessage()).contains("执行被停止").contains("计划 2 个节点，完成 2 个");

        ArgumentCaptor<EtlTask> taskCaptor = ArgumentCaptor.forClass(EtlTask.class);
        verify(etlTaskMapper).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getLastRunStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("落账 - 节点失败时保留失败前已读到的行数，错误信息带出根因")
    void should_keepPartialCounts_when_runFails() {
        EtlTask task = taskWithDag();
        DagRunSummary partial = summary(3000, 0, false);
        when(dagExecutor.execute(anyString(), anyLong())).thenThrow(
                new DagExecutionException("DAG execution failed: n2 failed", partial,
                        new IllegalStateException("目标端写入失败: Data too long for column 'status'")));

        executionService.executeTask(TASK_ID);

        EtlTaskLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getRecordsRead()).isEqualTo(3000L);
        assertThat(log.getRecordsWritten()).isZero();
        // 只记外层转述的话，历史里就看不出"哪个节点为什么失败"
        assertThat(log.getErrorMessage()).contains("DAG execution failed")
                .contains("Data too long for column 'status'");

        ArgumentCaptor<EtlTask> taskCaptor = ArgumentCaptor.forClass(EtlTask.class);
        verify(etlTaskMapper).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(EtlTaskStatus.ERROR.name());
        assertThat(taskCaptor.getValue().getLastRunStatus()).isEqualTo("FAILED");
        assertThat(task.getStatus()).isEqualTo(EtlTaskStatus.ERROR.name());
    }

    @Test
    @DisplayName("落账 - 没有配置 DAG 时不去执行，也不留一个空的读数")
    void should_failWithoutExecuting_when_transformConfigBlank() {
        EtlTask task = new EtlTask();
        task.setId(TASK_ID);
        task.setStatus(EtlTaskStatus.STOPPED.name());
        task.setTransformConfig("   ");
        when(etlTaskMapper.selectById(TASK_ID)).thenReturn(task);

        executionService.executeTask(TASK_ID);

        assertThat(capturedLog().getErrorMessage()).contains("没有配置转换流程");
        verify(dagExecutor, never()).execute(anyString(), anyLong());
    }

    @Test
    @DisplayName("落账 - 任务不存在时直接返回，不留下无人认领的执行日志")
    void should_doNothing_when_taskMissing() {
        when(etlTaskMapper.selectById(TASK_ID)).thenReturn(null);

        executionService.executeTask(TASK_ID);

        verify(etlTaskLogMapper, never()).insert(any(EtlTaskLog.class));
        verify(dagExecutor, never()).execute(anyString(), anyLong());
    }

    @Test
    @DisplayName("并发 - 执行期间 isRunning 为真：连点两次不会往同一张表再插一遍")
    void should_reportRunning_when_executionInFlight() {
        taskWithDag();
        when(dagExecutor.execute(anyString(), anyLong())).thenAnswer(invocation -> {
            assertThat(executionService.isRunning(TASK_ID)).isTrue();
            return summary(1, 1, false);
        });

        assertThat(executionService.isRunning(TASK_ID)).isFalse();
        executionService.executeTask(TASK_ID);
        assertThat(executionService.isRunning(TASK_ID)).isFalse();
    }

    @Test
    @DisplayName("并发 - 执行抛异常后运行标记照样清掉，否则任务永远点不动")
    void should_clearRunningFlag_when_executionThrows() {
        taskWithDag();
        when(dagExecutor.execute(anyString(), anyLong()))
                .thenThrow(new DagExecutionException("boom", null,
                        new IllegalStateException("inner")));

        executionService.executeTask(TASK_ID);

        assertThat(executionService.isRunning(TASK_ID)).isFalse();
    }
}
