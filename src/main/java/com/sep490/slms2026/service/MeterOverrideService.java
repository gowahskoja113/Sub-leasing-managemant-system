package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.MeterOverrideVerifyRequest;
import com.sep490.slms2026.dto.response.MeterOverrideLogResponse;
import com.sep490.slms2026.dto.response.MeterOverrideVerifyResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MeterOverrideService {

    MeterOverrideVerifyResponse verifyPasscode(UUID managerId, MeterOverrideVerifyRequest request);

    /**
     * Xác thực token override khi manager nhập chỉ số thủ công (không có ảnh).
     * Trả true nếu token hợp lệ và ghi audit log; false nếu không dùng override.
     */
    boolean consumeOverrideIfPresent(UUID managerId, Long contractId, String meterKind,
                                     UUID overrideToken, BigDecimal enteredValue, String reason);

    List<MeterOverrideLogResponse> listLogs();
}
