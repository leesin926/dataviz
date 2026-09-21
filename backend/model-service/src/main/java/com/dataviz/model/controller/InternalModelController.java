package com.dataviz.model.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.model.service.DatasetService;
import com.dataviz.model.vo.DatasetMetaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间数据集元数据接口：analysis（OLAP 执行要知道真实表名/自定义 SQL 与 datasource_id）、
 * etl（抽取配置）从这里取，避免各自凭 datasetId 猜表名。
 * <p>
 * 免 JWT 但受三层保护：网关 DENY_LIST 拒绝外部访问内部路径、{@code internal.api.token} 口令校验、
 * 以及这里只读元数据（不含数据源口令）。本服务路由没有 StripPrefix，故路径带 /api（见 D38）。
 */
@Slf4j
@RestController
@RequestMapping("/api/model/internal")
@RequiredArgsConstructor
@Tag(name = "内部元数据接口", description = "服务间调用的数据集定义读取，不对外")
public class InternalModelController {

    private final DatasetService datasetService;

    @GetMapping("/dataset/{id}")
    @Operation(summary = "读取数据集执行元数据（服务间）",
            description = "data 为 null 表示数据集不存在，与调用失败（503）是两种语义")
    public R<DatasetMetaVO> getDatasetMeta(@PathVariable("id") Long id) {
        DatasetMetaVO meta = datasetService.getDatasetMeta(id);
        if (meta == null) {
            log.info("内部元数据查询未命中数据集: id={}", id);
        }
        return R.ok(meta);
    }
}
