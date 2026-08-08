package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MeterOverrideToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeterOverrideTokenRepository extends JpaRepository<MeterOverrideToken, Long> {

    Optional<MeterOverrideToken> findByToken(UUID token);
}
