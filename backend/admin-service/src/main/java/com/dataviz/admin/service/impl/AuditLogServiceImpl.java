package com.dataviz.admin.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.admin.entity.AuditLog;
import com.dataviz.admin.mapper.AuditLogMapper;
import com.dataviz.admin.service.AuditLogService;
import com.dataviz.admin.vo.AuditLogVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void saveLog(AuditLog auditLog) {
        auditLogMapper.insert(auditLog);
    }

    @Override
    public PageResult<AuditLogVO> getLogs(PageQuery pageQuery, String username, String module, String action) {
        Page<AuditLog> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.eq(AuditLog::getUsername, username);
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(AuditLog::getModule, module);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(AuditLog::getAction, action);
        }
        wrapper.orderByDesc(AuditLog::getCreateTime);
        Page<AuditLog> result = auditLogMapper.selectPage(page, wrapper);
        List<AuditLogVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public AuditLogVO getDetail(Long id) {
        AuditLog auditLog = auditLogMapper.selectById(id);
        if (auditLog == null) {
            throw new BizException("Audit log not found: " + id);
        }
        return toVO(auditLog);
    }

    @Override
    public void exportToExcel(HttpServletResponse response, String username, String module, String action) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("audit_log_export", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            // Build query without pagination
            LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(username)) {
                wrapper.eq(AuditLog::getUsername, username);
            }
            if (StringUtils.hasText(module)) {
                wrapper.eq(AuditLog::getModule, module);
            }
            if (StringUtils.hasText(action)) {
                wrapper.eq(AuditLog::getAction, action);
            }
            wrapper.orderByDesc(AuditLog::getCreateTime);
            // Limit to 10000 rows for export
            wrapper.last("LIMIT 10000");

            List<AuditLog> logs = auditLogMapper.selectList(wrapper);
            List<AuditLogVO> voList = logs.stream().map(this::toVO).collect(Collectors.toList());

            EasyExcel.write(response.getOutputStream(), AuditLogVO.class)
                    .sheet("Audit Logs")
                    .doWrite(voList);
            log.info("Exported {} audit log records", voList.size());
        } catch (Exception e) {
            log.error("Failed to export audit logs", e);
            throw new BizException("Failed to export audit logs: " + e.getMessage());
        }
    }

    private AuditLogVO toVO(AuditLog auditLog) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(auditLog, vo);
        return vo;
    }
}
