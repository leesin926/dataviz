package com.dataviz.openapi.service;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.openapi.dto.OpenApiLogQueryDTO;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.vo.OpenApiLogVO;

/**
 * Logs all API calls and provides stats
 */
public interface ApiLogService {

    /**
     * Save an API call log
     */
    void saveLog(OpenApiLog log);

    /**
     * 管理端读路径：分页查 openapi_log，只回可安全展示的列。
     *
     * <p>口径见 {@code ApiLogServiceImpl#pageLogs}：租户靠父表 {@code openapi_client} 收口，
     * 永不回 appSecret / requestParams；打开分片后跨月扇出，结果不完整时必须在响应里说出来。
     */
    PageResult<OpenApiLogVO> pageLogs(OpenApiLogQueryDTO query);
}
