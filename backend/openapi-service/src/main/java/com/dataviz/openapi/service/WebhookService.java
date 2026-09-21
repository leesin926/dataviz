package com.dataviz.openapi.service;

import com.dataviz.openapi.dto.WebhookCreateDTO;
import com.dataviz.openapi.vo.WebhookVO;

import java.util.List;

public interface WebhookService {

    Long create(WebhookCreateDTO dto);

    void update(WebhookCreateDTO dto);

    void delete(Long id);

    WebhookVO getById(Long id);

    List<WebhookVO> listByApp(Long appId);
}
