package com.dataviz.openapi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/openapi/callback")
@RequiredArgsConstructor
public class CallbackController {

    @PostMapping("/datasource/{datasourceId}")
    public Map<String, Object> datasourceCallback(
            @PathVariable Long datasourceId,
            @RequestBody Map<String, Object> payload) {
        log.info("Received datasource callback: datasourceId={}, payload={}", datasourceId, payload);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Callback received");
        return result;
    }

    @PostMapping("/auth/callback")
    public Map<String, Object> authCallback(@RequestParam Map<String, String> params) {
        log.info("Received auth callback: params={}", params);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Auth callback received");
        return result;
    }

    @PostMapping("/webhook/test")
    public Map<String, Object> testWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Test webhook received: payload={}", payload);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Webhook test successful");
        return result;
    }
}
