package com.dataviz.ai.dto;

import lombok.Data;

@Data
public class Nl2SqlDTO {

    private Long datasourceId;

    /**
     * Natural language question to translate to SQL
     */
    private String question;

    /**
     * Optional: existing SQL to explain
     */
    private String sql;
}
