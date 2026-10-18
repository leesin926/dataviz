package com.dataviz.ai.controller;

import com.dataviz.ai.service.ChartRecommendService;
import com.dataviz.ai.vo.ChartRecommendVO;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 图表推荐：对调用方已经拿到的列与样本做纯函数推断，不落库、不调模型，所以只有读档。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/recommend")
@RequiredArgsConstructor
@RequiresPermission("ai:read")
public class RecommendController {

    private final ChartRecommendService chartRecommendService;

    @PostMapping("/chart")
    public List<ChartRecommendVO> recommend(
            @RequestParam List<String> columns,
            @RequestBody List<Map<String, Object>> data) {
        return chartRecommendService.recommend(columns, data);
    }
}
