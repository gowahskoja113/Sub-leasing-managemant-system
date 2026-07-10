package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.OtpVerification;
import com.sep490.slms2026.enums.OtpPurpose;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.OtpVerificationRepository;
import com.sep490.slms2026.service.TwilioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private OtpVerificationRepository otpVerificationRepository;
    @Mock private TwilioService twilioService;

    @InjectMocks private OtpServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expiryMinutes", 5);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
    }

    @Test
    void sendOtp_savesCodeAndCallsTwilio() {
        when(twilioService.isConfigured()).thenReturn(false);

        service.sendOtp("0912345678", OtpPurpose.CONTRACT_CONFIRM, 42L);

        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpVerificationRepository).save(captor.capture());
        OtpVerification saved = captor.getValue();
        assertEquals("+84912345678", saved.getPhoneNumber());
        assertEquals(OtpPurpose.CONTRACT_CONFIRM, saved.getPurpose());
        assertEquals(42L, saved.getReferenceId());
        assertEquals(6, saved.getCode().length());
        verify(twilioService).sendSms(anyString(), anyString());
    }

    @Test
    void verifyOrThrow_acceptsMatchingCode() {
        OtpVerification otp = OtpVerification.builder()
                .phoneNumber("+84912345678")
                .code("123456")
                .purpose(OtpPurpose.CONTRACT_CONFIRM)
                .referenceId(42L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attemptCount(0)
                .verified(false)
                .build();
        when(otpVerificationRepository
                .findTopByPhoneNumberAndPurposeAndReferenceIdAndVerifiedFalseOrderByCreatedAtDesc(
                        "+84912345678", OtpPurpose.CONTRACT_CONFIRM, 42L))
                .thenReturn(Optional.of(otp));

        assertDoesNotThrow(() ->
                service.verifyOrThrow("0912345678", "123456", OtpPurpose.CONTRACT_CONFIRM, 42L));

        assertEquals(true, otp.isVerified());
        verify(otpVerificationRepository).save(otp);
    }

    @Test
    void verifyOrThrow_rejectsWrongCode() {
        OtpVerification otp = OtpVerification.builder()
                .phoneNumber("+84912345678")
                .code("123456")
                .purpose(OtpPurpose.CONTRACT_CONFIRM)
                .referenceId(42L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attemptCount(0)
                .verified(false)
                .build();
        when(otpVerificationRepository
                .findTopByPhoneNumberAndPurposeAndReferenceIdAndVerifiedFalseOrderByCreatedAtDesc(
                        "+84912345678", OtpPurpose.CONTRACT_CONFIRM, 42L))
                .thenReturn(Optional.of(otp));

        assertThrows(BusinessException.class, () ->
                service.verifyOrThrow("0912345678", "000000", OtpPurpose.CONTRACT_CONFIRM, 42L));
        assertEquals(1, otp.getAttemptCount());
    }
}
