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
public class DashboardListVO {

    private Long id;

    private String name;

    private String description;

    private String coverUrl;

    private Integer status;

    private Long viewCount;

    private Long likeCount;

    private Boolean isTemplate;

    private LocalDateTime createTime;
}
