package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.OperationManagement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OperationManagementRepository extends JpaRepository<OperationManagement, UUID> {
    Optional<OperationManagement> findById(UUID id);
}
