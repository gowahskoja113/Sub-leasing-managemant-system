package com.sep490.slms2026.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*"
    );

    private List<String> allowedMethods = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    private List<String> allowedHeaders = List.of("*");

    private List<String> exposedHeaders = List.of("Authorization");

    private boolean allowCredentials = true;

    private long maxAgeSeconds = 3600L;
}
