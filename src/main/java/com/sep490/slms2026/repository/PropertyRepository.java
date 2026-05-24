package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    // Lọc danh sách BĐS thuộc các Zone mà Manager được quyền quản lý
    @Query("SELECT p FROM Property p JOIN p.zone z JOIN z.managers m WHERE m.id = :managerId")
    Page<Property> findAllByManagerZones(@Param("managerId") UUID managerId, Pageable pageable);
}