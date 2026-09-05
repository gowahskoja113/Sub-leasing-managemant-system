package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MaintenanceCreateRequest {
    private Long roomId;
    private Long propertyId;
    private Long equipmentId;
    private Long previousRequestId;
    private String title;
    private String description;
    /** APPLIANCE | FURNITURE | PLUMBING | ELECTRICAL */
    private String category;
    private List<String> images;
    /** Bắt buộc — lịch hẹn manager tới xem (giờ hành chính, không trùng). */
    private LocalDateTime visitAppointmentAt;
}
