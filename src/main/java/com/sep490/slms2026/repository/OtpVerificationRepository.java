package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.OtpVerification;
import com.sep490.slms2026.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByPhoneNumberAndPurposeAndReferenceIdAndVerifiedFalseOrderByCreatedAtDesc(
            String phoneNumber, OtpPurpose purpose, Long referenceId);
}
