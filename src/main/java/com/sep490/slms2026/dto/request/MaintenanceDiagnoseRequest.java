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

    /** Giá thợ báo (chi phí sửa thực tế). */
    private BigDecimal quotedRepairAmount;

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
