package com.sep490.slms2026.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final PropertyImageUploadProperties uploadProperties;
    private final ContractDocumentUploadProperties contractDocumentProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path baseDir = Path.of(uploadProperties.getDir()).toAbsolutePath().normalize();
        String location = baseDir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/properties/**")
                .addResourceLocations(location);

        Path contractDir = Path.of(contractDocumentProperties.getDir()).toAbsolutePath().normalize();
        String contractLocation = contractDir.toUri().toString();
        if (!contractLocation.endsWith("/")) {
            contractLocation += "/";
        }
        registry.addResourceHandler("/uploads/contracts/**")
                .addResourceLocations(contractLocation);
    }
}
