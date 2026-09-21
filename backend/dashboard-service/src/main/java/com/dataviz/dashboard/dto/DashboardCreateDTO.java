package com.dataviz.dashboard.dto;

import lombok.Data;

@Data
public class DashboardCreateDTO {

    private String name;

    private String description;

    private String configJson;

    private String layoutJson;

    private String coverUrl;

    private Boolean isTemplate;
}
