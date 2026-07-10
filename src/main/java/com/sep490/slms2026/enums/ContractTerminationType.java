package com.sep490.slms2026.enums;

/** Lý do thanh lý / chấm dứt hợp đồng thuê đang hiệu lực. */
public enum ContractTerminationType {
    /** Khách trả phòng / trả nhà trước hạn. */
    EARLY_MOVE_OUT,
    /** Vi phạm điều khoản HĐ. */
    VIOLATION,
    /** Hai bên thỏa thuận chấm dứt. */
    MUTUAL_AGREEMENT,
    /** Khác. */
    OTHER
}
