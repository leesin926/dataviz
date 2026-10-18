package com.dataviz.schedule.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.schedule.dto.ScheduleJobCreateDTO;
import com.dataviz.schedule.dto.ScheduleJobUpdateDTO;
import com.dataviz.schedule.service.ScheduleJobService;
import com.dataviz.schedule.vo.JobHandlerVO;
import com.dataviz.schedule.vo.ScheduleJobLogVO;
import com.dataviz.schedule.vo.ScheduleJobVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 调度任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule/job")
@RequiredArgsConstructor
@RequiresPermission("schedule:read")
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;

    /**
     * 创建任务
     */
    @PostMapping
    @RequiresPermission("schedule:write")
    public R<Long> create(@RequestBody ScheduleJobCreateDTO createDTO) {
        return R.ok(scheduleJobService.createJob(createDTO));
    }

    /**
     * 更新任务
     */
    @PutMapping
    @RequiresPermission("schedule:write")
    public R<Void> update(@RequestBody ScheduleJobUpdateDTO updateDTO) {
        scheduleJobService.updateJob(updateDTO);
        return R.ok();
    }

    /**
     * 可被调度的处理器清单
     *
     * <p>这条端点是"执行内容"下拉的唯一来源。原先 {@code jobClass} 由前端自由填写、
     * 后端拿它 {@code Class.forName} 反射执行，等于让表单决定实例化哪个类；
     * 现在候选项由服务端注册表给出，填不进去的值在创建时就被拒。</p>
     */
    @GetMapping("/handlers")
    public R<List<JobHandlerVO>> handlers() {
        return R.ok(scheduleJobService.listHandlers());
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public R<ScheduleJobVO> getById(@PathVariable Long id) {
        return R.ok(scheduleJobService.getJobById(id));
    }

    /**
     * 分页查询任务列表
     */
    @GetMapping("/list")
    public R<PageResult<ScheduleJobVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) String status) {
        return R.ok(scheduleJobService.listJobs(pageNum, pageSize, jobGroup, status));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("schedule:write")
    public R<Void> delete(@PathVariable Long id) {
        scheduleJobService.deleteJob(id);
        return R.ok();
    }

    /**
     * 暂停任务
     */
    @PostMapping("/{id}/pause")
    @RequiresPermission("schedule:write")
    public R<Void> pause(@PathVariable Long id) {
        scheduleJobService.pauseJob(id);
        return R.ok();
    }

    /**
     * 恢复任务
     */
    @PostMapping("/{id}/resume")
    @RequiresPermission("schedule:write")
    public R<Void> resume(@PathVariable Long id) {
        scheduleJobService.resumeJob(id);
        return R.ok();
    }

    /**
     * 立即执行一次任务
     */
    @PostMapping("/{id}/runOnce")
    @RequiresPermission("schedule:write")
    public R<Void> runOnce(@PathVariable Long id) {
        scheduleJobService.runOnce(id);
        return R.ok();
    }

    /**
     * 查询任务执行日志
     */
    @GetMapping("/{id}/logs")
    public R<PageResult<ScheduleJobLogVO>> getLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(scheduleJobService.getLogs(id, pageNum, pageSize));
    }
}
