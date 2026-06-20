package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.enums.EquipmentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByPropertyId(Long propertyId);

    long countByPropertyId(Long propertyId);

    long countByPropertyIdAndSource(Long propertyId, EquipmentSource source);

    @Query("SELECT COALESCE(SUM(e.price), 0) FROM Equipment e WHERE e.property.id = :propertyId AND e.source = 'PURCHASED'")
    java.math.BigDecimal sumPurchasedEquipmentCostByPropertyId(@Param("propertyId") Long propertyId);

    long countByManifestId(Long manifestId);

    long countByRoomId(Long roomId);

    List<Equipment> findByRoomId(Long roomId);

    @Query("SELECT COUNT(e) FROM Equipment e WHERE e.property.id = :propertyId AND e.catalog.id = :catalogId")
    long countByPropertyIdAndCatalogId(@Param("propertyId") Long propertyId, @Param("catalogId") Long catalogId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Equipment e WHERE e.property.id = :propertyId")
    void deleteByPropertyId(@Param("propertyId") Long propertyId);

    java.util.Optional<Equipment> findByIdAndPropertyId(Long id, Long propertyId);

    @Query("SELECT DISTINCT e.catalog.name FROM Equipment e WHERE e.property.id = :propertyId ORDER BY e.catalog.name")
    List<String> findDistinctAmenityNamesByPropertyId(@Param("propertyId") Long propertyId);
}