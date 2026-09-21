package com.dataviz.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_insight")
public class AiInsight {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasourceId;

    private Long datasetId;

    /**
     * TREND / OUTLIER / CORRELATION / DISTRIBUTION
     */
    private String insightType;

    private String content;

    /**
     * JSON config for the insight generation
     */
    private String config;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
