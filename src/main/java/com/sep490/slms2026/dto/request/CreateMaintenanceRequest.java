package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateMaintenanceRequest {
    private Long roomId;
    private Long equipmentId;  // nullable — từ QR scan
    private String title;
    private String description;
    private List<String> images; // Cloudinary URLs
}
