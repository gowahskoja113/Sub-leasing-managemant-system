package com.sep490.slms2026.enums;

import java.util.List;

/**
 * Dùng chung cho {@code TenantContract} và {@code InboundContract}.
 * <ul>
 *   <li>Tenant onboard: {@code DRAFT → AWAITING_ONBOARD → AWAITING_PAYMENT → AWAITING_CONFIRM → ACTIVE}</li>
 *   <li>{@code PENDING} giữ lại cho HĐ inbound (master lease), không dùng cho tenant onboard mới.</li>
 * </ul>
 */
public enum ContractStatus {
    /** Admin import / tạo nháp — chưa tới ngày đón. */
    DRAFT,
    /** Tới ngày đón — chờ manager chụp hiện trạng + chỉ số. */
    AWAITING_ONBOARD,
    /** Đã chụp xong — chờ thanh toán onboard (cọc + kỳ đầu). */
    AWAITING_PAYMENT,
    /** Đã thanh toán — chờ dual OTP xác nhận HĐ. */
    AWAITING_CONFIRM,
    /**
     * Legacy / inbound lease: chờ xử lý.
     * Tenant onboard mới không set trạng thái này.
     */
    PENDING,
    ACTIVE,
    EXPIRED,
    TERMINATED;

    /** Nhãn tiếng Việt cho FE (màn hồ sơ đón khách / list HĐ). */
    public String displayLabelVi() {
        return switch (this) {
            case DRAFT -> "Chờ đến ngày đón";
            case AWAITING_ONBOARD -> "Chờ onboard";
            case AWAITING_PAYMENT -> "Chờ thanh toán";
            case AWAITING_CONFIRM -> "Chờ xác nhận hợp đồng";
            case PENDING -> "Chờ xử lý";
            case ACTIVE -> "Đang hiệu lực";
            case EXPIRED -> "Hết hạn";
            case TERMINATED -> "Đã hủy / thanh lý";
        };
    }

    /** HĐ tenant đang giữ chỗ / chưa kết thúc onboard (trừ ACTIVE). */
    public static List<ContractStatus> onboardInProgress() {
        return List.of(DRAFT, AWAITING_ONBOARD, AWAITING_PAYMENT, AWAITING_CONFIRM);
    }

    /** HĐ tenant còn sống (giữ chỗ hoặc đang thuê). */
    public static List<ContractStatus> occupyingOrHolding() {
        return List.of(DRAFT, AWAITING_ONBOARD, AWAITING_PAYMENT, AWAITING_CONFIRM, ACTIVE);
    }

    /** Có thể sửa hiện trạng / chỉ số trước khi thu tiền. */
    public boolean isCaptureEditable() {
        return this == DRAFT || this == AWAITING_ONBOARD;
    }

    /** Có thể tạo QR / chờ thanh toán onboard. */
    public boolean canCollectPayment() {
        return this == AWAITING_PAYMENT;
    }

    /** Đã trả tiền, chờ OTP confirm. */
    public boolean canConfirm() {
        return this == AWAITING_CONFIRM;
    }
}
