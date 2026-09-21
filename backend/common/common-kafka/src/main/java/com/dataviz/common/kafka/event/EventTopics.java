package com.dataviz.common.kafka.event;

/**
 * Kafka Topic常量定义
 */
public final class EventTopics {

    private EventTopics() {
        throw new IllegalStateException("Utility class");
    }

    /** 用户事件主题 */
    public static final String USER_TOPIC = "dataviz-user-events";

    /** 数据源事件主题 */
    public static final String DATASOURCE_TOPIC = "dataviz-datasource-events";

    /** ETL事件主题 */
    public static final String ETL_TOPIC = "dataviz-etl-events";

    /** 模型事件主题 */
    public static final String MODEL_TOPIC = "dataviz-model-events";

    /** 分析事件主题 */
    public static final String ANALYSIS_TOPIC = "dataviz-analysis-events";

    /** 仪表盘事件主题 */
    public static final String DASHBOARD_TOPIC = "dataviz-dashboard-events";

    /** 大屏事件主题 */
    public static final String SCREEN_TOPIC = "dataviz-screen-events";

    /** 协作事件主题 */
    public static final String COLLAB_TOPIC = "dataviz-collab-events";

    /** 告警事件主题 */
    public static final String ALERT_TOPIC = "dataviz-alert-events";

    /** 系统事件主题 */
    public static final String SYSTEM_TOPIC = "dataviz-system-events";

    /** 文件事件主题 */
    public static final String FILE_TOPIC = "dataviz-file-events";

    /** 调度事件主题 */
    public static final String SCHEDULE_TOPIC = "dataviz-schedule-events";
}
