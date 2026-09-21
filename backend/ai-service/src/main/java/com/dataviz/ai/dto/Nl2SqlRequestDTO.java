package com.dataviz.ai.dto;

import lombok.Data;

@Data
public class Nl2SqlRequestDTO {

    private Long datasetId;

    private String question;

    private String schemaDescription;
}
