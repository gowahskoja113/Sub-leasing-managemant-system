package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MeterOverrideFailCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MeterOverrideFailCounterRepository extends JpaRepository<MeterOverrideFailCounter, UUID> {
}
