package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Mang thiết bị đi kiểm tra thêm — vào REPAIR_SCHEDULED mà chưa cần nguyên nhân/giá.
 */
@Data
public class MaintenanceSendForInspectionRequest {

    /**
     * Ngày dự kiến trả máy (tham khảo). Không ràng buộc — thợ báo sớm/trễ đều được.
     */
    private LocalDateTime expectedReturnAt;

    /** Tuỳ chọn — ghi đè/gán category nếu phiếu chưa có. */
    private String category;

    /** Tuỳ chọn — ghi chú timeline. */
    private String note;
}
