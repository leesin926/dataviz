package com.dataviz.openapi.service;

import com.dataviz.openapi.dto.ApiKeyCreateDTO;
import com.dataviz.openapi.vo.ApiClientVO;
import com.dataviz.openapi.vo.ApiKeyVO;

import java.util.List;

/**
 * CRUD for API clients, key generation (UUID), secret generation
 */
public interface ApiClientService {

    /**
     * Generate a new API key pair (appKey + appSecret)
     */
    ApiKeyVO generateKey(ApiKeyCreateDTO dto);

    /**
     * Reset the appSecret for an existing client
     */
    ApiKeyVO resetSecret(Long clientId);

    /**
     * List all API clients
     */
    List<ApiClientVO> listClients();

    /**
     * Disable an API client
     */
    void disableClient(Long clientId);
}
