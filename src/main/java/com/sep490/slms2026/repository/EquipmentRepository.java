package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Equipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    Page<Equipment> findAllByRoomId(UUID roomId, Pageable pageable);

    // Lấy tất cả equipment thuộc property (qua room)
    @Query("SELECT e FROM Equipment e JOIN e.room r WHERE r.property.id = :propertyId")
    Page<Equipment> findAllByPropertyId(@Param("propertyId") UUID propertyId, Pageable pageable);

    Optional<Equipment> findByQrPayload(String qrPayload);
}