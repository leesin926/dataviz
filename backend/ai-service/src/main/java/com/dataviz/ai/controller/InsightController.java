package com.dataviz.ai.controller;

import com.dataviz.ai.dto.InsightGenerateDTO;
import com.dataviz.ai.service.InsightService;
import com.dataviz.ai.vo.InsightVO;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @PostMapping("/generate")
    public R<InsightVO> generateInsight(@RequestBody InsightGenerateDTO dto) {
        return R.ok(insightService.generateInsight(dto));
    }

    @GetMapping("/list")
    public R<List<InsightVO>> getInsights(@RequestParam(required = false) Long datasourceId,
                                           @RequestParam(required = false) Long datasetId,
                                           @RequestParam(required = false) String insightType) {
        return R.ok(insightService.getInsights(datasourceId, datasetId, insightType));
    }

    @GetMapping("/{id}")
    public R<InsightVO> getInsightDetail(@PathVariable Long id) {
        return R.ok(insightService.getInsightDetail(id));
    }
}
