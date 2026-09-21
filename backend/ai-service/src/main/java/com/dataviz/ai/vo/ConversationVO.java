package com.dataviz.ai.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {

    private Long id;

    private Long userId;

    private String title;

    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
