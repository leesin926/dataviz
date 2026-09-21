package com.dataviz.openapi.controller;

import com.dataviz.openapi.dto.ApiKeyCreateDTO;
import com.dataviz.openapi.service.ApiClientService;
import com.dataviz.openapi.vo.ApiClientVO;
import com.dataviz.openapi.vo.ApiKeyVO;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/openapi/key")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiClientService apiClientService;

    @PostMapping("/generate")
    public R<ApiKeyVO> generateKey(@RequestBody ApiKeyCreateDTO dto) {
        return R.ok(apiClientService.generateKey(dto));
    }

    @PostMapping("/{clientId}/reset-secret")
    public R<ApiKeyVO> resetSecret(@PathVariable Long clientId) {
        return R.ok(apiClientService.resetSecret(clientId));
    }

    @GetMapping("/clients")
    public R<List<ApiClientVO>> listClients() {
        return R.ok(apiClientService.listClients());
    }

    @PostMapping("/{clientId}/disable")
    public R<Void> disableClient(@PathVariable Long clientId) {
        apiClientService.disableClient(clientId);
        return R.ok();
    }
}
