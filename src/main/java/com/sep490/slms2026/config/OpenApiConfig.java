package com.sep490.slms2026.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI slmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SLMS 2026 API")
                        .description("Student/Landlord Management System - REST API documentation")
                        .version("v1")
                        .contact(new Contact()
                                .name("SEP490 Team")
                                .email("sep490@example.com")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập JWT token lấy từ POST /api/v1/auth/login (không cần prefix \"Bearer \")")));
    }
}
