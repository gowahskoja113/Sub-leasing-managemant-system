package com.sep490.slms2026.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class MaintenanceCreateRequest {
    /**
     * Thuê theo phòng: bắt buộc.
     * Thuê nguyên căn: có thể null — khi đó phải gửi {@code propertyId}.
     */
    private Long roomId;
    /**
     * Thuê nguyên căn (không gắn phòng): bắt buộc khi {@code roomId} null.
     * Thuê theo phòng: có thể bỏ (lấy từ phòng).
     */
    private Long propertyId;
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
