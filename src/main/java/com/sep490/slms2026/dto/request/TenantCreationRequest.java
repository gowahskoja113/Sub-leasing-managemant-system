package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class TenantCreationRequest {

    private String username;
    private String password;
    private String phoneNumber;

    private String fullName;
    private String citizenIdNumber;

    private Long roomId;
    private Long propertyId;
}