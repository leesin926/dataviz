package com.dataviz.collab.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    private Long id;

    private Long tenantId;

    private String targetType;

    private Long targetId;

    private String content;

    private Long parentId;

    private String mentionsJson;

    private Long createBy;

    private LocalDateTime createTime;
}
