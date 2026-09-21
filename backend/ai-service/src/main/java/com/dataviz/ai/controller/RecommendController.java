package com.dataviz.ai.controller;

import com.dataviz.ai.service.ChartRecommendService;
import com.dataviz.ai.vo.ChartRecommendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final ChartRecommendService chartRecommendService;

    @PostMapping("/chart")
    public List<ChartRecommendVO> recommend(
            @RequestParam List<String> columns,
            @RequestBody List<Map<String, Object>> data) {
        return chartRecommendService.recommend(columns, data);
    }
}
