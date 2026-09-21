package com.dataviz.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("few_shot_example")
public class FewShotExample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String question;

    @TableField("`sql`")
    private String sql;

    private String category;
}
