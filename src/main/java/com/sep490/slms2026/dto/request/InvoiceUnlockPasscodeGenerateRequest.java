package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.InvoiceUnlockPurpose;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceUnlockPasscodeGenerateRequest {

    @NotNull
    private Long invoiceId;

    @NotNull
    private InvoiceUnlockPurpose purpose;

    private Integer ttlMinutes;
    private String note;
}
