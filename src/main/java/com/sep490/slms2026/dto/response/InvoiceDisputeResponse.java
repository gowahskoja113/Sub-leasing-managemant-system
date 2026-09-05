package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDisputeResponse {
    private Long id;
    private Long invoiceId;
    private String status;
    private String reason;
    private String note;
    private List<String> photos;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
    private Long replacementInvoiceId;
    private String replacementInvoiceCode;
}
