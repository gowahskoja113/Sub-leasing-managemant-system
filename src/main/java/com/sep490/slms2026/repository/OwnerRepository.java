package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.OperationManagement;
import com.sep490.slms2026.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository extends JpaRepository<Owner, UUID> {
    Optional<Owner> findById(UUID id);
}