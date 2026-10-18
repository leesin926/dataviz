package com.dataviz.analysis.dto;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Data;

/**
 * 行列权限规则的创建/更新入参。
 */
@Data
public class DataPermRuleDTO {

    @NotBlank(message = "规则名不能为空")
    @Size(max = 64, message = "规则名不能超过 64 个字符")
    private String name;

    /** 作用数据集；不传 = 本租户全部数据集 */
    private Long datasetId;

    @NotBlank(message = "命中方式不能为空")
    @Pattern(regexp = "USER|ROLE", message = "命中方式只能是 USER 或 ROLE")
    private String scopeType;

    /** USER 传用户ID，ROLE 传角色码 */
    @NotBlank(message = "命中对象不能为空")
    @Size(max = 64, message = "命中对象不能超过 64 个字符")
    private String scopeValue;

    /**
     * 行条件；与列权限至少填一项（跨字段判据在服务层，不在注解里 —— 注解表达不了"二选一"，
     * 硬塞成 @NotEmpty 会让"只想遮列"的规则存不进去）。
     */
    @Valid
    private List<PermConditionDTO> conditions;

    /** 受限列名 */
    private List<String> restrictedColumns;

    /** 0=停用，默认启用 */
    private Integer enabled;

    @Size(max = 256, message = "备注不能超过 256 个字符")
    private String remark;

    /**
     * 一条行条件：字段 操作符 值。值可以是标量、数组（in/notin/between）或占位符
     * （{@code :userId} / {@code :tenantId} / {@code :username} / {@code :deptId}）。
     */
    @Data
    public static class PermConditionDTO {

        @NotBlank(message = "条件字段不能为空")
        private String field;

        @NotBlank(message = "条件操作符不能为空")
        private String operator;

        private Object value;
    }
}
