package com.dataviz.openapi.controller;

import com.dataviz.openapi.dto.WebhookCreateDTO;
import com.dataviz.openapi.service.WebhookService;
import com.dataviz.openapi.vo.WebhookVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/openapi/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    public Long create(@RequestBody WebhookCreateDTO dto) {
        return webhookService.create(dto);
    }

    @PutMapping
    public void update(@RequestBody WebhookCreateDTO dto) {
        webhookService.update(dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        webhookService.delete(id);
    }

    @GetMapping("/{id}")
    public WebhookVO getById(@PathVariable Long id) {
        return webhookService.getById(id);
    }

    @GetMapping("/list/{appId}")
    public List<WebhookVO> listByApp(@PathVariable Long appId) {
        return webhookService.listByApp(appId);
    }
}
