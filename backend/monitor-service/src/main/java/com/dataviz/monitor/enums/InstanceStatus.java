package com.dataviz.monitor.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 服务实例状态枚举
 */
@Getter
@AllArgsConstructor
public enum InstanceStatus {

    UP("UP", "正常运行"),
    DOWN("DOWN", "已下线"),
    STARTING("STARTING", "启动中");

    private final String code;
    private final String desc;
}
