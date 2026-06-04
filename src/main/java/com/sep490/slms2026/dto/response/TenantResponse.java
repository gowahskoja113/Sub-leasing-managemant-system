package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TenantResponse {

    private UUID id;
    private String username;
    private String phoneNumber;
    private UserStatus status;

    private String fullName;
    private String citizenIdNumber;
    private String roomRentalStatus;
}