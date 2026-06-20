package com.sep490.slms2026.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload.property-images")
public class PropertyImageUploadProperties {

    /** Thư mục lưu file ảnh trên disk (relative hoặc absolute). */
    private String dir = "uploads/properties";

    /** URL gốc BE (không slash cuối), dùng ghép public URL ảnh. */
    private String publicBaseUrl = "http://localhost:8080";
}
