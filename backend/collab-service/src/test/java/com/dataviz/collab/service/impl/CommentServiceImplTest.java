package com.dataviz.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.entity.CollabComment;
import com.dataviz.collab.mapper.CommentMapper;
import com.dataviz.collab.test.MybatisPlusTableInfoHelper;
import com.dataviz.collab.vo.CommentVO;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论服务：这一批的三条判据都落在"写进去的行到底长什么样"，而不是返回值。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private CommentServiceImpl commentService;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(CollabComment.class);
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
    @DisplayName("新建评论 - 作者取自会话，不靠公共填充器")
    void should_stampAuthorFromSession_when_create() {
        CommentCreateDTO dto = new CommentCreateDTO();
        dto.setTargetType("screen");
        dto.setTargetId(901L);
        dto.setContent("标题区换成主品牌色");

        commentService.create(dto);

        ArgumentCaptor<CollabComment> saved = ArgumentCaptor.forClass(CollabComment.class);
        verify(commentMapper).insert(saved.capture());
        // 公共填充器按 String 填 createBy，本表是 BIGINT ⇒ 填充被跳过；不显式写就是 NULL 作者
        assertEquals(7L, saved.getValue().getCreateBy());
        assertEquals("screen", saved.getValue().getTargetType());
        assertEquals(901L, saved.getValue().getTargetId());
    }

    @Test
    @DisplayName("新建评论 - parentId 为空时保持为空，交给 DDL 默认值")
    void should_leaveParentIdNull_when_createTopLevel() {
        CommentCreateDTO dto = new CommentCreateDTO();
        dto.setTargetType("dashboard");
        dto.setTargetId(1L);
        dto.setContent("补一个同比指标卡");

        commentService.create(dto);

        ArgumentCaptor<CollabComment> saved = ArgumentCaptor.forClass(CollabComment.class);
        verify(commentMapper).insert(saved.capture());
        // 断言的是"服务没有伪造 parent_id"：落库拿到 0 是 MyBatis-Plus 的 NOT_NULL 插入策略 +
        // DDL DEFAULT 0 共同决定的，读路径必须同时认 0 和 NULL（见下一条）。
        assertNull(saved.getValue().getParentId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_acceptBothNullAndZeroParent_when_listByTarget() {
        when(commentMapper.selectList(any(Wrapper.class))).thenReturn(Collections.<CollabComment>emptyList());

        commentService.listByTarget("screen", 901L);

        ArgumentCaptor<Wrapper<CollabComment>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(commentMapper).selectList(captor.capture());
        Wrapper<CollabComment> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        Map<String, Object> pairs = ((AbstractWrapper<CollabComment, ?, ?>) wrapper).getParamNameValuePairs();
        assertTrue(sql.contains("IS NULL"), "顶层条件缺 IS NULL: " + sql);
        assertTrue(sql.contains("OR"), "顶层条件应是 IS NULL 或 = 0 的并集: " + sql);
        assertTrue(pairs.containsValue(0L), "顶层条件应把 parent_id = 0 也算顶层: " + pairs);
    }

    @Test
    @DisplayName("删除评论 - 只删父行，子回复成为不可达的孤儿（本轮登记不修）")
    void should_deleteGivenIdOnly_when_delete() {
        commentService.delete(902L);

        verify(commentMapper).deleteById(902L);
        verify(commentMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("编辑评论 - 目标不存在时炸，不静默写一条不存在的行")
    void should_throw_when_updateMissing() {
        when(commentMapper.selectById(404L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentService.update(404L, "改一下"));
        assertTrue(ex.getMessage().contains("404"), ex.getMessage());
        verify(commentMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("编辑评论 - 命中时只改正文")
    void should_writeContentOnly_when_updateExisting() {
        CollabComment existing = new CollabComment();
        existing.setId(902L);
        existing.setContent("旧文案");
        existing.setCreateBy(7L);
        when(commentMapper.selectById(902L)).thenReturn(existing);

        commentService.update(902L, "新文案");

        ArgumentCaptor<CollabComment> updated = ArgumentCaptor.forClass(CollabComment.class);
        verify(commentMapper).updateById(updated.capture());
        assertEquals("新文案", updated.getValue().getContent());
        assertEquals(7L, updated.getValue().getCreateBy());
    }

    @Test
    @DisplayName("子回复列表 - 按父 id 升序返回并映射成 VO")
    void should_mapReplies_when_listReplies() {
        CollabComment reply = new CollabComment();
        reply.setId(903L);
        reply.setParentId(902L);
        reply.setContent("同意");
        reply.setCreateBy(8L);
        when(commentMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(reply));

        List<CommentVO> vos = commentService.listReplies(902L);

        assertEquals(1, vos.size());
        assertEquals(903L, vos.get(0).getId());
        assertEquals(8L, vos.get(0).getCreateBy());
    }
}
