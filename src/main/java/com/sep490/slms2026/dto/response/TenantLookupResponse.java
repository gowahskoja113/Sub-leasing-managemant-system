package com.sep490.slms2026.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantLookupResponse {
    private boolean exists;
    private String fullName;
    private String phoneNumber;
    private String cccd;
}
