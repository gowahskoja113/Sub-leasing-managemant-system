package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthMeResponse {
    private String id;
    private String username;
    private String fullName;
    private String phone;
    private String email;
    private String role;
    private String avatarUrl;
    private boolean isFirstLogin;
}
