package com.dataviz.ai.service.impl;

import com.dataviz.ai.dto.Nl2SqlDTO;
import com.dataviz.ai.llm.LlmClient;
import com.dataviz.ai.llm.LlmReply;
import com.dataviz.ai.llm.SqlGuard;
import com.dataviz.ai.schema.DatasourceSchemaReader;
import com.dataviz.ai.service.Nl2SqlService;
import com.dataviz.ai.vo.Nl2SqlResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlServiceImpl implements Nl2SqlService {

    private static final String SQL_SYSTEM_PROMPT =
            "你是一个 SQL 专家。根据用户给出的数据库表结构和问题，生成一条可在 MySQL 上直接执行的查询语句。\n"
                    + "要求：只返回一条 SELECT（或 WITH 开头的 CTE）语句；不要返回 DDL/DML；不要返回多条语句；"
                    + "不要用 markdown 代码块；不要附加任何解释文字。";

    private static final String EXPLAIN_SYSTEM_PROMPT =
            "你是一个 SQL 专家。用中文解释用户给出的 SQL 语句在做什么，包括涉及的表、过滤条件、聚合口径与可能的性能风险。"
                    + "只返回解释文字，不要重写 SQL。";

    private final LlmClient llmClient;
    private final DatasourceSchemaReader schemaReader;

    @Override
    public Nl2SqlResultVO translate(Nl2SqlDTO dto) {
        String question = dto.getQuestion() == null ? "" : dto.getQuestion().trim();
        log.info("Translating NL to SQL: datasourceId={}, question={}", dto.getDatasourceId(), question);

        DatasourceSchemaReader.SchemaResult schema = schemaReader.read(dto.getDatasourceId());
        String note = schema.getNote();

        LlmReply reply = llmClient.chat(SQL_SYSTEM_PROMPT, buildTranslatePrompt(schema, question));
        if (!reply.isAvailable()) {
            return placeholder(joinNotes(reply.getReason(), note), question);
        }

        SqlGuard.Result check = SqlGuard.check(reply.getText());
        if (!check.isValid()) {
            // 一次带错误原因的修正机会：模型把解释和语句混在一起是常见失误，重试一次通常就干净了
            LlmReply retry = llmClient.chat(SQL_SYSTEM_PROMPT,
                    buildTranslatePrompt(schema, question)
                            + "\n\n上一次生成不合规：" + check.getReason() + "。请只返回一条 SELECT 语句。");
            if (retry.isAvailable()) {
                SqlGuard.Result retried = SqlGuard.check(retry.getText());
                if (retried.isValid()) {
                    check = retried;
                } else {
                    return rejected(retried, "重试后仍不合规：" + retried.getReason(), schema);
                }
            } else {
                // 原因取 retry 那一次的：第一条回复是成功的，它的 reason 是 null，
                // 拿它拼 note 会让界面显示"模型生成"却一句话都不说
                return rejected(check, retry.getReason(), schema);
            }
        }

        double confidence = schema.isPresent() ? 0.9 : 0.6;
        String explanation = "基于" + (schema.isPresent() ? schema.getColumnCount() + " 个列的真实表结构" : "问题本身（未取到表结构）")
                + "生成，已通过单条只读 SELECT 校验。执行前请自行核对口径。";
        return Nl2SqlResultVO.builder()
                .sql(check.getSql())
                .explanation(explanation)
                .confidence(confidence)
                .llmUsed(true)
                .llmNote(note)
                .build();
    }

    @Override
    public Nl2SqlResultVO explain(Nl2SqlDTO dto) {
        String sql = SqlGuard.stripFence(dto.getSql());
        log.info("Explaining SQL: datasourceId={}, sqlLength={}", dto.getDatasourceId(), sql.length());
        if (!StringUtils.hasText(sql)) {
            return Nl2SqlResultVO.builder()
                    .sql("")
                    .explanation("没有可解释的 SQL")
                    .confidence(0.0)
                    .llmUsed(false)
                    .llmNote("入参 sql 为空")
                    .build();
        }

        LlmReply reply = llmClient.chat(EXPLAIN_SYSTEM_PROMPT, "请解释这条 SQL：\n" + sql);
        if (!reply.isAvailable()) {
            return Nl2SqlResultVO.builder()
                    .sql(sql)
                    .explanation("未接入大模型，无法给出解释。")
                    .confidence(0.0)
                    .llmUsed(false)
                    .llmNote(reply.getReason())
                    .build();
        }
        return Nl2SqlResultVO.builder()
                .sql(sql)
                .explanation(reply.getText())
                .confidence(0.85)
                .llmUsed(true)
                .build();
    }

    private String buildTranslatePrompt(DatasourceSchemaReader.SchemaResult schema, String question) {
        StringBuilder prompt = new StringBuilder();
        if (schema.isPresent()) {
            prompt.append("数据库表结构：\n").append(schema.getSchema()).append('\n');
        } else {
            prompt.append("（未取到表结构，请只根据问题给出通用写法，并在语句里用注释标出需要替换的表名与列名）\n\n");
        }
        prompt.append("用户问题：").append(question);
        return prompt.toString();
    }

    /** 拿不到模型回复：不给一句看着像 SQL 的模板，只给注释，免得被当成可执行语句 */
    private Nl2SqlResultVO placeholder(String note, String question) {
        return Nl2SqlResultVO.builder()
                .sql("-- 未生成 SQL：" + note + "\n-- 原始问题：" + question)
                .explanation("未接入大模型，无法生成 SQL。")
                .confidence(0.0)
                .llmUsed(false)
                .llmNote(note)
                .build();
    }

    private Nl2SqlResultVO rejected(SqlGuard.Result check, String note, DatasourceSchemaReader.SchemaResult schema) {
        return Nl2SqlResultVO.builder()
                .sql(check.getSql())
                .explanation("模型生成的语句未通过只读校验，已原样返回供人工判断，不要直接执行。")
                .confidence(0.2)
                .llmUsed(true)
                .llmNote(joinNotes(note, schema.getNote()))
                .build();
    }

    private static String joinNotes(String a, String b) {
        if (!StringUtils.hasText(a)) {
            return b;
        }
        if (!StringUtils.hasText(b)) {
            return a;
        }
        return a + "；" + b;
    }
}
