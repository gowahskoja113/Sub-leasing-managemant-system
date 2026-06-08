package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.enums.EquipmentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByPropertyId(Long propertyId);

    boolean existsByPropertyIdAndRoomIsNotNull(Long propertyId);

    @Query("SELECT COALESCE(SUM(e.purchasePrice), 0) FROM Equipment e " +
            "WHERE e.property.id = :propertyId AND e.room IS NULL " +
            "AND e.source = :source AND e.purchasePrice IS NOT NULL")
    BigDecimal sumPurchasePriceByPropertyIdAndRoomIsNullAndSource(
            @Param("propertyId") Long propertyId,
            @Param("source") EquipmentSource source);

    @Query("SELECT COALESCE(SUM(e.purchasePrice), 0) FROM Equipment e " +
            "WHERE e.room.id = :roomId AND e.source = :source AND e.purchasePrice IS NOT NULL")
    BigDecimal sumPurchasePriceByRoomIdAndSource(
            @Param("roomId") Long roomId,
            @Param("source") EquipmentSource source);
}
