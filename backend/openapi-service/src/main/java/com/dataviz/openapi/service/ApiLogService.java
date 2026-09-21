package com.dataviz.openapi.service;

import com.dataviz.openapi.entity.OpenApiLog;

/**
 * Logs all API calls and provides stats
 */
public interface ApiLogService {

    /**
     * Save an API call log
     */
    void saveLog(OpenApiLog log);

    /**
     * Get API call statistics
     */
    long getClientCallCount(Long clientId);
}
