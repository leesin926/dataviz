package com.dataviz.analysis.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.dataviz.analysis.dto.DataPermRuleDTO.PermConditionDTO;
import lombok.Data;

/**
 * 行列权限规则的回显形状。
 */
@Data
public class DataPermRuleVO {

    private Long id;
    private String name;
    private Long datasetId;
    private String scopeType;
    private String scopeValue;
    private List<PermConditionDTO> conditions;
    private List<String> restrictedColumns;
    private Integer enabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
