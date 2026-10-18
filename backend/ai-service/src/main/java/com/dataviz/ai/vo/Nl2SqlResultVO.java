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

    /**
     * 这份结果是不是真从大模型来的。
     * <p>false 时 sql/explanation 都是占位内容，界面必须显式标出来 —— 把占位当 AI 结论展示，
     * 用户会照着一条跑不通的语句去查数。</p>
     */
    private Boolean llmUsed;

    /**
     * llmUsed=false 的原因（未配置 / 调用失败 / 生成结果未通过只读校验）
     */
    private String llmNote;
}
