package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MaintenanceCreateRequest {
    /**
     * ID phòng — bắt buộc với HĐ thuê theo phòng (ROOM).
     * Với HĐ nguyên căn (WHOLE_HOUSE) có thể bỏ trống / null; BE lấy property từ HĐ ACTIVE.
     */
    private Long roomId;
    /** Optional — báo hỏng không gắn thiết bị thì để null. */
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
