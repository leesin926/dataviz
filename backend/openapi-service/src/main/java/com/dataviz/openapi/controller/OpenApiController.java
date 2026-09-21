package com.dataviz.openapi.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.openapi.dto.ApiProxyRequestDTO;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.service.ApiAuthService;
import com.dataviz.openapi.service.ApiLogService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/openapi/proxy")
@RequiredArgsConstructor
public class OpenApiController {

    private final ApiAuthService apiAuthService;
    private final ApiLogService apiLogService;

    /**
     * Proxy endpoint that validates and forwards requests to internal services.
     * All external API calls go through this single endpoint.
     */
    @PostMapping("/invoke")
    public R<Object> proxy(@RequestBody ApiProxyRequestDTO dto, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String clientIp = getClientIp(request);

        // Authenticate
        OpenApiClient client = apiAuthService.authenticate(dto.getAppKey(), dto.getAppSecret(), clientIp);

        Object result = null;
        int responseCode = 200;
        try {
            // Route to internal service based on apiPath
            result = routeRequest(dto, client);
        } catch (Exception e) {
            responseCode = 500;
            log.error("Proxy request failed: apiPath={}, error={}", dto.getApiPath(), e.getMessage());
            return R.fail("Internal error: " + e.getMessage());
        } finally {
            // Log the API call
            long executionTime = System.currentTimeMillis() - startTime;
            OpenApiLog apiLog = OpenApiLog.builder()
                    .clientId(client.getId())
                    .appName(client.getAppName())
                    .apiPath(dto.getApiPath())
                    .method(dto.getMethod())
                    .requestParams(dto.getParams() != null ? dto.getParams().toString() : "")
                    .responseCode(responseCode)
                    .executionTime(executionTime)
                    .ip(clientIp)
                    .createTime(LocalDateTime.now())
                    .build();
            apiLogService.saveLog(apiLog);
        }

        return R.ok(result);
    }

    /**
     * Route the request to the appropriate internal service
     */
    private Object routeRequest(ApiProxyRequestDTO dto, OpenApiClient client) {
        String apiPath = dto.getApiPath();
        log.info("Routing API request: appKey={}, path={}, method={}", client.getAppKey(), apiPath, dto.getMethod());

        // TODO: Implement actual routing to internal services
        // This would typically use a service registry or direct HTTP calls
        // to forward the request to the appropriate microservice
        Map<String, Object> result2 = new HashMap<>();
        result2.put("path", apiPath);
        result2.put("method", dto.getMethod());
        result2.put("message", "Request routed successfully. Internal service integration pending.");
        result2.put("clientApp", client.getAppName());
        return result2;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Take first IP if multiple
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
