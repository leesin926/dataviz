package com.dataviz.alert.service;

import com.dataviz.alert.dto.NotifyChannelDTO;
import com.dataviz.alert.vo.ChannelSchemaVO;
import com.dataviz.alert.vo.ChannelTestVO;
import com.dataviz.alert.vo.NotifyChannelVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

import java.util.List;

/** 通知渠道配置（管理端配置页的唯一后端入口） */
public interface NotifyChannelService {

    /** 当前进程注册在案的渠道类型 + 各自的配置项声明，配置页据此渲染表单 */
    List<ChannelSchemaVO> schemas();

    PageResult<NotifyChannelVO> page(PageQuery pageQuery, String keyword, String type);

    NotifyChannelVO detail(Long id);

    Long create(NotifyChannelDTO dto);

    void update(NotifyChannelDTO dto);

    void delete(Long id);

    void setEnabled(Long id, boolean enabled);

    /**
     * 用一条合成消息实测该渠道，结果（含失败原因）作为数据回，不抛异常。
     *
     * @param recipients 临时收件人（逗号/空格/换行分隔）。收件人已经归到规则侧之后，
     *                   配置页这一条测试<b>没有规则可解析</b>，所以要么在这里给一个，
     *                   要么就只能撞渠道里存量的历史收件人。留空表示"用渠道里存量那份试"。
     */
    ChannelTestVO test(Long id, String recipients);
}
