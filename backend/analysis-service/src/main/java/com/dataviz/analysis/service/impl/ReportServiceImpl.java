package com.dataviz.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.dto.ReportCreateDTO;
import com.dataviz.analysis.dto.ReportUpdateDTO;
import com.dataviz.analysis.entity.AnalysisReport;
import com.dataviz.analysis.engine.QueryEngine;
import com.dataviz.analysis.mapper.ReportMapper;
import com.dataviz.analysis.service.ReportService;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.analysis.vo.ReportDataVO;
import com.dataviz.analysis.vo.ReportVO;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报告服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final QueryEngine queryEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReport(ReportCreateDTO dto, String tenantId) {
        AnalysisReport report = new AnalysisReport();
        report.setName(dto.getName());
        report.setDescription(dto.getDescription());
        report.setDatasourceId(dto.getDatasourceId());
        report.setDatasetId(dto.getDatasetId());
        report.setConfig(dto.getConfig());
        report.setIsPublished(0);

        reportMapper.insert(report);
        log.info("Report created: id={}, name={}", report.getId(), report.getName());
        return report.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReport(Long id, ReportUpdateDTO dto) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new RuntimeException("Report not found: " + id);
        }

        if (dto.getName() != null) report.setName(dto.getName());
        if (dto.getDescription() != null) report.setDescription(dto.getDescription());
        if (dto.getDatasourceId() != null) report.setDatasourceId(dto.getDatasourceId());
        if (dto.getDatasetId() != null) report.setDatasetId(dto.getDatasetId());
        if (dto.getConfig() != null) report.setConfig(dto.getConfig());

        reportMapper.updateById(report);
        log.info("Report updated: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new RuntimeException("Report not found: " + id);
        }
        reportMapper.deleteById(id);
        log.info("Report deleted: id={}", id);
    }

    @Override
    public ReportVO getReportById(Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new RuntimeException("Report not found: " + id);
        }
        return convertToVO(report);
    }

    @Override
    public PageResult<ReportVO> listReports(String tenantId, PageQuery pageQuery) {
        Page<AnalysisReport> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<AnalysisReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AnalysisReport::getCreateTime);
        Page<AnalysisReport> result = reportMapper.selectPage(page, wrapper);

        List<ReportVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishReport(Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new RuntimeException("Report not found: " + id);
        }
        report.setIsPublished(1);
        reportMapper.updateById(report);
        log.info("Report published: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublishReport(Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new RuntimeException("Report not found: " + id);
        }
        report.setIsPublished(0);
        reportMapper.updateById(report);
        log.info("Report unpublished: id={}", id);
    }

    @Override
    public ReportDataVO getReportData(Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new RuntimeException("Report not found: " + id);
        }

        ReportDataVO dataVO = new ReportDataVO();
        dataVO.setReport(convertToVO(report));

        // Execute the query to get report data
        // In production, parse the report config to build AnalysisQueryDTO
        if (report.getDatasetId() != null) {
            AnalysisQueryDTO queryDTO = new AnalysisQueryDTO();
            queryDTO.setDatasetId(report.getDatasetId());
            // Config would contain dimensions, metrics, filters etc.
            // Parse from report.getConfig() in production

            QueryResultVO queryResult = queryEngine.execute(queryDTO, null, null);

            dataVO.setColumns(queryResult.getColumns());
            dataVO.setRows(queryResult.getRows());
            dataVO.setRowCount(queryResult.getRowCount());
        } else {
            dataVO.setColumns(new ArrayList<>());
            dataVO.setRows(new ArrayList<>());
            dataVO.setRowCount(0);
        }

        return dataVO;
    }

    private ReportVO convertToVO(AnalysisReport report) {
        ReportVO vo = new ReportVO();
        BeanUtils.copyProperties(report, vo);
        return vo;
    }

}
