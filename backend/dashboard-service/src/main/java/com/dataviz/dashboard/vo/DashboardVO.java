package com.dataviz.dashboard.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {

    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    private String configJson;

    private String layoutJson;

    private String coverUrl;

    private Integer status;

    private Long viewCount;

    private Long likeCount;

    private Boolean isTemplate;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
