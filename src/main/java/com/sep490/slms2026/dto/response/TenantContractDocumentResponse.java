package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantContractDocumentResponse {

    private Long contractId;
    private String contractCode;
    private String documentUrl;
    private LocalDateTime documentGeneratedAt;
    private Boolean effective;
    private String effectiveLabel;
}
