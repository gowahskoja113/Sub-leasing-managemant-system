package com.sep490.slms2026.dto.request;

import lombok.Data;

/**
 * Manager bắt đầu xử lý phiếu tại hiện trường — bắt buộc quét QR khi phiếu gắn thiết bị.
 * Xem phiếu (GET) không cần QR.
 */
@Data
public class MaintenanceConfirmArrivalRequest {
    /** Mã QR đã quét (vd EQ-226). Bắt buộc khi phiếu có equipmentId. */
    private String qrCode;
}
