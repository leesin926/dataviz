package com.dataviz.analysis.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 查询历史VO
 */
@Data
public class QueryHistoryVO {

    private Long id;
    private String userId;
    private Long datasourceId;
    private String sql;
    private String status;
    private Long executionTime;
    private Integer rowCount;
    private LocalDateTime createTime;
}
