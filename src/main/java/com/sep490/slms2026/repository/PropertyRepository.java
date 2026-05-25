package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    @Query("SELECT DISTINCT p FROM Property p " +
            "JOIN p.zone z " +
            "LEFT JOIN z.parent parent " +
            "LEFT JOIN parent.parent grandparent " +
            "WHERE EXISTS (SELECT 1 FROM OperationManagement om JOIN om.zones mz " +
            "              WHERE om.id = :managerId AND " +
            "              (mz.id = z.id OR mz.id = parent.id OR mz.id = grandparent.id))")
    Page<Property> findAllByManagerZones(@Param("managerId") UUID managerId, Pageable pageable);
}