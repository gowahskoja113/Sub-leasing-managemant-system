package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TenantCreationRequest {

    private String username;
    private String password;
    private String phoneNumber;

    private String fullName;
    private String citizenIdNumber;

    private UUID roomId;
    private UUID propertyId;
}