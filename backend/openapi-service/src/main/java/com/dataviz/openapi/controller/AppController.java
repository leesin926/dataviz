package com.dataviz.openapi.controller;

import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.openapi.dto.AppCreateDTO;
import com.dataviz.openapi.service.OpenApiAppService;
import com.dataviz.openapi.vo.AppVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 开放应用管理（与 {@code ApiKeyController} 同属对外凭据面）。
 * <p>{@code /list?tenantId=} 的主体取自入参而不是会话，属于 API-40 那一族跨租户读的问题；
 * 本轮只上码（把面收给 openapi:read），不收入参 —— 收口径要连带定"管理端到底给不给看别的租户"，
 * 那是决策不是 bug 修复。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/openapi/app")
@RequiredArgsConstructor
@RequiresPermission("openapi:read")
public class AppController {

    private final OpenApiAppService openApiAppService;

    @PostMapping
    @RequiresPermission("openapi:write")
    public Long create(@RequestBody AppCreateDTO dto) {
        return openApiAppService.create(dto);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("openapi:write")
    public void delete(@PathVariable Long id) {
        openApiAppService.delete(id);
    }

    @GetMapping("/{id}")
    public AppVO getById(@PathVariable Long id) {
        return openApiAppService.getById(id);
    }

    @GetMapping("/list")
    public List<AppVO> list(@RequestParam(required = false) Long tenantId) {
        return openApiAppService.list(tenantId);
    }

    @PostMapping("/{id}/regenerate-secret")
    @RequiresPermission("openapi:write")
    public AppVO regenerateSecret(@PathVariable Long id) {
        return openApiAppService.regenerateSecret(id);
    }
}
