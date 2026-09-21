package com.dataviz.collab.dto;

import lombok.Data;

@Data
public class CommentCreateDTO {

    private String targetType;

    private Long targetId;

    private String content;

    private Long parentId;

    private String mentionsJson;
}
