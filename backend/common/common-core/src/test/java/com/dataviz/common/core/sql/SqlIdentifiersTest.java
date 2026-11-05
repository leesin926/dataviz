package com.dataviz.common.core.sql;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SQL 标识符白名单：datasource-service 的批量写入与 etl-service 的源端抽取都直接把它拼进 SQL 文本，
 * 没有参数绑定可退。这里测的是"形状判据有多严"——反引号、引号、空格、分号、注释符必须全被挡掉，
 * 因为放过去就是注入；同时边界（空、恰好 64、65）要钉死，改一边不改另一边会造成
 * "同一张表在分析页能查、在 ETL 里报非法标识符"。
 */
class SqlIdentifiersTest {

    private static final String LABEL = "目标表";

    @Test
    @DisplayName("常规小写表名原样通过")
    void should_returnSameName_when_plainIdentifier() {
        // Given
        String raw = "user_order";

        // When
        String actual = SqlIdentifiers.require(raw, LABEL);

        // Then
        assertEquals("user_order", actual);
    }

    @Test
    @DisplayName("首字符允许下划线，后续允许数字")
    void should_accept_when_leadingUnderscoreAndDigits() {
        // Given/When/Then
        assertEquals("_tmp_2024", SqlIdentifiers.require("_tmp_2024", "写入列"));
        assertEquals("t_1", SqlIdentifiers.require("t_1", "写入列"));
        assertEquals("T_Order_9", SqlIdentifiers.require("T_Order_9", "写入列"));
    }

    @Test
    @DisplayName("首尾空白被裁掉后按裁剪值校验并返回裁剪值")
    void should_trimAndAccept_when_surroundedByWhitespace() {
        // Given
        String raw = "  t_order\t\n";

        // When
        String actual = SqlIdentifiers.require(raw, LABEL);

        // Then
        assertEquals("t_order", actual);
    }

    @Test
    @DisplayName("反引号必须被拒：放过去就等于允许调用方自己闭合引用")
    void should_reject_when_wrappedInBackticks() {
        // Given
        BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.require("`t_order`", LABEL));

        // When/Then
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("目标表名不合法: `t_order`", ex.getMessage());
    }

    @Test
    @DisplayName("双引号/单引号一律拒")
    void should_reject_when_quoted() {
        // Given/When/Then
        assertEquals("目标表名不合法: \"t_order\"",
                assertThrows(BizException.class, () -> SqlIdentifiers.require("\"t_order\"", LABEL)).getMessage());
        assertEquals("目标表名不合法: 't_order'",
                assertThrows(BizException.class, () -> SqlIdentifiers.require("'t_order'", LABEL)).getMessage());
    }

    @Test
    @DisplayName("标识符内部空格拒：空格是注入的第二级拼装点")
    void should_reject_when_containsInnerSpace() {
        // Given
        BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.require("t order", LABEL));

        // When/Then
        assertEquals("目标表名不合法: t order", ex.getMessage());
    }

    @Test
    @DisplayName("分号语句串联拒")
    void should_reject_when_containsSemicolon() {
        // Given
        String evil = "t_order;DROP TABLE sys_user";

        // When
        BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.require(evil, LABEL));

        // Then
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("目标表名不合法: " + evil, ex.getMessage());
    }

    @Test
    @DisplayName("注释符 -- 与行尾注释形态拒")
    void should_reject_when_containsCommentMarker() {
        // Given/When/Then
        assertEquals("目标表名不合法: t--x",
                assertThrows(BizException.class, () -> SqlIdentifiers.require("t--x", LABEL)).getMessage());
        assertEquals("目标表名不合法: t_order --",
                assertThrows(BizException.class, () -> SqlIdentifiers.require("t_order --", LABEL)).getMessage());
    }

    @Test
    @DisplayName("UNION 注入常见形态整串拒（不是靠转义放行）")
    void should_reject_when_sqlInjectionShape() {
        // Given/When/Then
        assertThrows(BizException.class, () -> SqlIdentifiers.require("t_order union select 1", LABEL));
        assertThrows(BizException.class, () -> SqlIdentifiers.require("t_order/**/x", LABEL));
    }

    @Test
    @DisplayName("中文/全角等 unicode 标识符拒：白名单只认 ASCII，避免 MySQL 反引号名绕过校验")
    void should_reject_when_unicodeIdentifier() {
        // Given/When/Then
        assertEquals("目标表名不合法: 用户表",
                assertThrows(BizException.class, () -> SqlIdentifiers.require("用户表", LABEL)).getMessage());
        assertThrows(BizException.class, () -> SqlIdentifiers.require("t_order_中文", LABEL));
        assertThrows(BizException.class, () -> SqlIdentifiers.require("ｔ＿ｏｒｄｅｒ", LABEL));
    }

    @Test
    @DisplayName("首字符是数字拒")
    void should_reject_when_startsDigit() {
        // Given
        BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.require("1order", LABEL));

        // When/Then
        assertEquals("目标表名不合法: 1order", ex.getMessage());
    }

    @Test
    @DisplayName("null / 空串 / 纯空白都报“名不能为空”，与“不合法”区分开")
    void should_rejectWithEmptyMessage_when_inputBlank() {
        // Given/When/Then
        for (String raw : Arrays.asList(null, "", "   ", "\t\n")) {
            BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.require(raw, LABEL));
            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode(), String.valueOf(raw));
            assertEquals("目标表名不能为空", ex.getMessage(), String.valueOf(raw));
            assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode(), String.valueOf(raw));
        }
    }

    @Test
    @DisplayName("长度上界钉死在 64：64 通过，65 拒")
    void should_enforceSixtyFourCharCeiling() {
        // Given
        String name64 = repeat('a', 64);
        String name65 = repeat('a', 65);

        // When
        String accepted = SqlIdentifiers.require(name64, LABEL);

        // Then
        assertEquals(64, accepted.length());
        BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.require(name65, LABEL));
        assertEquals("目标表名不合法: " + name65, ex.getMessage());
    }

    @Test
    @DisplayName("requireAll 原序返回逐个裁剪后的名字，不查重（查重口径由调用方定）")
    void should_keepOrderAndDuplicates_when_requireAll() {
        // Given
        List<String> raws = Arrays.asList(" b ", "a", "b");

        // When
        List<String> out = SqlIdentifiers.requireAll(raws, "写入列");

        // Then
        assertEquals(Arrays.asList("b", "a", "b"), out);
    }

    @Test
    @DisplayName("requireAll 传 null 得到空列表而不是 NPE")
    void should_returnEmptyList_when_requireAllGivenNull() {
        // Given/When/Then
        assertEquals(0, SqlIdentifiers.requireAll(null, "写入列").size());
        assertEquals(0, SqlIdentifiers.requireAll(Collections.<String>emptyList(), "写入列").size());
    }

    @Test
    @DisplayName("requireAll 遇非法项立即抛出，错误文案带的是未裁剪的原始输入")
    void should_failFastWithRawInput_when_anyElementInvalid() {
        // Given
        List<String> raws = Arrays.asList("ok_col", " bad col ");

        // When
        BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.requireAll(raws, "写入列"));

        // Then
        assertEquals("写入列名不合法:  bad col ", ex.getMessage());
    }

    @Test
    @DisplayName("错误文案里的 label 只影响可读性：调用方传的 label 原样出现在消息中")
    void should_echoLabel_when_rejected() {
        // Given/When
        BizException ex = assertThrows(BizException.class, () -> SqlIdentifiers.require("a;b", "源表"));

        // Then
        assertEquals("源表名不合法: a;b", ex.getMessage());
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
