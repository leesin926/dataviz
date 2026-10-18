package com.dataviz.openapi.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.openapi.dto.ApiKeyCreateDTO;
import com.dataviz.openapi.service.ApiClientService;
import com.dataviz.openapi.vo.ApiClientVO;
import com.dataviz.openapi.vo.ApiKeyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对外 API 凭据管理。
 * <p>生成/重置口令这类动作在读写两档粒度下只能落在 {@code openapi:write}：拿到它等于能给自己铸一把
 * 通往内部服务的钥匙。这是两档粒度的已知代价（见 2026-09-22 码表补丁 ①②），
 * 要"能看列表但不能铸钥匙"以外的粒度得走五码。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/openapi/key")
@RequiredArgsConstructor
@RequiresPermission("openapi:read")
public class ApiKeyController {

    private final ApiClientService apiClientService;

    @PostMapping("/generate")
    @RequiresPermission("openapi:write")
    public R<ApiKeyVO> generateKey(@RequestBody ApiKeyCreateDTO dto) {
        return R.ok(apiClientService.generateKey(dto));
    }

    @PostMapping("/{clientId}/reset-secret")
    @RequiresPermission("openapi:write")
    public R<ApiKeyVO> resetSecret(@PathVariable Long clientId) {
        return R.ok(apiClientService.resetSecret(clientId));
    }

    @GetMapping("/clients")
    public R<List<ApiClientVO>> listClients() {
        return R.ok(apiClientService.listClients());
    }

    @PostMapping("/{clientId}/disable")
    @RequiresPermission("openapi:write")
    public R<Void> disableClient(@PathVariable Long clientId) {
        apiClientService.disableClient(clientId);
        return R.ok();
    }
}
