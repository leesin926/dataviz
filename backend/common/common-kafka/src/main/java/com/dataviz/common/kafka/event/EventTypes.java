package com.dataviz.common.kafka.event;

/**
 * 事件类型常量定义
 */
public final class EventTypes {

    private EventTypes() {
        throw new IllegalStateException("Utility class");
    }

    // 通用事件类型
    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String DELETED = "DELETED";
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    // 用户事件类型
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_LOGGED_IN = "USER_LOGGED_IN";
    public static final String USER_LOGGED_OUT = "USER_LOGGED_OUT";
    public static final String USER_PASSWORD_CHANGED = "USER_PASSWORD_CHANGED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";

    // 数据源事件类型
    public static final String DATASOURCE_CREATED = "DATASOURCE_CREATED";
    public static final String DATASOURCE_UPDATED = "DATASOURCE_UPDATED";
    public static final String DATASOURCE_DELETED = "DATASOURCE_DELETED";
    public static final String DATASOURCE_TESTED = "DATASOURCE_TESTED";
    public static final String DATASOURCE_SYNC_STARTED = "DATASOURCE_SYNC_STARTED";
    public static final String DATASOURCE_SYNC_COMPLETED = "DATASOURCE_SYNC_COMPLETED";
    public static final String DATASOURCE_SYNC_FAILED = "DATASOURCE_SYNC_FAILED";

    // ETL事件类型
    public static final String ETL_TASK_CREATED = "ETL_TASK_CREATED";
    public static final String ETL_TASK_STARTED = "ETL_TASK_STARTED";
    public static final String ETL_TASK_COMPLETED = "ETL_TASK_COMPLETED";
    public static final String ETL_TASK_FAILED = "ETL_TASK_FAILED";
    public static final String ETL_TASK_CANCELLED = "ETL_TASK_CANCELLED";

    // 仪表盘事件类型
    public static final String DASHBOARD_CREATED = "DASHBOARD_CREATED";
    public static final String DASHBOARD_UPDATED = "DASHBOARD_UPDATED";
    public static final String DASHBOARD_PUBLISHED = "DASHBOARD_PUBLISHED";
    public static final String DASHBOARD_SHARED = "DASHBOARD_SHARED";
    public static final String DASHBOARD_DELETED = "DASHBOARD_DELETED";

    // 大屏事件类型
    public static final String SCREEN_CREATED = "SCREEN_CREATED";
    public static final String SCREEN_UPDATED = "SCREEN_UPDATED";
    public static final String SCREEN_PUBLISHED = "SCREEN_PUBLISHED";
    public static final String SCREEN_DELETED = "SCREEN_DELETED";

    // 协作事件类型
    public static final String COLLAB_SESSION_STARTED = "COLLAB_SESSION_STARTED";
    public static final String COLLAB_SESSION_ENDED = "COLLAB_SESSION_ENDED";
    public static final String COLLAB_USER_JOINED = "COLLAB_USER_JOINED";
    public static final String COLLAB_USER_LEFT = "COLLAB_USER_LEFT";
    public static final String COLLAB_CONTENT_CHANGED = "COLLAB_CONTENT_CHANGED";

    // 告警事件类型
    public static final String ALERT_TRIGGERED = "ALERT_TRIGGERED";
    public static final String ALERT_ACKNOWLEDGED = "ALERT_ACKNOWLEDGED";
    public static final String ALERT_RESOLVED = "ALERT_RESOLVED";
    public static final String ALERT_NOTIFICATION_SENT = "ALERT_NOTIFICATION_SENT";

    // 文件事件类型
    public static final String FILE_UPLOADED = "FILE_UPLOADED";
    public static final String FILE_DELETED = "FILE_DELETED";
    public static final String FILE_DOWNLOADED = "FILE_DOWNLOADED";

    // 调度事件类型
    public static final String SCHEDULE_CREATED = "SCHEDULE_CREATED";
    public static final String SCHEDULE_TRIGGERED = "SCHEDULE_TRIGGERED";
    public static final String SCHEDULE_COMPLETED = "SCHEDULE_COMPLETED";
    public static final String SCHEDULE_FAILED = "SCHEDULE_FAILED";
}
