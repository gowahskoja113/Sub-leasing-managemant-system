package com.sep490.slms2026.service;

import com.sep490.slms2026.enums.OtpPurpose;

public interface OtpService {

    void sendOtp(String phoneNumber, OtpPurpose purpose, Long referenceId);

    void verifyOrThrow(String phoneNumber, String code, OtpPurpose purpose, Long referenceId);
}
