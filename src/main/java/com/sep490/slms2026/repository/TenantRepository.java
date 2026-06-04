package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByCitizenIdNumber(String citizenIdNumber);
}
