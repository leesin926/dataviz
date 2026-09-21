package com.dataviz.openapi.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.openapi.entity.OpenApiApp;
import com.dataviz.openapi.mapper.OpenApiAppMapper;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private final OpenApiAppMapper openApiAppMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip authentication for non-openapi paths
        if (!path.startsWith("/api/openapi/callback")) {
            filterChain.doFilter(request, response);
            return;
        }

        String appKey = request.getHeader("X-App-Key");
        String signature = request.getHeader("X-Signature");

        if (appKey == null || signature == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Missing AppKey or Signature\"}");
            return;
        }

        LambdaQueryWrapper<OpenApiApp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenApiApp::getAppKey, appKey)
               .eq(OpenApiApp::getStatus, 1);
        OpenApiApp app = openApiAppMapper.selectOne(wrapper);

        if (app == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid AppKey\"}");
            return;
        }

        // Verify signature using appSecret
        if (!verifySignature(signature, app.getAppSecret(), request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid Signature\"}");
            return;
        }

        // Check rate limit
        // TODO: Implement rate limiting with Redis

        // Check IP whitelist
        if (app.getIpWhitelist() != null && !app.getIpWhitelist().isEmpty()) {
            String clientIp = getClientIp(request);
            if (!app.getIpWhitelist().contains(clientIp)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"IP not in whitelist\"}");
                return;
            }
        }

        request.setAttribute("appId", app.getId());
        request.setAttribute("tenantId", app.getTenantId());
        filterChain.doFilter(request, response);
    }

    private boolean verifySignature(String signature, String appSecret, HttpServletRequest request) {
        // TODO: Implement HMAC signature verification
        // String body = readBody(request);
        // String expectedSignature = HmacSHA256(body, appSecret);
        // return expectedSignature.equals(signature);
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/openapi/callback");
    }
}
