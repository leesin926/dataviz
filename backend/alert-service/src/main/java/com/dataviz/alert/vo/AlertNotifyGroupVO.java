package com.dataviz.alert.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertNotifyGroupVO {

    private Long id;

    private String name;

    private String description;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 组内联系人（含被停用的：停用是"这次不收件"，不是"这个人不在组里"，界面必须看得见） */
    private List<AlertContactVO> members;

    /** 被多少条告警规则引用 —— 删除守卫的预告 */
    private Integer ruleCount;
}
