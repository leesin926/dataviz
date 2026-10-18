package com.dataviz.openapi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方回调入口。三条端点本轮<b>不上用户权限码</b>（R1 的 B 档）：
 * 它们的调用方是外部系统，手上没有用户会话，用 {@code openapi:*} 去判等于用错尺子。
 * <p>真正卡在这里的是一个待拍板的口径冲突：{@code OpenApiAuthFilter} 已经对
 * {@code /api/openapi/callback} 做了 HMAC 验签 + nonce + IP 白名单 + 限流，但这条路径
 * 不在网关与 {@code AuthInterceptor} 的免登名单里 —— 于是签名过了仍会在一道 JWT 检查上 401，
 * 外部集成实际上打不通。要么把 callback 加进两处白名单（认证交给 HMAC，与分享链接同构），
 * 要么承认这三条只是占位、等真实业务再定。三个方法体现在也都只回显"收到"，没有副作用，
 * 所以维持现状不会造成数据风险，只是"看起来有回调、其实没人接得住"。</p>
 */
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
