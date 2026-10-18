package com.dataviz.admin.controller;

import com.dataviz.admin.dto.LicenseActivateDTO;
import com.dataviz.admin.service.LicenseService;
import com.dataviz.admin.vo.LicenseVO;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版本授权信息展示与密钥激活（平台级，不区分租户）。
 * <p>原来这三个端点返回的是硬编码的 Map：{@code valid:true} 永远为真，激活只是打一行日志再回
 * {@code success:true}。那种"永远绿"的面板比没有面板更坏 —— 它会让人以为许可证真的在被管控。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/license")
@RequiredArgsConstructor
@Tag(name = "License", description = "平台许可证状态与激活")
@RequiresPermission("platform:read")
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping("/info")
    @Operation(summary = "许可证信息")
    public R<LicenseVO> info() {
        return R.ok(licenseService.current());
    }

    @PostMapping("/activate")
    @RequiresPermission("platform:write")
    @Operation(summary = "激活许可证", description = "验签通过后旧证转历史；密钥本身不落库、不回显")
    public R<LicenseVO> activate(@RequestBody @Valid LicenseActivateDTO dto) {
        return R.ok(licenseService.activate(dto.getLicenseKey()));
    }

    @GetMapping("/validate")
    @Operation(summary = "重新校验许可证", description = "与 info 同源，给前端一个显式复查动作")
    public R<LicenseVO> validate() {
        return R.ok(licenseService.current());
    }
}
