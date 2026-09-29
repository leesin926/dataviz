package com.dataviz.alert.service;

import com.dataviz.alert.dto.AlertContactDTO;
import com.dataviz.alert.vo.AlertContactVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

import java.util.List;

public interface AlertContactService {

    Long create(AlertContactDTO dto);

    void update(AlertContactDTO dto);

    AlertContactVO detail(Long id);

    /** 仍被通知组含着时拒绝删除（400 并点名是哪几个组） */
    void delete(Long id);

    void setEnabled(Long id, boolean enabled);

    PageResult<AlertContactVO> page(PageQuery pageQuery, String keyword);

    /** 通知组成员选择器用的全量选项（含停用，界面上自己标灰） */
    List<AlertContactVO> options();
}
