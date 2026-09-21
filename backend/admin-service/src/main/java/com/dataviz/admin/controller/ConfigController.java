package com.dataviz.admin.controller;

import com.dataviz.admin.dto.ConfigCreateDTO;
import com.dataviz.admin.service.ConfigService;
import com.dataviz.admin.vo.ConfigVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class ConfigController {

    /** 免登可读取的配置键（新增全局开关需在此登记） */
    private static final Set<String> PUBLIC_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("screen.mourning.enabled")));

    private final ConfigService configService;

    @PostMapping
    public R<Long> create(@RequestBody ConfigCreateDTO dto) {
        return R.ok(configService.create(dto));
    }

    @PutMapping
    public R<Void> update(@RequestBody ConfigCreateDTO dto) {
        configService.update(dto);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ConfigVO> getById(@PathVariable Long id) {
        return R.ok(configService.getById(id));
    }

    @DeleteMapping("/{id}")
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
     * 免登读取的系统配置白名单：分享页/uni 端匿名访问时也要拿到全局哀悼模式开关
     */
    @GetMapping("/public/{configKey}")
    public R<String> getPublicByKey(@PathVariable String configKey) {
        if (!PUBLIC_KEYS.contains(configKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Config key is not public: " + configKey);
        }
        return R.ok(configService.getByKey(configKey));
    }
}
