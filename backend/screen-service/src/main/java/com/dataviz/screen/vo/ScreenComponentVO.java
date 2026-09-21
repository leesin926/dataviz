package com.dataviz.screen.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenComponentVO {

    private Long id;

    private Long screenId;

    private String componentType;

    private String title;

    private String configJson;

    private String dataConfigJson;

    private String positionJson;

    private Integer refreshInterval;
}
