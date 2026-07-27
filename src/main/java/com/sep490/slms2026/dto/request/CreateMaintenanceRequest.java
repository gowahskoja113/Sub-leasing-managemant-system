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
    private String description; // optional
    /** Bắt buộc khi không có equipmentId: STRUCTURAL | ELECTRICAL | PLUMBING | OTHER */
    private String category;
    private List<String> images; // Cloudinary URLs
}
