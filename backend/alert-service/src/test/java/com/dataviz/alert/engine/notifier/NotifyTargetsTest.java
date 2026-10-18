package com.dataviz.alert.engine.notifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyTargetsTest {

    @Test
    @DisplayName("of 去重、去空并保留来源")
    void should_deduplicateAndTrim_when_ofCalled() {
        NotifyTargets targets = NotifyTargets.of(
                Arrays.asList("a@x.com", " a@x.com ", null, "b@x.com"),
                Arrays.asList("+86138", "+86138", "", "  "),
                NotifyTargets.Source.GROUP);

        assertEquals(Arrays.asList("a@x.com", "b@x.com"), targets.emails());
        assertEquals(Collections.singletonList("+86138"), targets.mobiles());
        assertEquals(NotifyTargets.Source.GROUP, targets.source());
        assertTrue(targets.hasEmails());
        assertTrue(targets.hasMobiles());
    }

    @Test
    @DisplayName("空列表返回来源为 NONE 的目标")
    void should_returnNoneTarget_when_emptyListsProvided() {
        NotifyTargets targets = NotifyTargets.of(Collections.emptyList(), Collections.emptyList(), NotifyTargets.Source.NONE);

        assertEquals(NotifyTargets.Source.NONE, targets.source());
        assertFalse(targets.hasEmails());
        assertFalse(targets.hasMobiles());
    }

    @Test
    @DisplayName("describe 拼接所有收件地址")
    void should_joinRecipients_when_describeCalled() {
        NotifyTargets targets = NotifyTargets.of(
                Arrays.asList("a@x.com", "b@x.com"),
                Collections.singletonList("+86138"),
                NotifyTargets.Source.GROUP);

        assertEquals("a@x.com,b@x.com,+86138", targets.describe());
    }

    @Test
    @DisplayName("无收件人时 describe 为空串")
    void should_returnEmptyString_when_noRecipients() {
        assertEquals("", NotifyTargets.none().describe());
    }
}
