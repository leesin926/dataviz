package com.dataviz.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataviz.analysis.client.ModelServiceClient;
import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.dto.DataPermRuleDTO;
import com.dataviz.analysis.entity.DataPermRule;
import com.dataviz.analysis.engine.DataPermission;
import com.dataviz.analysis.engine.DataPermissionResolver;
import com.dataviz.analysis.engine.SqlBuilder;
import com.dataviz.analysis.mapper.DataPermRuleMapper;
import com.dataviz.analysis.vo.DataPermExplainVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
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
 * 规则保存时的自证（API-21 的写端）。
 * <p>
 * 这一层的价值全在"存不下去"这件事上：行条件是长期存在的配置，等用户查询时才炸，
 * 症状是某个账号突然打不开分析页，而当事人不知道是谁改了哪条规则。
 * 所以每条用例都要同时看两头 —— 该挡的挡住了，而且 mapper 一次都没被调用。
 */
@ExtendWith(MockitoExtension.class)
class DataPermRuleServiceImplTest {

    @Mock
    private DataPermRuleMapper ruleMapper;

    @Mock
    private ModelServiceClient modelServiceClient;

    @Mock
    private DataPermissionResolver resolver;

    @Mock
    private SqlBuilder sqlBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DataPermRuleServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), DataPermRule.class);
    }

    @BeforeEach
    void setUp() {
        service = new DataPermRuleServiceImpl(ruleMapper, modelServiceClient, resolver, sqlBuilder, objectMapper);
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).username("bob").tenantId("1").deptId(7L)
                .roles(Collections.singleton("designer")).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private DataPermRuleDTO dto(String scopeType, String scopeValue,
                               List<DataPermRuleDTO.PermConditionDTO> conditions,
                               List<String> columns) {
        DataPermRuleDTO dto = new DataPermRuleDTO();
        dto.setName("测试规则");
        dto.setScopeType(scopeType);
        dto.setScopeValue(scopeValue);
        dto.setConditions(conditions);
        dto.setRestrictedColumns(columns);
        return dto;
    }

    private DataPermRuleDTO.PermConditionDTO condition(String field, String operator, Object value) {
        DataPermRuleDTO.PermConditionDTO item = new DataPermRuleDTO.PermConditionDTO();
        item.setField(field);
        item.setOperator(operator);
        item.setValue(value);
        return item;
    }

    private DataPermRule capturedInsert() {
        ArgumentCaptor<DataPermRule> captor = ArgumentCaptor.forClass(DataPermRule.class);
        verify(ruleMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("占位符 - 存进去前换成样例值并立刻试渲染")
    void should_replacePlaceholderWithSample_when_storingConditions() {
        when(sqlBuilder.permissionPredicate(anyList())).thenReturn("dept_id = 1");

        service.create(dto("ROLE", "designer",
                Collections.singletonList(condition("dept_id", "eq", " :deptId ")), null));

        // 渲染证明收到的是样例值而不是 ":deptId" —— 占位符本身过不了白名单
        ArgumentCaptor<List<AnalysisQueryDTO.FilterConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(sqlBuilder).permissionPredicate(captor.capture());
        assertThat(captor.getValue().get(0).getValue()).isEqualTo("1");

        assertThat(capturedInsert().getConditionsJson()).contains("\"dept_id\"");
    }

    @Test
    @DisplayName("拼错的占位符 - 保存就报错，不落库")
    void should_rejectSave_when_placeholderIsUnknown() {
        assertThatThrownBy(() -> service.create(dto("ROLE", "designer",
                Collections.singletonList(condition("dept_id", "eq", ":deptCode")), null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("dept_id")
                .hasMessageContaining("占位符")
                .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST.getCode());

        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("按用户命中但不是用户ID - 挡住")
    void should_rejectSave_when_userScopeValueIsNotAnId() {
        assertThatThrownBy(() -> service.create(dto("USER", "bob",
                Collections.singletonList(condition("amount", "gt", 0)), null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户ID");

        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("命中方式不在 USER / ROLE 里 - 挡住")
    void should_rejectSave_when_scopeTypeIsUnsupported() {
        assertThatThrownBy(() -> service.create(dto("DEPT", "7",
                Collections.singletonList(condition("amount", "gt", 0)), null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("USER");

        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("行条件与受限列都空 - 这条规则什么都不做，不许存")
    void should_rejectSave_when_neitherConditionsNorColumns() {
        assertThatThrownBy(() -> service.create(dto("ROLE", "designer", null, Collections.emptyList())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少填一项");

        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("脏列名 - 由 SqlBuilder 的标识符白名单当场挡掉")
    void should_rejectSave_when_restrictedColumnIsNotAnIdentifier() {
        assertThatThrownBy(() -> service.create(dto("ROLE", "designer", null,
                Arrays.asList("phone", "bad name"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("受限列");

        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("受限列去重去空格，逗号串入库")
    void should_normaliseColumns_when_storingColumnMask() {
        service.create(dto("ROLE", "designer", null, Arrays.asList(" phone ", "phone", "id_card")));

        assertThat(capturedInsert().getRestrictedColumns()).isEqualTo("phone,id_card");
        verify(sqlBuilder, never()).permissionPredicate(anyList());
    }

    @Test
    @DisplayName("只遮列的规则 - conditionsJson 留空，不做无谓的渲染证明")
    void should_storeNullConditions_when_ruleIsColumnOnly() {
        service.create(dto("ROLE", "designer", null, Collections.singletonList("phone")));

        DataPermRule stored = capturedInsert();
        assertThat(stored.getConditionsJson()).isNull();
        assertThat(stored.getEnabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("数据集不存在 - 不给它配规则")
    void should_rejectSave_when_datasetIsMissing() {
        DataPermRuleDTO input = dto("ROLE", "designer",
                Collections.singletonList(condition("amount", "gt", 0)), null);
        input.setDatasetId(99L);
        when(modelServiceClient.getDatasetMeta(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集不存在");

        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("跨租户配规则 - 403")
    void should_rejectSave_when_datasetBelongsToAnotherTenant() {
        DataPermRuleDTO input = dto("ROLE", "designer",
                Collections.singletonList(condition("amount", "gt", 0)), null);
        input.setDatasetId(99L);
        DatasetMeta meta = new DatasetMeta();
        meta.setTenantId("2");
        when(modelServiceClient.getDatasetMeta(99L)).thenReturn(meta);

        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.FORBIDDEN.getCode());

        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("enabled 只认 0 与非 0 - 脏值一律归到启用")
    void should_clampEnabled_when_clientSendsOddValue() {
        DataPermRuleDTO odd = dto("ROLE", "designer",
                Collections.singletonList(condition("amount", "gt", 0)), null);
        odd.setEnabled(5);
        when(sqlBuilder.permissionPredicate(anyList())).thenReturn("amount > 1");

        service.create(odd);
        assertThat(capturedInsert().getEnabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("改不存在的规则 - 404，不做无条件的 update")
    void should_rejectUpdate_when_ruleIsMissing() {
        when(ruleMapper.selectById(404L)).thenReturn(null);
        DataPermRuleDTO input = dto("ROLE", "designer",
                Collections.singletonList(condition("amount", "gt", 0)), null);

        assertThatThrownBy(() -> service.update(404L, input))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());

        verify(ruleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("解释接口 - 命中结果原样带给管理端，包括 deny-all")
    void should_mapExplainVo_when_adminAsksWhy() {
        DataPermission permission = DataPermission.deniedAll(
                new LinkedHashSet<>(Arrays.asList("id_card")), Collections.singletonList("遮身份证"), 3);
        when(resolver.explain(1L, 2L, 100L)).thenReturn(permission);

        DataPermExplainVO vo = service.explain(100L);

        assertThat(vo.isDenyAll()).isTrue();
        assertThat(vo.getRowPredicate()).isEqualTo("1 = 0");
        assertThat(vo.getRestrictedColumns()).containsExactly("id_card");
        assertThat(vo.getAppliedRuleNames()).containsExactly("遮身份证");
        assertThat(vo.getEnabledRuleCount()).isEqualTo(3);
    }
}
