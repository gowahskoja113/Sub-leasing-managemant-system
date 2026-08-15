package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String phoneNumber;
    private String fullName;
    private Role role;
    private String avatarUrl;
    private Boolean isFirstLogin;
    private String cccd;
    private UserStatus status;
    private LocalDateTime createAt;
}