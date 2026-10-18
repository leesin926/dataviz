package com.dataviz.analysis.vo;

import java.util.List;

import lombok.Data;

/**
 * "我这个身份在这个数据集上会被怎么限制"的回显。
 * <p>
 * 规则配完只靠猜是哪几条在生效，是数据权限最容易被拖成"没人敢动"的那类配置；
 * 所以命中过程要能单独问一次，而不是只能从结果行数里反推。
 */
@Data
public class DataPermExplainVO {

    /** true = 命中不到任何规则，查询会返回空集（fail-closed） */
    private boolean denyAll;

    /** 渲染后的行谓词；空串表示行上不加限制 */
    private String rowPredicate;

    private List<String> restrictedColumns;

    /** 命中的规则名，按命中顺序 */
    private List<String> appliedRuleNames;

    /** 该数据集上启用中的规则总条数；&gt;0 而 appliedRuleNames 为空就意味着"你不在任何规则的命中范围里" */
    private int enabledRuleCount;
}
