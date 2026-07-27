package com.sep490.slms2026.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class MaintenanceCreateRequest {
    private Long roomId;
    private Long equipmentId;
    /** Tiêu đề sự cố — hiển thị trên list/detail cho tenant và manager */
    private String title;
    /** Tùy chọn — mô tả chi tiết (không bắt buộc) */
    private String description;
    /**
     * Bắt buộc khi không chọn {@code equipmentId} (hư hao không phải trang thiết bị/nội thất).
     * Giá trị: {@code STRUCTURAL} | {@code ELECTRICAL} | {@code PLUMBING} | {@code OTHER}.
     * Khi có {@code equipmentId} thì có thể bỏ trống (manager gán khi duyệt).
     */
    private String category;
    private List<String> images;
}
