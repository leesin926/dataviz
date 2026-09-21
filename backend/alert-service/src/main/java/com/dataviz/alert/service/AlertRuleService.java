package com.dataviz.alert.service;

import com.dataviz.alert.dto.AlertRuleCreateDTO;
import com.dataviz.alert.dto.AlertRuleUpdateDTO;
import com.dataviz.alert.vo.AlertRuleTestVO;
import com.dataviz.alert.vo.AlertRuleVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

public interface AlertRuleService {

    Long create(AlertRuleCreateDTO dto);

    void update(AlertRuleUpdateDTO dto);

    AlertRuleVO getById(Long id);

    void delete(Long id);

    PageResult<AlertRuleVO> page(PageQuery pageQuery, String keyword, String type, String severity);

    void enable(Long id);

    void disable(Long id);

    /** 试跑：取一次指标值并判定条件，不建事件、不发通知 */
    AlertRuleTestVO testRule(Long id);
}
