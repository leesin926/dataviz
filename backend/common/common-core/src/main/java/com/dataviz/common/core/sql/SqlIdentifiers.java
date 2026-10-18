package com.dataviz.common.core.sql;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL 标识符白名单（表名、列名）。
 * <p>
 * 用在两处服务间通道上：datasource-service 的结构化批量写入（拼 INSERT）与 etl-service 的源端抽取
 * （拼 SELECT）。判据相同 —— 这两条通道<b>没有参数绑定可退</b>：标识符最终会成为 SQL 文本的一部分，
 * 放进 HTTP 请求体里传进来，所以只能认形状，不能靠转义。
 * <p>
 * analysis-service 的 {@code SqlBuilder} 另有一份（那边还要白名单操作符与字面量，口径更宽），
 * 两份的锚定与长度上限一致：<b>首字符字母或下划线，总长 ≤ 64</b>。改一边不改另一边，
 * 症状是"同一张表在分析页能查、在 ETL 里报非法标识符"。
 */
public final class SqlIdentifiers {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private SqlIdentifiers() {
    }

    /**
     * @param raw   待校验的标识符，允许首尾空白
     * @param label 出现在错误文案里的名字，如「目标表」「写入列」
     * @return 去掉首尾空白后的标识符
     * @throws BizException 空值或形状不合法
     */
    public static String require(String raw, String label) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "名不能为空");
        }
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "名不合法: " + raw);
        }
        return value;
    }

    /** 逐个校验并原序返回；调用方自己负责查重（重复的判据各家不同，不能在这里替你决定）。 */
    public static List<String> requireAll(List<String> raws, String label) {
        List<String> out = new ArrayList<>();
        if (raws == null) {
            return out;
        }
        for (String raw : raws) {
            out.add(require(raw, label));
        }
        return out;
    }
}
