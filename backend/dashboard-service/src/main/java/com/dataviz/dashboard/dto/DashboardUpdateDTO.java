package com.dataviz.dashboard.dto;

import lombok.Data;

@Data
public class DashboardUpdateDTO {

    private Long id;

    private String name;

    private String description;

    private String configJson;

    private String layoutJson;

    private String coverUrl;
}
