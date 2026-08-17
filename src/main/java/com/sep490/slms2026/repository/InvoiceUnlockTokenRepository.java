package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InvoiceUnlockToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceUnlockTokenRepository extends JpaRepository<InvoiceUnlockToken, Long> {

    Optional<InvoiceUnlockToken> findByToken(UUID token);
}
