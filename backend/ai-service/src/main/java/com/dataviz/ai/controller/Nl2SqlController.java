package com.dataviz.ai.controller;

import com.dataviz.ai.dto.Nl2SqlDTO;
import com.dataviz.ai.service.Nl2SqlService;
import com.dataviz.ai.vo.Nl2SqlResultVO;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 自然语言转 SQL。两条端点都会调大模型，所以都记写档 —— 与 ChatController 同一口径。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/nl2sql")
@RequiredArgsConstructor
@RequiresPermission("ai:read")
public class Nl2SqlController {

    private final Nl2SqlService nl2SqlService;

    @PostMapping("/translate")
    @RequiresPermission("ai:write")
    public R<Nl2SqlResultVO> translate(@RequestBody Nl2SqlDTO dto) {
        return R.ok(nl2SqlService.translate(dto));
    }

    @PostMapping("/explain")
    @RequiresPermission("ai:write")
    public R<Nl2SqlResultVO> explain(@RequestBody Nl2SqlDTO dto) {
        return R.ok(nl2SqlService.explain(dto));
    }
}
