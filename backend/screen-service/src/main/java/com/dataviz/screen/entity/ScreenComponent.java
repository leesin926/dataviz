package com.dataviz.screen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("screen_component")
public class ScreenComponent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long screenId;

    private String componentType;

    private String title;

    private String configJson;

    private String dataConfigJson;

    private String positionJson;

    private Integer refreshInterval;
}
