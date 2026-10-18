package com.dataviz.ai.llm;

import java.util.regex.Pattern;

/**
 * 大模型生成的 SQL 的只读校验。
 *
 * <p>为什么必须有这一道：NL2SQL 的输出会被用户复制到查询页去执行，而模型完全可能生成
 * {@code DELETE}/{@code DROP}，或者把解释文字和语句混在一起。生成侧是不可信的输入源，
 * 所以这里按"只允许单条 SELECT/WITH"收口，不合规就明确退回，而不是把置信度调低了事。</p>
 */
public final class SqlGuard {

    /** 写操作与 DDL：出现在语句里就判不合规（用词边界，避免误伤 create_time 这类列名） */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|rename|replace|grant|revoke|"
                    + "call|execute|load|handler|lock|unlock|set|use|into|outfile|dumpfile)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LEADING_COMMENT = Pattern.compile("^\\s*(--[^\\n]*\\n|/\\*.*?\\*/|\\s)+", Pattern.DOTALL);

    private SqlGuard() {
    }

    public static Result check(String rawSql) {
        String sql = stripFence(rawSql);
        if (sql.isEmpty()) {
            return Result.reject(sql, "生成的 SQL 为空");
        }
        if (sql.contains(";")) {
            return Result.reject(sql, "包含多条语句（出现分号），只允许单条查询");
        }
        String lower = sql.toLowerCase();
        if (!(lower.startsWith("select") || lower.startsWith("with") || lower.startsWith("(select"))) {
            return Result.reject(sql, "不是 SELECT 查询");
        }
        java.util.regex.Matcher matcher = FORBIDDEN.matcher(sql);
        if (matcher.find()) {
            return Result.reject(sql, "包含被禁止的关键字: " + matcher.group(1).toUpperCase());
        }
        return Result.accept(sql);
    }

    /** 去掉 markdown 代码围栏与首尾空白/注释，并抹掉结尾分号 */
    public static String stripFence(String rawSql) {
        if (rawSql == null) {
            return "";
        }
        String text = rawSql.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
        }
        text = LEADING_COMMENT.matcher(text).replaceAll("");
        text = text.trim();
        while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }

    /** 校验结果：{@code valid=false} 时 {@code reason} 说明为什么不能用 */
    public static final class Result {
        private final boolean valid;
        private final String sql;
        private final String reason;

        private Result(boolean valid, String sql, String reason) {
            this.valid = valid;
            this.sql = sql;
            this.reason = reason;
        }

        static Result accept(String sql) {
            return new Result(true, sql, null);
        }

        static Result reject(String sql, String reason) {
            return new Result(false, sql, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String getSql() {
            return sql;
        }

        public String getReason() {
            return reason;
        }
    }
}
