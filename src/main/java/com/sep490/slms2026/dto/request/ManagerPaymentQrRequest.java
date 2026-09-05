package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.InvoiceUnlockPurpose;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ManagerPaymentQrRequest {

    @NotNull
    private UUID unlockToken;

    @NotNull
    private InvoiceUnlockPurpose purpose;

    private String payerName;
    private String payerPhone;
}
