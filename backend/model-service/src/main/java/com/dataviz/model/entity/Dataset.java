package com.dataviz.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dataset")
public class Dataset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Long datasourceId;
    private String tableName;
    /** Type: 1=physical table, 2=SQL query, 3=join model */
    private Integer type;
    /** SQL content for type=2 */
    private String sqlContent;
    /** Join configuration JSON for type=3 */
    private String joinConfig;
    /** Column definitions as JSON array */
    private String columnsJson;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableField("create_by")
    private String createBy;
    @TableField("update_by")
    private String updateBy;
    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
