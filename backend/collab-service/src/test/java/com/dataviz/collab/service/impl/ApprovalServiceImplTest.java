package com.dataviz.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.collab.dto.ApprovalCreateDTO;
import com.dataviz.collab.dto.ApprovalDecisionDTO;
import com.dataviz.collab.entity.CollabApproval;
import com.dataviz.collab.enums.ApprovalStatus;
import com.dataviz.collab.mapper.ApprovalMapper;
import com.dataviz.collab.test.MybatisPlusTableInfoHelper;
import com.dataviz.collab.vo.ApprovalVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批流水。三条判据各占一角：身份只能从会话来（这张表没有公共填充器兜着）、
 * 状态只能单向流转、裁决必须是条件更新 —— 两个审批人同时打开同一张待办时，
 * "读出来改完再写回去"会让后一个人的裁决静默覆盖前一个人的，而且流水看起来完全正常。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceImplTest {

    /** 会话用户是 7；裁决用例的申请人都得用别人，否则还没走到断言就被自批拦截了 */
    private static final Long APPLICANT = 21L;

    @Mock
    private ApprovalMapper approvalMapper;

    @InjectMocks
    private ApprovalServiceImpl approvalService;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(CollabApproval.class);
    }

    @BeforeEach
    void setUpSession() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(7L).username("designer01").tenantId("1").build());
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("提审 - 申请人与租户取自会话，类型归一为大写")
    void should_stampApplicantAndTenant_when_apply() {
        when(approvalMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        approvalService.apply(createDto("screen", 901L));

        ArgumentCaptor<CollabApproval> captor = ArgumentCaptor.forClass(CollabApproval.class);
        verify(approvalMapper).insert(captor.capture());
        CollabApproval saved = captor.getValue();
        assertEquals("SCREEN", saved.getTargetType());
        assertEquals(Long.valueOf(1L), saved.getTenantId());
        assertEquals(Long.valueOf(7L), saved.getApplicantId());
        assertEquals(Integer.valueOf(ApprovalStatus.PENDING.getCode()), saved.getStatus());
        assertNull(saved.getApproverId());
    }

    @Test
    @DisplayName("提审 - 对象类型是闭集，脏类型不进库")
    void should_reject_when_targetTypeUnsupported() {
        BizException e = assertThrows(BizException.class,
                () -> approvalService.apply(createDto("WIDGET", 1L)));

        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
        assertTrue(e.getMessage().contains("WIDGET"), e.getMessage());
        verify(approvalMapper, never()).insert(any(CollabApproval.class));
    }

    @Test
    @DisplayName("提审 - 同一人对同一目标的待审单不重复开")
    void should_reject_when_sameApplicantAlreadyPending() {
        when(approvalMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BizException e = assertThrows(BizException.class,
                () -> approvalService.apply(createDto("SCREEN", 901L)));

        assertEquals(ErrorCode.CONFLICT, e.getErrorCode());
        verify(approvalMapper, never()).insert(any(CollabApproval.class));
    }

    @Test
    @DisplayName("提审 - 无会话身份直接拒，不能落一条没有申请人的流水")
    void should_reject_when_noLoginUser() {
        SecurityContextHolder.clear();

        BizException e = assertThrows(BizException.class,
                () -> approvalService.apply(createDto("SCREEN", 901L)));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
        verify(approvalMapper, never()).insert(any(CollabApproval.class));
    }

    @Test
    @DisplayName("提审 - 会话租户不是数字时拒绝，而不是归属到别的租户")
    void should_reject_when_tenantNotNumeric() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(7L).username("designer01").tenantId("acme").build());

        BizException e = assertThrows(BizException.class,
                () -> approvalService.apply(createDto("SCREEN", 901L)));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
        assertTrue(e.getMessage().contains("acme"), e.getMessage());
    }

    @Test
    @DisplayName("裁决 - 走条件更新：不写回整行，where 同时锁 id 和待审状态")
    void should_useConditionalUpdate_when_approve() {
        CollabApproval decided = pending(5L, APPLICANT);
        decided.setStatus(ApprovalStatus.APPROVED.getCode());
        decided.setApproverId(8L);
        decided.setComment("同意发布");
        when(approvalMapper.selectById(5L)).thenReturn(pending(5L, APPLICANT), decided);
        when(approvalMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ApprovalVO vo = approvalService.approve(5L, decision("同意发布"));

        assertEquals(Integer.valueOf(ApprovalStatus.APPROVED.getCode()), vo.getStatus());
        assertEquals("已通过", vo.getStatusLabel());

        ArgumentCaptor<Wrapper<CollabApproval>> captor = ArgumentCaptor.forClass(Wrapper.class);
        // 第一个参数是 null：只按条件更新那几列，不把整行写回去
        verify(approvalMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<CollabApproval> wrapper =
                (LambdaUpdateWrapper<CollabApproval>) captor.getValue();
        String where = wrapper.getSqlSegment();
        assertTrue(where.contains("id ="), where);
        assertTrue(where.contains("status ="), where);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(5L),
                String.valueOf(wrapper.getParamNameValuePairs()));
        assertTrue(wrapper.getSqlSet().contains("approver_id="), wrapper.getSqlSet());
        assertTrue(wrapper.getSqlSet().contains("approve_time="), wrapper.getSqlSet());
    }

    @Test
    @DisplayName("裁决 - 空白意见存成 null，不给列表留一条空串")
    void should_storeNullComment_when_commentBlank() {
        CollabApproval decided = pending(5L, APPLICANT);
        decided.setStatus(ApprovalStatus.REJECTED.getCode());
        decided.setComment(null);
        when(approvalMapper.selectById(5L)).thenReturn(pending(5L, APPLICANT), decided);
        when(approvalMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ApprovalVO vo = approvalService.reject(5L, decision("   "));

        assertNull(vo.getComment());
        assertEquals(Integer.valueOf(ApprovalStatus.REJECTED.getCode()), vo.getStatus());
        ArgumentCaptor<Wrapper<CollabApproval>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(approvalMapper).update(isNull(), captor.capture());
        Collection<Object> values =
                ((AbstractWrapper<CollabApproval, ?, ?>) captor.getValue()).getParamNameValuePairs().values();
        assertFalse(values.contains("   "), String.valueOf(values));
        assertTrue(captor.getValue() instanceof LambdaUpdateWrapper);
    }

    @Test
    @DisplayName("裁决 - 已裁决的单子不能再改，状态只能单向流转")
    void should_reject_when_alreadyDecided() {
        CollabApproval approved = pending(5L, APPLICANT);
        approved.setStatus(ApprovalStatus.APPROVED.getCode());
        when(approvalMapper.selectById(5L)).thenReturn(approved);

        BizException e = assertThrows(BizException.class, () -> approvalService.reject(5L, null));

        assertEquals(ErrorCode.CONFLICT, e.getErrorCode());
        assertTrue(e.getMessage().contains("已通过"), e.getMessage());
        verify(approvalMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("裁决 - 影响 0 行说明有人先动了，报错且不回读、不覆盖")
    void should_reject_when_someoneElseDecidedFirst() {
        when(approvalMapper.selectById(5L)).thenReturn(pending(5L, APPLICANT));
        when(approvalMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        BizException e = assertThrows(BizException.class,
                () -> approvalService.approve(5L, decision("同意")));

        assertEquals(ErrorCode.CONFLICT, e.getErrorCode());
        assertTrue(e.getMessage().contains("其他人"), e.getMessage());
        // 失败路径不回读，否则会把别人的裁决当成自己的结果返回给前端
        verify(approvalMapper, times(1)).selectById(5L);
    }

    @Test
    @DisplayName("裁决 - 申请人不能自己批")
    void should_rejectSelfApproval_when_notSuperAdmin() {
        when(approvalMapper.selectById(5L)).thenReturn(pending(5L, 7L));

        BizException e = assertThrows(BizException.class,
                () -> approvalService.approve(5L, null));

        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verify(approvalMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("裁决 - 超管可以自批，单人租户不至于卡死")
    void should_allowSelfApproval_when_superAdmin() {
        asSuperAdmin();
        CollabApproval decided = pending(5L, 7L);
        decided.setStatus(ApprovalStatus.APPROVED.getCode());
        decided.setApproverId(7L);
        when(approvalMapper.selectById(5L)).thenReturn(pending(5L, 7L), decided);
        when(approvalMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertEquals(Integer.valueOf(ApprovalStatus.APPROVED.getCode()),
                approvalService.approve(5L, null).getStatus());
    }

    @Test
    @DisplayName("详情 - 单号不存在时报 NOT_FOUND，不返回空壳")
    void should_reject_when_approvalMissing() {
        when(approvalMapper.selectById(99L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> approvalService.detail(99L));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("待办列表 - 只看待审且排除自己提的单")
    void should_filterPendingAndExcludeOwn_when_todoOnly() {
        stubEmptyPage();

        approvalService.page(null, null, null, false, true, pageQuery());

        Wrapper<CollabApproval> wrapper = capturePageWrapper();
        String where = wrapper.getSqlSegment();
        assertTrue(where.contains("status ="), where);
        assertTrue(where.contains("applicant_id <>"), where);
    }

    @Test
    @DisplayName("待办列表 - 超管能看见自己提的单（他本来就允许自批）")
    void should_keepOwnTickets_when_superAdminTodo() {
        asSuperAdmin();
        stubEmptyPage();

        approvalService.page(null, null, null, false, true, pageQuery());

        String where = capturePageWrapper().getSqlSegment();
        assertTrue(where.contains("status ="), where);
        assertFalse(where.contains("applicant_id <>"), where);
    }

    @Test
    @DisplayName("列表 - 未知状态码直接拒，不静默当成无过滤")
    void should_reject_when_statusCodeUnknown() {
        BizException e = assertThrows(BizException.class,
                () -> approvalService.page(null, null, 9, false, false, pageQuery()));

        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
        verify(approvalMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("列表 - 我提的按申请人过滤，分页元数据回传给前端")
    void should_filterByApplicant_when_mine() {
        Page<CollabApproval> page = new Page<CollabApproval>(2, 5);
        page.setRecords(Collections.singletonList(pending(5L, 7L)));
        page.setTotal(11L);
        when(approvalMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageQuery query = pageQuery();
        query.setPageNum(2);
        query.setPageSize(5);
        PageResult<ApprovalVO> result = approvalService.page("screen", 901L, null, true, false, query);

        String where = capturePageWrapper().getSqlSegment();
        assertTrue(where.contains("applicant_id ="), where);
        assertTrue(where.contains("target_type ="), where);
        assertTrue(where.contains("target_id ="), where);
        assertEquals(11L, result.getTotal());
        assertEquals(3, result.getTotalPages());
        assertEquals(1, result.getRecords().size());
        assertEquals("待审批", result.getRecords().get(0).getStatusLabel());
    }

    @Test
    @DisplayName("列表 - 查询结果为 null 时回空表而不是把 null 吐给前端")
    void should_returnEmptyRecords_when_pageHasNoRecords() {
        Page<CollabApproval> page = new Page<CollabApproval>(1, 10);
        page.setRecords(null);
        page.setTotal(0L);
        when(approvalMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        assertEquals(0, approvalService.page(null, null, null, false, false, pageQuery())
                .getRecords().size());
    }

    // ---------- helpers ----------

    private void asSuperAdmin() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(7L).username("admin").tenantId("1")
                .roles(new HashSet<String>(Collections.singletonList("super_admin"))).build());
    }

    private void stubEmptyPage() {
        when(approvalMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<CollabApproval>(1, 10));
    }

    private Wrapper<CollabApproval> capturePageWrapper() {
        ArgumentCaptor<Wrapper<CollabApproval>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(approvalMapper).selectPage(any(Page.class), captor.capture());
        return captor.getValue();
    }

    private static PageQuery pageQuery() {
        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        return query;
    }

    private static ApprovalCreateDTO createDto(String targetType, Long targetId) {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setTargetType(targetType);
        dto.setTargetId(targetId);
        return dto;
    }

    private static ApprovalDecisionDTO decision(String comment) {
        ApprovalDecisionDTO dto = new ApprovalDecisionDTO();
        dto.setComment(comment);
        return dto;
    }

    private static CollabApproval pending(Long id, Long applicantId) {
        return CollabApproval.builder()
                .id(id).tenantId(1L).targetType("SCREEN").targetId(901L)
                .applicantId(applicantId).status(ApprovalStatus.PENDING.getCode())
                .createTime(LocalDateTime.now()).build();
    }
}
