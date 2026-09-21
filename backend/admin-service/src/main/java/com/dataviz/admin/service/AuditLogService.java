package com.dataviz.admin.service;

import com.dataviz.admin.entity.AuditLog;
import com.dataviz.admin.vo.AuditLogVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

import javax.servlet.http.HttpServletResponse;

/**
 * Save log, get logs with pagination + filters, export to Excel
 */
public interface AuditLogService {

    /**
     * Save an audit log
     */
    void saveLog(AuditLog log);

    /**
     * Get logs with pagination and filters
     */
    PageResult<AuditLogVO> getLogs(PageQuery pageQuery, String username, String module, String action);

    /**
     * Get log detail
     */
    AuditLogVO getDetail(Long id);

    /**
     * Export logs to Excel
     */
    void exportToExcel(HttpServletResponse response, String username, String module, String action);
}
