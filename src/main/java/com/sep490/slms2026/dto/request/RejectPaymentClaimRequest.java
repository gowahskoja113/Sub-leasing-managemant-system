package com.sep490.slms2026.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectPaymentClaimRequest {
    private String reason;
}
