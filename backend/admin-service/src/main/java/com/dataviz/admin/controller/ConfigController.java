package com.dataviz.admin.controller;

import com.dataviz.admin.config.PublicConfigKeys;
import com.dataviz.admin.dto.ConfigCreateDTO;
import com.dataviz.admin.service.ConfigService;
import com.dataviz.admin.vo.ConfigVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 platform:read（PermissionInterceptor 会回落到类上的注解），改配置的动作抬到 platform:write；
// getPublicByKey（/public/**）是刻意免登端点，保持不加注解——该路径已在 SecurityConfig 的 exclude 列表里，拦截器根本不会走到它。
@RequiresPermission("platform:read")
public class ConfigController {

    private final ConfigService configService;

    @PostMapping
    @RequiresPermission("platform:write")
    public R<Long> create(@RequestBody ConfigCreateDTO dto) {
        return R.ok(configService.create(dto));
    }

    @PutMapping
    @RequiresPermission("platform:write")
    public R<Void> update(@RequestBody ConfigCreateDTO dto) {
        configService.update(dto);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ConfigVO> getById(@PathVariable Long id) {
        return R.ok(configService.getById(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("platform:write")
    public R<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    public R<PageResult<ConfigVO>> page(PageQuery pageQuery,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String configType) {
        return R.ok(configService.page(pageQuery, keyword, configType));
    }

    @GetMapping("/key/{configKey}")
    public R<String> getByKey(@PathVariable String configKey) {
        return R.ok(configService.getByKey(configKey));
    }

    /**
     * 免登读取的系统配置白名单：分享页/uni 端匿名访问时也要拿到全局哀悼模式开关。
     * 键的唯一登记处是 {@link PublicConfigKeys}——新增全局开关只改那一处，推送通道读的是同一份。
     */
    @GetMapping("/public/{configKey}")
    public R<String> getPublicByKey(@PathVariable String configKey) {
        if (!PublicConfigKeys.isPublic(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Config key is not public: " + configKey);
        }
        return R.ok(configService.getByKey(configKey));
    }
}
