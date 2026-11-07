package com.dataviz.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("openapi_log")
public class OpenApiLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clientId;

    /**
     * 调用方所属租户。密钥没验过的拒绝记录（401/403）留空 —— 没验过身份就等于没归属，
     * 写进去等于让外部自己声称属于某个租户。
     * <p>
     * 刻意不加 {@code @TableField(fill = INSERT)}：自动填充那条路把会话租户当 {@code String} 塞，
     * 而这个字段与库里的列都是 bigint；而且这张表按月份轮转实例（route-by=NONE），
     * 这一列只是审计维度，不是路由键。
     */
    private Long tenantId;

    private String appName;

    private String apiPath;

    /**
     * HTTP method: GET, POST, PUT, DELETE
     */
    private String method;

    /**
     * JSON string of request parameters
     */
    private String requestParams;

    private Integer responseCode;

    /**
     * Execution time in milliseconds
     */
    private Long executionTime;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
