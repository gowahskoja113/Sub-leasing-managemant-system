package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterOverrideVerifyRequest {

    @NotBlank
    private String passcode;

    @NotNull
    private Long contractId;

    /** ELEC | WATER */
    @NotBlank
    private String meterKind;
}
