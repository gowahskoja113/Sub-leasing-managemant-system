package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceUnlockVerifyRequest {

    @NotNull
    private Long invoiceId;

    @NotBlank
    private String passcode;
}
