package com.dataviz.analysis.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.entity.DataPermRule;
import com.dataviz.analysis.mapper.DataPermRuleMapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 行列权限规则的命中语义（API-21）。
 * <p>
 * 这里守的是两类会直接漏数据的走向：一是"配了规则反而看全表"（求不出条件时必须往严的方向倒），
 * 二是规则存成脏数据后被判成"这条规则没条件"。这里只喂一个形状确定的假渲染器，
 * 好把"规则间 OR、规则内 AND、占位符替换"这三件事单独看清楚；字段名/操作符白名单在 SqlBuilder 里另防。
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionResolverTest {

    private static final long DATASET_ID = 100L;

    @Mock
    private DataPermRuleMapper ruleMapper;

    @Mock
    private SqlBuilder sqlBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DataPermissionResolver resolver;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 环境没有 Spring/MyBatis 上下文，LambdaWrapper 解析方法引用要 MP 的列名缓存。
        // 必须显式打开下划线转换：Configuration 默认关，缓存里就会存成 datasetId 这种属性名，
        // 于是本用例断言"限定 dataset_id 列"会变成对不上线上 SQL 的自证。
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), DataPermRule.class);
    }

    @BeforeEach
    void setUp() {
        resolver = new DataPermissionResolver(ruleMapper, sqlBuilder, objectMapper);
        // 假渲染器：把条件的 field/operator/value 原样拼出来，顺带证明规则内是 AND
        lenientlyRenderConditions();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private void lenientlyRenderConditions() {
        lenient().when(sqlBuilder.permissionPredicate(anyList())).thenAnswer(invocation -> {
            List<AnalysisQueryDTO.FilterConfig> conditions = invocation.getArgument(0);
            List<String> parts = new ArrayList<>(conditions.size());
            for (AnalysisQueryDTO.FilterConfig condition : conditions) {
                parts.add(condition.getField() + " " + condition.getOperator() + " " + condition.getValue());
            }
            return String.join(" AND ", parts);
        });
    }

    private void login(Long userId, String username, Long deptId, String... roles) {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(userId)
                .username(username)
                .tenantId("1")
                .deptId(deptId)
                .roles(new HashSet<>(Arrays.asList(roles)))
                .build());
    }

    private DataPermRule roleRule(Long id, String name, String role, String conditionsJson, String columns) {
        return rule(id, name, "ROLE", role, conditionsJson, columns);
    }

    private DataPermRule rule(Long id, String name, String scopeType, String scopeValue,
                              String conditionsJson, String columns) {
        DataPermRule entity = new DataPermRule();
        entity.setId(id);
        entity.setName(name);
        entity.setDatasetId(DATASET_ID);
        entity.setScopeType(scopeType);
        entity.setScopeValue(scopeValue);
        entity.setConditionsJson(conditionsJson);
        entity.setRestrictedColumns(columns);
        entity.setEnabled(1);
        return entity;
    }

    private void givenRules(DataPermRule... rules) {
        when(ruleMapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(rules)));
    }

    @Test
    @DisplayName("超管 - 短路口径，连规则都不查")
    void should_skipRuleLookup_when_userIsSuperAdmin() {
        login(1L, "admin", 9L, "super_admin");

        DataPermission permission = resolver.resolve(1L, 1L, DATASET_ID);

        assertThat(permission.isEmpty()).isTrue();
        assertThat(permission.denyAll()).isFalse();
        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("数据集没配规则 - 不拼任何谓词，与本特性之前逐字一致")
    void should_leaveQueryUntouched_when_datasetHasNoRules() {
        login(2L, "bob", 7L, "viewer");
        givenRules();

        DataPermission permission = resolver.resolve(1L, 2L, DATASET_ID);

        assertThat(permission.isEmpty()).isTrue();
        assertThat(permission.candidateRuleCount()).isZero();
    }

    @Test
    @DisplayName("命中多条规则 - 规则之间取并集，占位符已换成当前账号的值")
    void should_unionMatchedRules_when_severalRolesHit() {
        login(2L, "bob", 7L, "sales", "ops");
        givenRules(
                roleRule(1L, "只看本部门", "sales", "[{\"field\":\"dept_id\",\"operator\":\"eq\",\"value\":\":deptId\"}]", null),
                roleRule(2L, "只看华东", "ops", "[{\"field\":\"region\",\"operator\":\"eq\",\"value\":\"east\"}]", null));

        DataPermission permission = resolver.resolve(1L, 2L, DATASET_ID);

        assertThat(permission.rowPredicate()).isEqualTo("(dept_id eq 7) OR (region eq east)");
        assertThat(permission.denyAll()).isFalse();
        assertThat(permission.appliedRuleNames()).containsExactly("只看本部门", "只看华东");
    }

    @Test
    @DisplayName("一条规则内多个条件 - 是 AND，不是并集")
    void should_andConditions_when_oneRuleCarriesTwoConditions() {
        login(2L, "bob", 7L, "sales");
        givenRules(roleRule(1L, "本部门华东", "sales",
                "[{\"field\":\"dept_id\",\"operator\":\"eq\",\"value\":\":deptId\"},"
                        + "{\"field\":\"region\",\"operator\":\"eq\",\"value\":\"east\"}]", null));

        assertThat(resolver.resolve(1L, 2L, DATASET_ID).rowPredicate())
                .isEqualTo("(dept_id eq 7 AND region eq east)");
    }

    @Test
    @DisplayName("有规则但一条都不属于这个人 - 查不到，而不是看全表")
    void should_denyAll_when_noRuleMatches() {
        login(2L, "bob", 7L, "viewer");
        givenRules(roleRule(1L, "只给分析师", "analyst", "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":0}]", null));

        DataPermission permission = resolver.resolve(1L, 2L, DATASET_ID);

        assertThat(permission.denyAll()).isTrue();
        assertThat(permission.rowPredicate()).isEqualTo("1 = 0");
        assertThat(permission.appliedRuleNames()).isEmpty();
        assertThat(permission.candidateRuleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("命中方式认不出来 - 按不匹配处理，不是按全员匹配")
    void should_denyAll_when_scopeTypeIsUnknown() {
        login(2L, "bob", 7L, "viewer");
        givenRules(rule(1L, "脏数据", "DEPT", "7", "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":0}]", null));

        assertThat(resolver.resolve(1L, 2L, DATASET_ID).denyAll()).isTrue();
    }

    @Test
    @DisplayName("按用户命中 - ID 对上才算这条规则的")
    void should_match_when_scopeTypeIsUserAndIdEquals() {
        login(42L, "bob", 7L, "viewer");
        givenRules(rule(1L, "只给42号", "USER", "42", "[{\"field\":\"owner_id\",\"operator\":\"eq\",\"value\":\":userId\"}]", null));

        assertThat(resolver.resolve(1L, 42L, DATASET_ID).rowPredicate()).isEqualTo("(owner_id eq 42)");
    }

    @Test
    @DisplayName("只配列权限的规则 - 行上不加限制，列照常遮")
    void should_maskColumnsOnly_when_ruleHasNoConditions() {
        login(2L, "bob", 7L, "viewer");
        givenRules(roleRule(1L, "遮身份证", "viewer", null, "phone,id_card"));

        DataPermission permission = resolver.resolve(1L, 2L, DATASET_ID);

        assertThat(permission.rowPredicate()).isEmpty();
        assertThat(permission.denyAll()).isFalse();
        assertThat(permission.restrictedColumns()).containsExactly("phone", "id_card");
        verify(sqlBuilder, never()).permissionPredicate(anyList());
    }

    @Test
    @DisplayName("唯一命中的规则求不出条件 - 退回没限制等于规则白配，按查不到处理")
    void should_denyAll_when_onlyMatchedRuleCannotResolvePlaceholder() {
        login(2L, "bob", null, "viewer");
        givenRules(roleRule(1L, "只看本部门", "viewer",
                "[{\"field\":\"dept_id\",\"operator\":\"eq\",\"value\":\":deptId\"}]", null));

        DataPermission permission = resolver.resolve(1L, 2L, DATASET_ID);

        assertThat(permission.denyAll()).isTrue();
        assertThat(permission.appliedRuleNames()).containsExactly("只看本部门");
        verify(sqlBuilder, never()).permissionPredicate(anyList());
    }

    @Test
    @DisplayName("求不出条件的那条规则不放开 - 并集里只剩求得出的那条")
    void should_dropUnresolvedRule_when_anotherRuleStillRenders() {
        login(2L, "bob", null, "viewer", "ops");
        givenRules(
                roleRule(1L, "只看本部门", "viewer", "[{\"field\":\"dept_id\",\"operator\":\"eq\",\"value\":\":deptId\"}]", null),
                roleRule(2L, "只看华东", "ops", "[{\"field\":\"region\",\"operator\":\"eq\",\"value\":\"east\"}]", null));

        DataPermission permission = resolver.resolve(1L, 2L, DATASET_ID);

        assertThat(permission.rowPredicate()).isEqualTo("(region eq east)");
        assertThat(permission.appliedRuleNames()).containsExactly("只看本部门", "只看华东");
    }

    @Test
    @DisplayName("条件求不出值时列权限照常生效")
    void should_keepColumnMask_when_theRowConditionOfTheSameRuleFails() {
        login(2L, "bob", null, "viewer");
        givenRules(roleRule(1L, "本部门且遮身份证", "viewer",
                "[{\"field\":\"dept_id\",\"operator\":\"eq\",\"value\":\":deptId\"}]", "id_card"));

        DataPermission permission = resolver.resolve(1L, 2L, DATASET_ID);

        assertThat(permission.denyAll()).isTrue();
        assertThat(permission.restrictedColumns()).containsExactly("id_card");
    }

    @Test
    @DisplayName("拼错的占位符 - 查询时直接报错并带上规则名，不是当没这条规则")
    void should_throw_withRuleName_when_placeholderIsUnknown() {
        login(2L, "bob", 7L, "viewer");
        givenRules(roleRule(1L, "写错的规则", "viewer", "[{\"field\":\"a\",\"operator\":\"eq\",\"value\":\":nope\"}]", null));

        assertThatThrownBy(() -> resolver.resolve(1L, 2L, DATASET_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("写错的规则")
                .hasMessageContaining("未知占位符")
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("库里的条件 JSON 读不出来 - 报错，绝不当成这条规则没有条件")
    void should_throw_when_conditionsJsonIsCorrupt() {
        login(2L, "bob", 7L, "viewer");
        givenRules(roleRule(1L, "坏 JSON", "viewer", "not-a-json", null));

        assertThatThrownBy(() -> resolver.resolve(1L, 2L, DATASET_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法解析")
                .extracting("code")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    }

    @Test
    @DisplayName("免登上下文里查询 - 没有角色也不抛，按不命中处理")
    void should_notThrow_when_securityContextIsEmpty() {
        SecurityContextHolder.clear();
        givenRules(roleRule(1L, "只给分析师", "analyst", "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":0}]", null));

        assertThat(resolver.resolve(1L, 2L, DATASET_ID).denyAll()).isTrue();
    }

    @Test
    @DisplayName("取规则的条件 - 只要启用规则，且限定本数据集或全局")
    void should_loadOnlyEnabledRulesForThisDataset_when_resolving() {
        login(2L, "bob", 7L, "viewer");
        givenRules(roleRule(1L, "只给分析师", "analyst", "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":0}]", null));

        // 停用的规则根本进不了 candidates，所以 candidateRuleCount 也就是 mapper 返回的条数
        assertThat(resolver.resolve(1L, 2L, DATASET_ID).candidateRuleCount()).isEqualTo(1);

        ArgumentCaptor<Wrapper<DataPermRule>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ruleMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("enabled =");
        assertThat(sql).contains("dataset_id IS NULL");
        assertThat(sql).contains("dataset_id =");
    }

    @Test
    @DisplayName("受限列逗号串 - 去空格、跳空项")
    void should_trimColumnList_when_splitting() {
        assertThat(DataPermissionResolver.splitColumns(" a , ,b ,, c ")).containsExactly("a", "b", "c");
        assertThat(DataPermissionResolver.splitColumns(null)).isEmpty();
        assertThat(DataPermissionResolver.splitColumns("   ")).isEmpty();
    }

    @Test
    @DisplayName("占位符判定 - 冒号开头即占位符，大小写与空格不影响识别")
    void should_recognisePlaceholders_when_validatingOnSave() {
        assertThat(DataPermissionResolver.isPlaceholder(" :userId ")).isTrue();
        assertThat(DataPermissionResolver.isKnownPlaceholder(" :userId ")).isTrue();
        assertThat(DataPermissionResolver.isKnownPlaceholder(":deptCode")).isFalse();
        assertThat(DataPermissionResolver.isPlaceholder(7L)).isFalse();
        assertThat(DataPermissionResolver.knownPlaceholders()).containsExactlyInAnyOrder(
                ":userId", ":tenantId", ":username", ":deptId");
    }

    @Test
    @DisplayName("条件值是列表 - 逐项替换占位符，任一项求不出则整条规则不放开")
    void should_resolveEachItem_when_conditionValueIsAList() {
        login(2L, "bob", 7L, "viewer");
        givenRules(roleRule(1L, "in 列表", "viewer",
                "[{\"field\":\"dept_id\",\"operator\":\"in\",\"value\":[\":userId\",3]}]", null));

        assertThat(resolver.resolve(1L, 2L, DATASET_ID).rowPredicate()).isEqualTo("(dept_id in [2, 3])");
    }

    @Test
    @DisplayName("in 列表里有求不出的占位符 - 整条条件作废")
    void should_dropCondition_when_listValueContainsUnresolvablePlaceholder() {
        login(2L, "bob", null, "viewer");
        givenRules(roleRule(1L, "in 部门", "viewer",
                "[{\"field\":\"dept_id\",\"operator\":\"in\",\"value\":[\":deptId\",3]}]", null));

        assertThat(resolver.resolve(1L, 2L, DATASET_ID).denyAll()).isTrue();
    }

}
