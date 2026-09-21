package com.dataviz.ai.service;

import com.dataviz.ai.vo.ChartRecommendVO;

import java.util.List;
import java.util.Map;

public interface ChartRecommendService {

    List<ChartRecommendVO> recommend(List<String> columns, List<Map<String, Object>> data);
}
