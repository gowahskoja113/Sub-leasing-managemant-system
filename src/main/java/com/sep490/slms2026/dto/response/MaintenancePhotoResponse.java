package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class MaintenancePhotoResponse {
    private UUID id;
    private String photoUrl;
    private String photoType;   // BEFORE | AFTER
    private LocalDateTime uploadedAt;
}