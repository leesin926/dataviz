package com.dataviz.screen.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.screen.dto.ScreenUpsertDTO;
import com.dataviz.screen.service.ScreenService;
import com.dataviz.screen.vo.ScreenListVO;
import com.dataviz.screen.vo.ScreenVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/screen")
@RequiredArgsConstructor
public class ScreenController {

    private final ScreenService screenService;

    @PostMapping
    public R<ScreenVO> create(@RequestBody ScreenUpsertDTO dto) {
        return R.ok(screenService.create(dto));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody ScreenUpsertDTO dto) {
        screenService.update(id, dto);
        return R.ok();
    }

    /** platform=mobile|tablet 时返回该端变体展平后的配置（uni 端播放器使用） */
    @GetMapping("/{id}")
    public R<ScreenVO> getById(@PathVariable Long id,
                               @RequestParam(required = false) String platform) {
        return R.ok(screenService.getById(id, platform));
    }

    /** 免登录分享读取（网关与服务层均已放行 /api/screen/share/**），仅已发布大屏可见 */
    @GetMapping("/share/{token}")
    public R<ScreenVO> getByShareToken(@PathVariable String token,
                                       @RequestParam(required = false) String platform) {
        return R.ok(screenService.getByShareToken(token, platform));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        screenService.delete(id);
        return R.ok();
    }

    @GetMapping("/list")
    public R<ScreenListVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(screenService.list(keyword, status, pageNum, pageSize));
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        screenService.publish(id);
        return R.ok();
    }

    @PostMapping("/{id}/unpublish")
    public R<Void> unpublish(@PathVariable Long id) {
        screenService.unpublish(id);
        return R.ok();
    }

    @PostMapping("/{id}/clone")
    public R<ScreenVO> clone(@PathVariable Long id,
                             @RequestBody(required = false) Map<String, Object> body) {
        Object name = body == null ? null : body.get("name");
        return R.ok(screenService.clone(id, name instanceof String ? (String) name : null));
    }

    @PostMapping("/{id}/share")
    public R<String> share(@PathVariable Long id) {
        return R.ok(screenService.share(id));
    }

    /** 按端保存配置变体，body 为整份变体 {width,height,config,components,layers?} */
    @PutMapping("/{id}/variant")
    public R<Void> saveVariant(@PathVariable Long id,
                               @RequestParam String platform,
                               @RequestBody Map<String, Object> variant) {
        screenService.saveVariant(id, platform, variant);
        return R.ok();
    }
}
