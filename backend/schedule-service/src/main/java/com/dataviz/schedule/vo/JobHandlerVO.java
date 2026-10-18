package com.dataviz.schedule.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可调度处理器VO —— 前端"执行内容"下拉的唯一数据来源。
 *
 * <p>之所以要这么一条只读端点：原先 {@code jobClass} 是让调用方手填全限定类名，
 * 后端再 {@code Class.forName} 反射执行，等价于把"任意类实例化"开放给了表单。
 * 现在候选项由注册表给出，前端只能选，后端也只认注册表里的键。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobHandlerVO {

    /** 存进 {@code schedule_job.job_class} 的键 */
    private String handlerKey;

    /** 给人看的名字 */
    private String label;

    /** 这个处理器干什么 + 参数怎么写 */
    private String description;
}
