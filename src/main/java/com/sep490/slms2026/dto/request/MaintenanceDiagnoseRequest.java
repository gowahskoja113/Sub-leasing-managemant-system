package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.DamageCause;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Màn "Chẩn đoán & báo giá" — dùng chung nhánh sửa ngay (OPEN) và sau khi mang đi kiểm tra
 * (REPAIR_SCHEDULED chưa có nguyên nhân).
 */
@Data
public class MaintenanceDiagnoseRequest {

    /**
     * Khi {@link #equipmentNeedsReplacement} = false/null: giá thợ báo (bắt buộc, ≥ 0) —
     * lưu vào estimatedDamageAmount.
     * Khi thay thiết bị: chi phí phát sinh thêm tuỳ chọn (vd lắp đặt) — lưu vào invoiceAmount.
     */
    private BigDecimal quotedRepairAmount;

    /**
     * Thiết bị hỏng hoàn toàn — cần thay mới.
     * Áp dụng độc lập với damageCause (cả WEAR lẫn TENANT_MISUSE).
     */
    private Boolean equipmentNeedsReplacement;

    /**
     * Bắt buộc khi {@link #equipmentNeedsReplacement} = true.
     * FE tự tính (khấu hao còn lại / penaltyFee), BE không tự tính lại.
     */
    private BigDecimal estimatedDamageAmount;

    /**
     * WEAR = hao mòn tự nhiên (công ty trả).
     * TENANT_MISUSE = lỗi do khách.
     */
    private DamageCause damageCause;

    /**
     * Bắt buộc khi damageCause = TENANT_MISUSE.
     * true = khách đồng ý trả → lập hoá đơn + gate thanh toán.
     * false = khách từ chối → companyAbsorbedFault, công ty trả hộ, không hoá đơn.
     */
    private Boolean tenantAgreesToPay;

    /** Bắt buộc khi lỗi do khách. */
    private String faultReason;

    /** Bắt buộc khi lỗi do khách — ảnh/video bằng chứng. */
    private List<String> faultEvidenceImages;

    /** Tuỳ chọn — tóm tắt thoả thuận khi khách từ chối trả. */
    private String companyAbsorbedNote;

    /**
     * Lịch hẹn giao máy / sửa chính thức.
     * Bắt buộc khi phiếu đang REPAIR_SCHEDULED (nhánh mang đi kiểm tra).
     * Tuỳ chọn khi đang OPEN: có giá trị → REPAIR_SCHEDULED, không → sửa ngay.
     */
    private LocalDateTime repairAppointmentAt;

    /** Tuỳ chọn — ghi đè category nếu phiếu chưa có. */
    private String category;

    private String priority;
}
