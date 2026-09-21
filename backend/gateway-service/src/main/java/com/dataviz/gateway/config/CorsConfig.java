package com.dataviz.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS configuration for the gateway.
 * <p>
 * Configures allowed origins, methods, and headers for cross-origin requests.
 * Since this is a WebFlux application, we use the reactive CorsWebFilter.
 * </p>
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins - in production, restrict to specific domains
        config.addAllowedOriginPattern("*");

        // Allowed HTTP methods
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("OPTIONS");

        // Allowed headers
        config.addAllowedHeader("*");

        // Exposed headers for the client
        config.addExposedHeader("Authorization");
        config.addExposedHeader("X-Request-Id");
        config.addExposedHeader("X-Total-Count");

        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Max age for preflight cache (1 hour)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
