package com.sep490.slms2026.dto.request;

import lombok.Data;
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
    /** Tiêu đề sự cố — hiển thị trên list/detail cho tenant và manager */
    private String title;
    private String description;
    private List<String> images;
}
