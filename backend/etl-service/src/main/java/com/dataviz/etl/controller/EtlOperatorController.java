package com.dataviz.etl.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.etl.engine.OperatorFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/etl/operator")
@RequiredArgsConstructor
@Tag(name = "ETL Operator", description = "List operators and get configuration schemas")
// 类级 = 本控制器全部端点至少要 etl:read（PermissionInterceptor 会回落到类上的注解），本控制器纯读，无 write 端点。
@RequiresPermission("etl:read")
public class EtlOperatorController {

    private final OperatorFactory operatorFactory;

    @GetMapping("/list")
    @Operation(summary = "List all available operators")
    public R<List<String>> listOperators() {
        return R.ok(operatorFactory.listOperatorTypes());
    }

    @GetMapping("/{type}/schema")
    @Operation(summary = "Get operator configuration schema")
    public R<Map<String, Object>> getOperatorSchema(@PathVariable("type") String type) {
        return R.ok(operatorFactory.getOperatorSchema(type));
    }
}
