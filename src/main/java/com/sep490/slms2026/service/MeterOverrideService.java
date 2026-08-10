package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.MeterOverridePasscodeGenerateRequest;
import com.sep490.slms2026.dto.request.MeterOverrideVerifyRequest;
import com.sep490.slms2026.dto.response.MeterOverrideLogResponse;
import com.sep490.slms2026.dto.response.MeterOverridePasscodeResponse;
import com.sep490.slms2026.dto.response.MeterOverrideVerifyResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MeterOverrideService {

    /**
     * Admin gen mã một lần (kiểu OTP). Manager nhập mã này ở /verify.
     */
    MeterOverridePasscodeResponse generatePasscode(UUID adminId, MeterOverridePasscodeGenerateRequest request);

    /** Danh sách mã (mới → cũ); mã đã dùng/hết hạn vẫn hiện để audit. */
    List<MeterOverridePasscodeResponse> listPasscodes(boolean activeOnly);

    /**
     * Manager nhập mã admin gen → nhận overrideToken (một lần, TTL riêng).
     * Mã passcode chết ngay khi verify thành công.
     */
    MeterOverrideVerifyResponse verifyPasscode(UUID managerId, MeterOverrideVerifyRequest request);

    /**
     * Xác thực token override khi manager nhập chỉ số thủ công (không có ảnh).
     * Trả true nếu token hợp lệ và ghi audit log; false nếu không dùng override.
     */
    boolean consumeOverrideIfPresent(UUID managerId, Long contractId, String meterKind,
                                     UUID overrideToken, BigDecimal enteredValue, String reason);

    List<MeterOverrideLogResponse> listLogs();
}
