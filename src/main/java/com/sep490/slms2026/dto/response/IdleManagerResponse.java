package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class IdleManagerResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String phoneNumber;
    private UserStatus status;
    private long zoneCount;
}
