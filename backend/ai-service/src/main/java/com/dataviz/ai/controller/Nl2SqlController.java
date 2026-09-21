package com.dataviz.ai.controller;

import com.dataviz.ai.dto.Nl2SqlDTO;
import com.dataviz.ai.service.Nl2SqlService;
import com.dataviz.ai.vo.Nl2SqlResultVO;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai/nl2sql")
@RequiredArgsConstructor
public class Nl2SqlController {

    private final Nl2SqlService nl2SqlService;

    @PostMapping("/translate")
    public R<Nl2SqlResultVO> translate(@RequestBody Nl2SqlDTO dto) {
        return R.ok(nl2SqlService.translate(dto));
    }

    @PostMapping("/explain")
    public R<Nl2SqlResultVO> explain(@RequestBody Nl2SqlDTO dto) {
        return R.ok(nl2SqlService.explain(dto));
    }
}
