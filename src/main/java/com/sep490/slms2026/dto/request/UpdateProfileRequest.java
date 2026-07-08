package com.sep490.slms2026.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String email;
    private String avatarUrl;
}
