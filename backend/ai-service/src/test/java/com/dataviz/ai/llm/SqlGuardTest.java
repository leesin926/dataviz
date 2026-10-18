package com.dataviz.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SqlGuard 是"模型输出不可信"这件事的唯一收口，所以既要验它拦得住写操作，
 * 也要验它别把正常查询误杀——误杀会让 NL2SQL 永远退化成注释，比放过一条危险语句更难被发现。
 */
class SqlGuardTest {

    @ParameterizedTest
    @DisplayName("只读查询放行")
    @ValueSource(strings = {
            "SELECT id FROM t_order",
            "select DATE(create_time) d, count(*) c from t_order group by d",
            "WITH s AS (SELECT id FROM t_order) SELECT count(*) FROM s",
            "(SELECT id FROM t_order LIMIT 1)",
    })
    void should_acceptReadOnlyQueries(String sql) {
        SqlGuard.Result result = SqlGuard.check(sql);
        assertTrue(result.isValid(), sql + " 应放行，实际理由：" + result.getReason());
        assertEquals(sql, result.getSql());
        assertNull(result.getReason());
    }

    @Test
    @DisplayName("已知误杀：replace() 既是函数名也是 REPLACE INTO 的关键字，宁可退回也不放行")
    void should_rejectReplaceEvenAsFunction() {
        SqlGuard.Result result = SqlGuard.check("SELECT replace(name, 'a', 'b') FROM t_user");
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("REPLACE"), result.getReason());
    }

    @ParameterizedTest
    @DisplayName("写操作与 DDL 一律拒绝，且理由点名被禁的关键字")
    @ValueSource(strings = {
            "DELETE FROM t_order",
            "UPDATE t_order SET status = 0",
            "INSERT INTO t_order (id) VALUES (1)",
            "DROP TABLE t_order",
            "TRUNCATE TABLE t_order",
            "GRANT ALL ON db TO 'x'",
            "SELECT * INTO OUTFILE '/tmp/a' FROM t_order",
    })
    void should_rejectWrites(String sql) {
        SqlGuard.Result result = SqlGuard.check(sql);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("被禁止的关键字") || result.getReason().contains("不是 SELECT"),
                sql + " 的理由应说明原因，实际：" + result.getReason());
    }

    @Test
    @DisplayName("分号即视为多条语句，不做逐条拆解")
    void should_rejectMultipleStatements() {
        SqlGuard.Result result = SqlGuard.check("SELECT 1; DELETE FROM t_order");
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("多条语句"));
    }

    @Test
    @DisplayName("非查询开头的语句拒绝")
    void should_rejectNonQueryHead() {
        SqlGuard.Result result = SqlGuard.check("SHOW TABLES");
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("不是 SELECT"));
    }

    @Test
    @DisplayName("空输入与 null 都退回，不给一句假 SQL")
    void should_rejectBlank() {
        assertFalse(SqlGuard.check(null).isValid());
        assertEquals("生成的 SQL 为空", SqlGuard.check("   ").getReason());
    }

    @Test
    @DisplayName("剥掉 markdown 围栏、前置注释与结尾分号后再判定")
    void should_stripFenceCommentAndTrailingSemicolon() {
        String fenced = "```sql\n-- 近 7 天订单\nSELECT id FROM t_order;\n```";
        SqlGuard.Result result = SqlGuard.check(fenced);
        assertTrue(result.isValid(), result.getReason());
        assertEquals("SELECT id FROM t_order", result.getSql());
    }

    @Test
    @DisplayName("列名里的 create_time 之类不该被当成 DDL 关键字")
    void should_notMatchKeywordInsideIdentifier() {
        assertTrue(SqlGuard.check("SELECT create_time, update_time FROM t_order").isValid());
    }

    @Test
    @DisplayName("stripFence 只处理围栏与首尾，语句中间的分号留着让 check 判")
    void should_keepInnerSemicolonInStripFence() {
        assertEquals("SELECT 1; SELECT 2", SqlGuard.stripFence(" SELECT 1; SELECT 2; "));
    }
}
