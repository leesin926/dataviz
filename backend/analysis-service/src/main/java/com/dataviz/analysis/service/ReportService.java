package com.dataviz.analysis.service;

import com.dataviz.analysis.dto.ReportCreateDTO;
import com.dataviz.analysis.dto.ReportUpdateDTO;
import com.dataviz.analysis.vo.ReportDataVO;
import com.dataviz.analysis.vo.ReportVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

/**
 * 报告服务接口
 */
public interface ReportService {

    /**
     * 创建报告
     */
    Long createReport(ReportCreateDTO dto, String tenantId);

    /**
     * 更新报告
     */
    void updateReport(Long id, ReportUpdateDTO dto);

    /**
     * 删除报告
     */
    void deleteReport(Long id);

    /**
     * 获取报告详情
     */
    ReportVO getReportById(Long id);

    /**
     * 分页查询报告列表
     */
    PageResult<ReportVO> listReports(String tenantId, PageQuery pageQuery);

    /**
     * 发布报告
     */
    void publishReport(Long id);

    /**
     * 取消发布报告
     */
    void unpublishReport(Long id);

    /**
     * 获取报告数据(执行底层查询并返回数据)
     */
    ReportDataVO getReportData(Long id);
}
