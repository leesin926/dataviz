package com.dataviz.ai.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Nl2SqlResultVO {

    /**
     * The generated SQL
     */
    private String sql;

    /**
     * Explanation of the SQL in natural language
     */
    private String explanation;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidence;
}
