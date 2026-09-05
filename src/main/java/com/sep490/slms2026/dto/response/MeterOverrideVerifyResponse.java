package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterOverrideVerifyResponse {
    private boolean valid;
    private UUID overrideToken;
    private LocalDateTime expiresAt;
    private String message;
}
