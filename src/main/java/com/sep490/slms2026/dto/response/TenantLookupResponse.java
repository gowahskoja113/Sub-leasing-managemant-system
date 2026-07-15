package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDate;

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
    private LocalDate dateOfBirth;
    private LocalDate cccdIssueDate;
    private String cccdIssuePlace;
    private String role;   // "ROLE_USER" / "ROLE_TENANT" / null — FE dùng để hiển thị hint
    private boolean eligible;
}
