package com.dataviz.alert.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertContactVO {

    private Long id;

    private String name;

    private String email;

    private String mobile;

    private String remark;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 这个联系人被几个通知组含着。列表页拿它当"能不能删"的预告，
     * 真正的守卫仍在服务层（这里只是让人少撞一次 400）。
     */
    private Integer groupCount;
}
