package com.dataviz.analysis.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次查询要套的数据权限：行谓词 + 受限列 + 命中的规则名。
 * <p>
 * 不可变值对象，命中过程只构造一次。规则名要带出去，因为"这次查询为什么少了一半行"是排查现场，
 * 没有名字就只能靠猜（与派发链路 {@code NotifyTargets.Source} 同一个思路：让生效路径可被看见）。
 */
public final class DataPermission {

    /** 永真不限制时谓词为空串；{@code SqlBuilder} 据此决定要不要拼 WHERE */
    private static final String NO_RESTRICTION = "";

    /** 有规则但当前用户一条都不命中：不返回"全部数据"，返回"查不到数据" */
    private static final String DENY_ALL = "1 = 0";

    private final String rowPredicate;
    private final Set<String> restrictedColumns;
    private final List<String> appliedRuleNames;
    private final boolean denyAll;
    /** 本次参与命中的启用规则条数（0 = 这个数据集根本没配规则，行为与本特性之前一致） */
    private final int candidateRuleCount;

    private DataPermission(String rowPredicate, Set<String> restrictedColumns,
                           List<String> appliedRuleNames, boolean denyAll, int candidateRuleCount) {
        this.rowPredicate = rowPredicate;
        this.restrictedColumns = Collections.unmodifiableSet(restrictedColumns);
        this.appliedRuleNames = Collections.unmodifiableList(appliedRuleNames);
        this.denyAll = denyAll;
        this.candidateRuleCount = candidateRuleCount;
    }

    public static DataPermission unrestricted() {
        return new DataPermission(NO_RESTRICTION, new LinkedHashSet<>(), new ArrayList<>(), false, 0);
    }

    public static DataPermission unrestricted(int candidateRuleCount) {
        return new DataPermission(NO_RESTRICTION, new LinkedHashSet<>(), new ArrayList<>(), false, candidateRuleCount);
    }

    public static DataPermission deniedAll(Set<String> restrictedColumns, List<String> ruleNames,
                                           int candidateRuleCount) {
        return new DataPermission(DENY_ALL, restrictedColumns, ruleNames, true, candidateRuleCount);
    }

    public static DataPermission of(String rowPredicate, Set<String> restrictedColumns, List<String> ruleNames,
                                    int candidateRuleCount) {
        return new DataPermission(rowPredicate == null ? NO_RESTRICTION : rowPredicate,
                restrictedColumns, ruleNames, false, candidateRuleCount);
    }

    public String rowPredicate() {
        return rowPredicate;
    }

    public Set<String> restrictedColumns() {
        return restrictedColumns;
    }

    public List<String> appliedRuleNames() {
        return appliedRuleNames;
    }

    public boolean denyAll() {
        return denyAll;
    }

    public boolean hasColumnRestriction() {
        return !restrictedColumns.isEmpty();
    }

    public int candidateRuleCount() {
        return candidateRuleCount;
    }

    public boolean isEmpty() {
        return rowPredicate.isEmpty() && restrictedColumns.isEmpty();
    }

    /**
     * 缓存键用的权限指纹。
     * <p>行谓词已经体现在 SQL 文本里，但<b>列权限不会改变 SQL</b>（{@code SELECT *} 的裁剪发生在结果侧），
     * 只按 SQL 做键的话，刚配的列规则会在 TTL 内继续把原列吐给前端。</p>
     */
    public String fingerprint() {
        return rowPredicate + "|" + String.join(",", restrictedColumns);
    }
}
