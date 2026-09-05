package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.ZoneManager;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ZoneManagerRepository extends JpaRepository<ZoneManager, UUID> {

    long countByManagerId(UUID managerId);
}
