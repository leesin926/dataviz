package com.dataviz.openapi.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ApiProxyRequestDTO {

    /**
     * Application key
     */
    private String appKey;

    /**
     * Application secret
     */
    private String appSecret;

    /**
     * Target API path in gateway-visible shape {@code /api/<service>/<internal path>},
     * e.g. {@code /api/model/internal/dataset/7}. Forwarding happens only when this path hits the
     * {@code openapi.proxy.services.*} allowlist (see OpenApiProxyService / OpenApiProxyProperties).
     */
    private String apiPath;

    /**
     * HTTP method: GET or POST only (POST exists because datasource's internal read-only query
     * endpoint is mapped as POST). Anything else is refused by the proxy. Null defaults to GET.
     */
    private String method;

    /**
     * Request parameters: appended as URL-encoded query string for GET,
     * serialized as the JSON body for POST when {@link #body} is absent.
     */
    private Map<String, Object> params;

    /**
     * Raw JSON request body for POST; takes precedence over params serialization when present.
     */
    private String body;
}
