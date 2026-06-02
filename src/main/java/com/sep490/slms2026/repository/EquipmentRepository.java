package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.enums.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    List<Equipment> findByRoomId(UUID roomId);

    // Tất cả thiết bị thuộc 1 property (qua room)
    @Query("SELECT e FROM Equipment e WHERE e.property.id = :propertyId")
    List<Equipment> findByPropertyId(@Param("propertyId") UUID propertyId);

    // Thiết bị theo property và trạng thái
    @Query("SELECT e FROM Equipment e WHERE e.room.property.id = :propertyId AND e.status = :status")
    List<Equipment> findByPropertyIdAndStatus(
            @Param("propertyId") UUID propertyId,
            @Param("status") EquipmentStatus status
    );

    // Thiết bị chưa được gán phòng nào (whole-house hoặc chưa gán)
    List<Equipment> findByRoomIsNull();

    // Kiểm tra qrPayload đã tồn tại chưa (đảm bảo unique)
    boolean existsByQrPayload(String qrPayload);
}