package com.dataviz.openapi.service;

import com.dataviz.openapi.entity.OpenApiClient;

/**
 * Validates appKey+appSecret signature, rate limiting check, IP whitelist check
 */
public interface ApiAuthService {

    /**
     * Authenticate the API request by validating appKey and appSecret
     * @return the authenticated client
     */
    OpenApiClient authenticate(String appKey, String appSecret, String clientIp);

    /**
     * Check rate limit for the client
     * @return true if the request is allowed
     */
    boolean checkRateLimit(OpenApiClient client);

    /**
     * Check if the client IP is in the allowed list
     * @return true if the IP is allowed
     */
    boolean checkIpWhitelist(OpenApiClient client, String clientIp);
}
