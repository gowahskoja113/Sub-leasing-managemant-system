package com.sep490.slms2026.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload.contract-documents")
public class ContractDocumentUploadProperties {

    /** Thư mục lưu file DOCX trên disk. */
    private String dir = "uploads/contracts";

    /** URL gốc BE (không slash cuối), dùng ghép public URL. */
    private String publicBaseUrl = "http://localhost:8080";

    /** Classpath tới template Word duy nhất (placeholder ${...}). */
    private String templateClasspath = "templates/contract/tenant-apartment-draft-template.docx";
}
