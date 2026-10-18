package com.dataviz.alert.service.impl;

import com.dataviz.alert.dto.AlertContactDTO;
import com.dataviz.alert.entity.AlertContact;
import com.dataviz.alert.entity.AlertNotifyGroup;
import com.dataviz.alert.entity.AlertNotifyGroupMember;
import com.dataviz.alert.mapper.AlertContactMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMemberMapper;
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertContactServiceImplTest {

    private AlertContactMapper contactMapper;
    private AlertNotifyGroupMapper groupMapper;
    private AlertNotifyGroupMemberMapper memberMapper;
    private AlertContactServiceImpl contactService;

    @BeforeAll
    static void setUpClass() {
        MybatisPlusTableInfoHelper.initFor(AlertContact.class);
    }

    @BeforeEach
    void setUp() {
        contactMapper = mock(AlertContactMapper.class);
        groupMapper = mock(AlertNotifyGroupMapper.class);
        memberMapper = mock(AlertNotifyGroupMemberMapper.class);
        contactService = new AlertContactServiceImpl(contactMapper, groupMapper, memberMapper);
    }

    @Test
    @DisplayName("创建联系人成功并返回 id")
    void should_createContact_when_inputValid() {
        AlertContactDTO dto = new AlertContactDTO();
        dto.setName("张三");
        dto.setEmail("zhangsan@x.com");
        dto.setMobile("+8613800138000");
        dto.setEnabled(true);

        when(contactMapper.insert(any(AlertContact.class))).thenAnswer(inv -> {
            AlertContact c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        Long id = contactService.create(dto);

        assertEquals(1L, id);
        ArgumentCaptor<AlertContact> captor = ArgumentCaptor.forClass(AlertContact.class);
        verify(contactMapper).insert(captor.capture());
        AlertContact saved = captor.getValue();
        assertEquals("张三", saved.getName());
        assertEquals("zhangsan@x.com", saved.getEmail());
        assertEquals("+8613800138000", saved.getMobile());
        assertTrue(saved.getEnabled());
    }

    @Test
    @DisplayName("创建联系人缺少姓名抛异常")
    void should_throwException_when_nameMissing() {
        AlertContactDTO dto = new AlertContactDTO();
        dto.setEmail("a@x.com");

        assertThrows(BizException.class, () -> contactService.create(dto));
        verify(contactMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建联系人邮箱和手机都为空抛异常")
    void should_throwException_when_noReachableAddress() {
        AlertContactDTO dto = new AlertContactDTO();
        dto.setName("张三");

        BizException ex = assertThrows(BizException.class, () -> contactService.create(dto));
        assertTrue(ex.getMessage().contains("邮箱与手机号至少填一个"));
    }

    @Test
    @DisplayName("创建联系人邮箱格式错误抛异常")
    void should_throwException_when_emailInvalid() {
        AlertContactDTO dto = new AlertContactDTO();
        dto.setName("张三");
        dto.setEmail("not-an-email");

        assertThrows(BizException.class, () -> contactService.create(dto));
    }

    @Test
    @DisplayName("创建联系人手机号格式错误抛异常")
    void should_throwException_when_mobileInvalid() {
        AlertContactDTO dto = new AlertContactDTO();
        dto.setName("张三");
        dto.setMobile("abc");

        assertThrows(BizException.class, () -> contactService.create(dto));
    }

    @Test
    @DisplayName("更新联系人走 wrapper 显式 SET 可空列")
    void should_updateViaWrapper_when_clearingOptionalFields() {
        AlertContact existing = new AlertContact();
        existing.setId(1L);
        existing.setName("张三");
        existing.setEmail("old@x.com");
        existing.setMobile("+8613800138000");
        existing.setRemark("old");

        AlertContactDTO dto = new AlertContactDTO();
        dto.setId(1L);
        dto.setName("张三新");
        dto.setEmail("zhangsan@x.com");
        dto.setMobile(null);
        dto.setRemark("");
        dto.setEnabled(false);

        when(contactMapper.selectById(1L)).thenReturn(existing);
        when(contactMapper.update(any(AlertContact.class), any())).thenReturn(1);

        contactService.update(dto);

        ArgumentCaptor<AlertContact> patchCaptor = ArgumentCaptor.forClass(AlertContact.class);
        verify(contactMapper).update(patchCaptor.capture(), any());
        AlertContact patch = patchCaptor.getValue();
        assertEquals(1L, patch.getId());
        assertEquals("张三新", patch.getName());
        assertFalse(patch.getEnabled());
        assertEquals(null, patch.getEmail());
        assertEquals(null, patch.getMobile());
        assertEquals(null, patch.getRemark());
    }

    @Test
    @DisplayName("删除联系人时仍被通知组引用则抛异常")
    void should_throwException_when_deleteReferencedContact() {
        AlertContact contact = new AlertContact();
        contact.setId(1L);
        contact.setName("张三");

        when(contactMapper.selectById(1L)).thenReturn(contact);
        when(memberMapper.selectList(any())).thenReturn(
                Collections.singletonList(memberOf(1L, 1L)));
        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(1L);
        group.setName("运维组");
        when(groupMapper.selectList(any())).thenReturn(Collections.singletonList(group));

        BizException ex = assertThrows(BizException.class, () -> contactService.delete(1L));
        assertTrue(ex.getMessage().contains("仍被"));
        verify(contactMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除未被引用的联系人成功")
    void should_deleteContact_when_notReferenced() {
        AlertContact contact = new AlertContact();
        contact.setId(1L);
        contact.setName("张三");

        when(contactMapper.selectById(1L)).thenReturn(contact);
        when(memberMapper.selectList(any())).thenReturn(Collections.emptyList());

        contactService.delete(1L);

        verify(contactMapper).deleteById(1L);
    }

    @Test
    @DisplayName("options 按姓名升序返回并附带组计数")
    void should_returnSortedOptions_withGroupCount() {
        AlertContact c1 = new AlertContact();
        c1.setId(1L);
        c1.setName("张三");
        AlertContact c2 = new AlertContact();
        c2.setId(2L);
        c2.setName("李四");

        when(contactMapper.selectList(any())).thenReturn(Arrays.asList(c1, c2));
        when(memberMapper.selectList(any())).thenReturn(Arrays.asList(
                memberOf(1L, 1L),
                memberOf(1L, 2L),
                memberOf(2L, 1L)));

        assertEquals(2, contactService.options().size());
        assertEquals("张三", contactService.options().get(0).getName());
        assertEquals("李四", contactService.options().get(1).getName());
        assertEquals(Integer.valueOf(2), contactService.options().get(0).getGroupCount());
        assertEquals(Integer.valueOf(1), contactService.options().get(1).getGroupCount());
    }

    private AlertNotifyGroupMember memberOf(Long contactId, Long groupId) {
        AlertNotifyGroupMember m = new AlertNotifyGroupMember();
        m.setContactId(contactId);
        m.setGroupId(groupId);
        return m;
    }
}
