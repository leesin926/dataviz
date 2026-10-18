package com.dataviz.schedule.job;

/**
 * 唯一允许被 cron 触发的执行体形态。
 *
 * <p>为什么要有这一层：原先 {@code JobExecutionServiceImpl} 拿 {@code jobClass} 直接
 * {@code Class.forName(...).newInstance()} 再反射调 {@code execute(String)} —— 那是一个
 * 持有 {@code schedule:write} 就能实例化 classpath 上任意无参构造类的口子，而且
 * {@code jobClass} 是自由文本输入。改成"只能命中注册过的 Spring Bean"之后，
 * 可调用的东西被闭集在代码里写死的那几个处理器上。</p>
 */
public interface ScheduledJob {

    /** 注册键，存进 {@code schedule_job.job_class}；用小驼峰、全局唯一 */
    String key();

    /** 给人看的名字，供前端下拉与日志文案 */
    String label();

    /** 这个处理器是干什么的（前端把这段原样显示，避免用户凭键名猜语义） */
    String description();

    /**
     * @param params {@code schedule_job.job_params} 的原文（约定是 JSON 字符串，解析由实现自己决定）
     * @throws Exception 任何异常都会被记成一条 FAILED 日志；**不许**吞掉异常后正常返回，
     *                   因为"正常返回"会被写成 SUCCESS
     */
    void execute(String params) throws Exception;
}
