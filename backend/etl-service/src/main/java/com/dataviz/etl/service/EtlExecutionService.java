package com.dataviz.etl.service;

/**
 * ETL执行服务接口 - 负责实际的数据抽取、转换、加载
 */
public interface EtlExecutionService {

    /**
     * 异步执行ETL任务
     */
    void executeTask(Long taskId);
}
