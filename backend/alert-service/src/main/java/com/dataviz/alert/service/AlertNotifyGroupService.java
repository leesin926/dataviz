package com.dataviz.alert.service;

import com.dataviz.alert.dto.AlertNotifyGroupDTO;
import com.dataviz.alert.vo.AlertNotifyGroupVO;
import com.dataviz.alert.vo.NotifyGroupOptionVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AlertNotifyGroupService {

    Long create(AlertNotifyGroupDTO dto);

    void update(AlertNotifyGroupDTO dto);

    AlertNotifyGroupVO detail(Long id);

    /** 仍被规则引用时拒绝删除（400 并点名是哪几条规则） */
    void delete(Long id);

    void setEnabled(Long id, boolean enabled);

    PageResult<AlertNotifyGroupVO> page(PageQuery pageQuery, String keyword);

    /** 规则表单用的选项：带每组的邮箱数/手机数，让人在勾选当场就能判断"这组收得到这条渠道吗" */
    List<NotifyGroupOptionVO> options();

    /**
     * 按 id 取同样形状的选项，给规则列表/详情用（一次查完，不给每行规则一次查询）。
     * 查不到的 id 不会出现在 map 里 —— 组被删掉过的历史引用，界面不该编造一个名字出来。
     */
    Map<Long, NotifyGroupOptionVO> optionsById(Collection<Long> groupIds);
}
