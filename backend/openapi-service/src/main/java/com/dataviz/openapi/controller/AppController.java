package com.dataviz.openapi.controller;

import com.dataviz.openapi.dto.AppCreateDTO;
import com.dataviz.openapi.service.OpenApiAppService;
import com.dataviz.openapi.vo.AppVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/openapi/app")
@RequiredArgsConstructor
public class AppController {

    private final OpenApiAppService openApiAppService;

    @PostMapping
    public Long create(@RequestBody AppCreateDTO dto) {
        return openApiAppService.create(dto);
    }

    @DeleteMapping("/{id}")
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
    public AppVO regenerateSecret(@PathVariable Long id) {
        return openApiAppService.regenerateSecret(id);
    }
}
