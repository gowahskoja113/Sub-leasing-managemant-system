package com.sep490.slms2026.enums;

public enum PaymentClaimStatus {
    PENDING_VERIFY,
    VERIFIED,
    REJECTED,
    /** Hoá đơn đã được trả qua kênh khác (PayOS, tiền mặt...) — claim tự đóng, không coi là bị từ chối. */
    SUPERSEDED
}
