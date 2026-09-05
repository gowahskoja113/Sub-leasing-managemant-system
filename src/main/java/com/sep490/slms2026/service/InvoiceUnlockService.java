package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.InvoiceUnlockPasscodeGenerateRequest;
import com.sep490.slms2026.dto.request.InvoiceUnlockVerifyRequest;
import com.sep490.slms2026.dto.response.InvoiceUnlockLogResponse;
import com.sep490.slms2026.dto.response.InvoiceUnlockPasscodeResponse;
import com.sep490.slms2026.dto.response.InvoiceUnlockVerifyResponse;

import java.util.List;
import java.util.UUID;

public interface InvoiceUnlockService {

    InvoiceUnlockPasscodeResponse generatePasscode(UUID adminId, InvoiceUnlockPasscodeGenerateRequest request);

    List<InvoiceUnlockPasscodeResponse> listPasscodes(boolean activeOnly);

    InvoiceUnlockVerifyResponse verifyPasscode(UUID managerId, InvoiceUnlockVerifyRequest request);

    List<InvoiceUnlockLogResponse> listLogs();
}
