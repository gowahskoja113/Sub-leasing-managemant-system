package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Cập nhật mã KH điện/nước cho nhà đã tồn tại.
 * null = không đổi; chuỗi rỗng = xoá mã.
 */
@Getter
@Setter
public class UpdateUtilityCustomerCodesRequest {
    private String electricityCustomerCode;
    private String waterCustomerCode;
}
